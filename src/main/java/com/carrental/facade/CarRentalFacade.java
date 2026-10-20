package com.carrental.facade;

import com.carrental.exception.*;
import com.carrental.orchestration.ChainStepStatus;
import com.carrental.orchestration.RentalChainOrder;
import com.carrental.orchestration.RentalChainRepository;
import com.carrental.subsystem.dispatch.DispatchRecord;
import com.carrental.subsystem.dispatch.DispatchService;
import com.carrental.subsystem.finance.DepositService;
import com.carrental.subsystem.finance.DepositTransaction;
import com.carrental.subsystem.reservation.ReservationService;
import com.carrental.subsystem.reservation.VehicleReservation;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Primary Client-Facing Facade coordinating the three-stage rental lifecycle:
 * Step A (Reservation) -> Step B (Financial Deposit) -> Step C (Inspection & Telematics Dispatch).
 *
 * Implements durable chain progress persistence and two distinct compensation depths:
 * 1. B fails after A: compensates A alone.
 * 2. C fails after A and B: compensates B, then A in exact reverse order (real two-step rollback).
 */
public class CarRentalFacade {

    private final ReservationService reservationService;
    private final DepositService depositService;
    private final DispatchService dispatchService;
    private final RentalChainRepository chainRepository;

    public CarRentalFacade(ReservationService reservationService,
                           DepositService depositService,
                           DispatchService dispatchService,
                           RentalChainRepository chainRepository) {
        this.reservationService = reservationService;
        this.depositService = depositService;
        this.dispatchService = dispatchService;
        this.chainRepository = chainRepository;
    }

    public RentalResult rentVehicle(RentalRequest request) {
        if (request == null) {
            throw new InvalidRentalRequestException("Rental request cannot be null");
        }

        String chainId = "CHAIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        RentalChainOrder chain = new RentalChainOrder(
                chainId, request.getCustomerId(), request.getVehicleId(),
                null, null, null, ChainStepStatus.INITIALIZED, "",
                LocalDateTime.now(), LocalDateTime.now()
        );
        chainRepository.save(chain);

        // Pre-step: Validate initial parameters before committing any durable state (Section 3.6 requirement)
        if (request.getDriverAge() < 21) {
            String err = "Pre-flight rejection: driver age " + request.getDriverAge() + " is below legal minimum (21)";
            chain.updateStatus(ChainStepStatus.FAILED_AT_START, err);
            chainRepository.save(chain);
            throw new InvalidRentalRequestException(err);
        }

        VehicleReservation reservation = null;
        DepositTransaction deposit = null;
        DispatchRecord dispatch = null;

        // -------------------------------------------------------------------------
        // STEP A: Vehicle Intake & Reservation
        // -------------------------------------------------------------------------
        try {
            reservation = reservationService.createReservation(
                    request.getCustomerId(),
                    request.getDriverAge(),
                    request.getVehicleId(),
                    request.getRentalDays(),
                    request.getDailyRate()
            );
            reservationService.confirmReservation(reservation.getReservationId());

            chain.setReservationId(reservation.getReservationId());
            chain.updateStatus(ChainStepStatus.STEP_A_COMMITTED, "Reservation committed and confirmed in Subsystem A");
            chainRepository.save(chain);
        } catch (CarRentalException e) {
            chain.updateStatus(ChainStepStatus.FAILED_AT_START, e.getMessage());
            chainRepository.save(chain);
            throw e;
        }

        // -------------------------------------------------------------------------
        // STEP B: Financial Security Deposit & Insurance Hold
        // -------------------------------------------------------------------------
        try {
            deposit = depositService.authorizeAndHoldDeposit(
                    reservation.getReservationId(),
                    request.getCustomerId(),
                    request.getCreditScore(),
                    request.getDailyRate(),
                    request.getRentalDays(),
                    request.isForceDepositDecline()
            );

            chain.setDepositId(deposit.getTransactionId());
            chain.updateStatus(ChainStepStatus.STEP_B_COMMITTED, "Financial deposit authorized and held in Subsystem B");
            chainRepository.save(chain);
        } catch (DepositDeclinedException e) {
            // COMPENSATION SCENARIO 1: B fails after A committed -> Compensate A alone!
            System.err.println("[Facade Compensation] Step B failed! Compensating Step A (Reservation " + reservation.getReservationId() + ")...");
            chain.updateStatus(ChainStepStatus.COMPENSATING_A, "Step B failed: " + e.getMessage() + ". Rolling back Step A.");
            chainRepository.save(chain);

            try {
                reservationService.cancelReservation(reservation.getReservationId());
                chain.updateStatus(ChainStepStatus.COMPENSATED_A_ONLY, "Successfully compensated Step A after Step B failure.");
                chainRepository.save(chain);
            } catch (Exception rollbackEx) {
                throw new CompensationException("Failed to compensate Step A after Step B failure", rollbackEx);
            }

            throw e;
        }

        // -------------------------------------------------------------------------
        // STEP C: Pre-trip Vehicle Safety Inspection & Telematics Dispatch
        // -------------------------------------------------------------------------
        try {
            dispatch = dispatchService.inspectAndDispatch(
                    reservation.getReservationId(),
                    request.getVehicleId(),
                    request.isForceInspectionDefect()
            );

            chain.setDispatchId(dispatch.getDispatchId());
            chain.updateStatus(ChainStepStatus.COMPLETED, "Rental chain completed successfully across A, B, and C.");
            chainRepository.save(chain);

            return new RentalResult(
                    true, chainId, reservation.getReservationId(), deposit.getTransactionId(),
                    dispatch.getDispatchId(), ChainStepStatus.COMPLETED,
                    "Vehicle " + request.getVehicleId() + " successfully inspected and dispatched to " + request.getCustomerId()
            );
        } catch (VehicleSafetyInspectionException e) {
            // COMPENSATION SCENARIO 2: C fails after A and B committed -> Real two-step rollback: B, then A!
            System.err.println("[Facade Compensation] Step C failed! Initiating 2-step compensation: reversing B, then A...");
            chain.updateStatus(ChainStepStatus.COMPENSATING_B_THEN_A, "Step C failed: " + e.getMessage() + ". Rolling back B then A.");
            chainRepository.save(chain);

            try {
                // Rollback Step 1: Refund deposit in Subsystem B
                System.err.println("[Facade Compensation] Step 1/2: Refunding deposit " + deposit.getTransactionId() + " in Subsystem B...");
                depositService.refundDeposit(deposit.getTransactionId());

                // Rollback Step 2: Cancel reservation in Subsystem A
                System.err.println("[Facade Compensation] Step 2/2: Cancelling reservation " + reservation.getReservationId() + " in Subsystem A...");
                reservationService.cancelReservation(reservation.getReservationId());

                chain.updateStatus(ChainStepStatus.COMPENSATED_FULL, "Successfully completed 2-step compensation (B then A).");
                chainRepository.save(chain);
            } catch (Exception rollbackEx) {
                throw new CompensationException("Two-step compensation failed after Step C failure", rollbackEx);
            }

            throw e;
        }
    }
}
