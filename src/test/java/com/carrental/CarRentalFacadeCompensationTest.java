package com.carrental;

import com.carrental.exception.DepositDeclinedException;
import com.carrental.exception.InvalidRentalRequestException;
import com.carrental.exception.VehicleSafetyInspectionException;
import com.carrental.facade.CarRentalFacade;
import com.carrental.facade.RentalAdminFacade;
import com.carrental.facade.RentalRequest;
import com.carrental.facade.RentalResult;
import com.carrental.orchestration.ChainStepStatus;
import com.carrental.orchestration.RentalChainOrder;
import com.carrental.orchestration.RentalChainRepository;
import com.carrental.subsystem.dispatch.*;
import com.carrental.subsystem.finance.*;
import com.carrental.subsystem.reservation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class CarRentalFacadeCompensationTest {

    @TempDir
    Path tempDir;

    private ReservationService reservationService;
    private DepositService depositService;
    private DispatchService dispatchService;
    private RentalChainRepository chainRepository;
    private CarRentalFacade facade;
    private RentalAdminFacade adminFacade;

    @BeforeEach
    void setUp() {
        String dir = tempDir.toString();
        ReservationFileRepository resRepo = new ReservationFileRepository(dir);
        reservationService = new ReservationService(resRepo, new FleetReservationValidator());

        DepositFileRepository depRepo = new DepositFileRepository(dir);
        depositService = new DepositService(depRepo, new DepositRiskCalculator());

        DispatchFileRepository dispRepo = new DispatchFileRepository(dir);
        dispatchService = new DispatchService(dispRepo, new PreTripInspectionEngine());

        chainRepository = new RentalChainRepository(dir);

        facade = new CarRentalFacade(reservationService, depositService, dispatchService, chainRepository);
        adminFacade = new RentalAdminFacade(resRepo, depRepo, dispRepo, chainRepository);
    }

    // =========================================================================
    // DOMAIN-SPECIFIC TESTING LANGUAGE (DSL) FLUENT BUILDER (Requirement 3.7)
    // =========================================================================

    private RentalTestSentence givenRentalChain() {
        return new RentalTestSentence();
    }

    private class RentalTestSentence {
        private String customerId = "CUST-DEFAULT";
        private int driverAge = 25;
        private int creditScore = 700;
        private String vehicleId = "CAR-SEDAN-101";
        private int rentalDays = 3;
        private double dailyRate = 50.0;
        private boolean forceDepositDecline = false;
        private boolean forceInspectionDefect = false;

        private RentalResult result;
        private Exception caughtException;

        RentalTestSentence forCustomer(String customerId, int age, int creditScore) {
            this.customerId = customerId;
            this.driverAge = age;
            this.creditScore = creditScore;
            return this;
        }

        RentalTestSentence withVehicle(String vehicleId, int days, double rate) {
            this.vehicleId = vehicleId;
            this.rentalDays = days;
            this.dailyRate = rate;
            return this;
        }

        RentalTestSentence withForcedDepositDecline() {
            this.forceDepositDecline = true;
            return this;
        }

        RentalTestSentence withForcedInspectionDefect() {
            this.forceInspectionDefect = true;
            return this;
        }

        RentalTestSentence whenExecuted() {
            RentalRequest request = new RentalRequest(
                    customerId, driverAge, creditScore, vehicleId, rentalDays, dailyRate,
                    forceDepositDecline, forceInspectionDefect
            );
            try {
                this.result = facade.rentVehicle(request);
            } catch (Exception ex) {
                this.caughtException = ex;
            }
            return this;
        }

        void thenExpectCompleted() {
            assertNull(caughtException, "Expected rental chain execution to succeed without exceptions");
            assertNotNull(result, "Expected non-null RentalResult");
            assertTrue(result.isSuccess(), "RentalResult.isSuccess must be true");
            assertEquals(ChainStepStatus.COMPLETED, result.getFinalStatus());

            // Assert persisted states
            VehicleReservation res = reservationService.findById(result.getReservationId()).orElseThrow();
            assertEquals(ReservationStatus.CONFIRMED, res.getStatus());

            DepositTransaction dep = depositService.findById(result.getDepositId()).orElseThrow();
            assertEquals(DepositStatus.HELD, dep.getStatus());

            DispatchRecord disp = dispatchService.findById(result.getDispatchId()).orElseThrow();
            assertEquals(DispatchStatus.DISPATCHED, disp.getStatus());
        }

        void thenExpectFailedAtStepBAndCompensatedA() {
            assertNotNull(caughtException, "Expected failure exception at Step B");
            assertInstanceOf(DepositDeclinedException.class, caughtException);

            RentalChainOrder chain = chainRepository.findAll().get(chainRepository.findAll().size() - 1);
            assertEquals(ChainStepStatus.COMPENSATED_A_ONLY, chain.getStatus(),
                    "Persisted chain status must be COMPENSATED_A_ONLY");

            // Verify Step A was fully rolled back to CANCELLED in durable storage
            assertNotNull(chain.getReservationId());
            VehicleReservation res = reservationService.findById(chain.getReservationId()).orElseThrow();
            assertEquals(ReservationStatus.CANCELLED, res.getStatus(),
                    "Subsystem A reservation must be cancelled as compensation for B failure");

            // Verify Step B was never held and Step C was never initiated
            assertNull(chain.getDepositId());
            assertNull(chain.getDispatchId());
        }

        void thenExpectFailedAtStepCAndCompensatedFull() {
            assertNotNull(caughtException, "Expected failure exception at Step C");
            assertInstanceOf(VehicleSafetyInspectionException.class, caughtException);

            RentalChainOrder chain = chainRepository.findAll().get(chainRepository.findAll().size() - 1);
            assertEquals(ChainStepStatus.COMPENSATED_FULL, chain.getStatus(),
                    "Persisted chain status must be COMPENSATED_FULL after two-step rollback");

            // Verify Step B was rolled back to REFUNDED in durable storage
            assertNotNull(chain.getDepositId());
            DepositTransaction dep = depositService.findById(chain.getDepositId()).orElseThrow();
            assertEquals(DepositStatus.REFUNDED, dep.getStatus(),
                    "Subsystem B deposit must be REFUNDED in Step 1 of compensation");

            // Verify Step A was rolled back to CANCELLED in durable storage
            assertNotNull(chain.getReservationId());
            VehicleReservation res = reservationService.findById(chain.getReservationId()).orElseThrow();
            assertEquals(ReservationStatus.CANCELLED, res.getStatus(),
                    "Subsystem A reservation must be CANCELLED in Step 2 of compensation");

            // Verify Step C was aborted/never dispatched
            assertNull(chain.getDispatchId());
        }
    }

    // =========================================================================
    // FIRST-CLASS TEST TARGETS (Requirements 3.4, 3.6, 3.7)
    // =========================================================================

    @Test
    @DisplayName("Happy Path: executes full A -> B -> C rental lifecycle and commits durable state")
    void testHappyPathRentalChain() {
        givenRentalChain()
                .forCustomer("CUST-ALICE", 28, 750)
                .withVehicle("CAR-TESLA-303", 5, 90.0)
                .whenExecuted()
                .thenExpectCompleted();
    }

    @Test
    @DisplayName("Compensation Depth 1: Step B fails after A committed -> compensates A alone")
    void testStepBFailureCompensatesAAlone() {
        givenRentalChain()
                .forCustomer("CUST-BOB", 24, 520)
                .withVehicle("CAR-SUV-202", 3, 75.0)
                .withForcedDepositDecline()
                .whenExecuted()
                .thenExpectFailedAtStepBAndCompensatedA();
    }

    @Test
    @DisplayName("Compensation Depth 2: Step C fails after A and B committed -> two-step rollback: B, then A")
    void testStepCFailureTwoStepRollbackBThenA() {
        givenRentalChain()
                .forCustomer("CUST-CHARLIE", 35, 780)
                .withVehicle("CAR-LUX-404", 4, 150.0)
                .withForcedInspectionDefect()
                .whenExecuted()
                .thenExpectFailedAtStepCAndCompensatedFull();
    }

    @Test
    @DisplayName("Pre-flight Validation (Section 3.6): under-age driver rejected before any subsystem commits")
    void testPreFlightValidationRejection() {
        RentalRequest underageRequest = RentalRequest.standard("CUST-TEEN", 18, 650, "CAR-SEDAN-101", 2, 40.0);

        assertThrows(InvalidRentalRequestException.class, () -> facade.rentVehicle(underageRequest));

        // Assert nothing committed in subsystems A, B, or C
        assertEquals(0, reservationService.getRepository().findAll().size(), "Subsystem A must remain empty");
        assertEquals(0, depositService.getRepository().findAll().size(), "Subsystem B must remain empty");
        assertEquals(0, dispatchService.getRepository().findAll().size(), "Subsystem C must remain empty");

        // Chain itself records FAILED_AT_START
        RentalChainOrder chain = chainRepository.findAll().get(0);
        assertEquals(ChainStepStatus.FAILED_AT_START, chain.getStatus());
    }
}
