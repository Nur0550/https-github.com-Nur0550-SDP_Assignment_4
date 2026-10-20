package com.carrental.facade;

import com.carrental.orchestration.ChainStepStatus;
import com.carrental.orchestration.RentalChainOrder;
import com.carrental.orchestration.RentalChainRepository;
import com.carrental.subsystem.dispatch.DispatchFileRepository;
import com.carrental.subsystem.dispatch.DispatchRecord;
import com.carrental.subsystem.finance.DepositFileRepository;
import com.carrental.subsystem.finance.DepositStatus;
import com.carrental.subsystem.finance.DepositTransaction;
import com.carrental.subsystem.reservation.ReservationFileRepository;
import com.carrental.subsystem.reservation.ReservationStatus;
import com.carrental.subsystem.reservation.VehicleReservation;

import java.util.List;
import java.util.Optional;

/**
 * Secondary Administrative / Inspection / Manual-Compensation Facade.
 * Provides diagnostics across all three subsystems, inspects persisted states,
 * and handles crash recovery / manual compensation orchestration based on durable disk records.
 */
public class RentalAdminFacade {

    private final ReservationFileRepository reservationRepo;
    private final DepositFileRepository depositRepo;
    private final DispatchFileRepository dispatchRepo;
    private final RentalChainRepository chainRepo;

    public RentalAdminFacade(ReservationFileRepository reservationRepo,
                             DepositFileRepository depositRepo,
                             DispatchFileRepository dispatchRepo,
                             RentalChainRepository chainRepo) {
        this.reservationRepo = reservationRepo;
        this.depositRepo = depositRepo;
        this.dispatchRepo = dispatchRepo;
        this.chainRepo = chainRepo;
    }

    public ChainAuditReport auditChain(String chainId) {
        RentalChainOrder chain = chainRepo.findById(chainId)
                .orElseThrow(() -> new IllegalArgumentException("Chain ID not found: " + chainId));

        VehicleReservation res = chain.getReservationId() != null
                ? reservationRepo.findById(chain.getReservationId()).orElse(null)
                : null;

        DepositTransaction dep = chain.getDepositId() != null
                ? depositRepo.findById(chain.getDepositId()).orElse(null)
                : null;

        DispatchRecord disp = chain.getDispatchId() != null
                ? dispatchRepo.findById(chain.getDispatchId()).orElse(null)
                : null;

        return new ChainAuditReport(chain, res, dep, disp);
    }

    public List<RentalChainOrder> listAllChains() {
        return chainRepo.findAll();
    }

    /**
     * Examines durable state on disk after an unexpected system crash or interruption,
     * determining what compensation steps remain unfulfilled and executing them in order.
     */
    public boolean recoverOrCompleteCompensation(String chainId) {
        Optional<RentalChainOrder> optionalChain = chainRepo.findById(chainId);
        if (optionalChain.isEmpty()) {
            return false;
        }

        RentalChainOrder chain = optionalChain.get();
        ChainStepStatus status = chain.getStatus();

        // If interrupted during 2-step compensation (B then A)
        if (status == ChainStepStatus.COMPENSATING_B_THEN_A) {
            // Check if Deposit in B needs refunding
            if (chain.getDepositId() != null) {
                depositRepo.findById(chain.getDepositId()).ifPresent(dep -> {
                    if (dep.getStatus() == DepositStatus.HELD) {
                        dep.refund();
                        depositRepo.save(dep);
                    }
                });
            }
            // Check if Reservation in A needs cancelling
            if (chain.getReservationId() != null) {
                reservationRepo.findById(chain.getReservationId()).ifPresent(res -> {
                    if (res.getStatus() != ReservationStatus.CANCELLED) {
                        res.cancel();
                        reservationRepo.save(res);
                    }
                });
            }
            chain.updateStatus(ChainStepStatus.COMPENSATED_FULL, "Recovered and completed 2-step compensation after restart.");
            chainRepo.save(chain);
            return true;
        }

        // If interrupted during single-step compensation (A)
        if (status == ChainStepStatus.COMPENSATING_A) {
            if (chain.getReservationId() != null) {
                reservationRepo.findById(chain.getReservationId()).ifPresent(res -> {
                    if (res.getStatus() != ReservationStatus.CANCELLED) {
                        res.cancel();
                        reservationRepo.save(res);
                    }
                });
            }
            chain.updateStatus(ChainStepStatus.COMPENSATED_A_ONLY, "Recovered and completed Step A compensation after restart.");
            chainRepo.save(chain);
            return true;
        }

        return false;
    }

    public void reloadAllFromDisk() {
        reservationRepo.reloadFromDisk();
        depositRepo.reloadFromDisk();
        dispatchRepo.reloadFromDisk();
        chainRepo.reloadFromDisk();
    }

    public void resetAllStores() {
        reservationRepo.clearStore();
        depositRepo.clearStore();
        dispatchRepo.clearStore();
        chainRepo.clearStore();
    }
}
