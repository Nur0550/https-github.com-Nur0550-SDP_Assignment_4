package com.carrental;

import com.carrental.exception.DepositDeclinedException;
import com.carrental.exception.VehicleUnavailableException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * On-the-spot live defense test scenarios requested by the examiner.
 * Covers full lifecycle setup, scenario execution, and strict verification
 * of the persisted end state across all three independent storage files.
 */
public class ExaminerLiveCodingTest {

    @TempDir
    Path storageDir;

    private ReservationFileRepository reservationRepo;
    private DepositFileRepository depositRepo;
    private DispatchFileRepository dispatchRepo;
    private RentalChainRepository chainRepo;

    private ReservationService reservationService;
    private DepositService depositService;
    private DispatchService dispatchService;

    private CarRentalFacade facade;
    private RentalAdminFacade adminFacade;

    @BeforeEach
    void setUp() {
        String dir = storageDir.toString();

        // 1. Setup durable file repositories in a pristine test directory
        reservationRepo = new ReservationFileRepository(dir);
        depositRepo = new DepositFileRepository(dir);
        dispatchRepo = new DispatchFileRepository(dir);
        chainRepo = new RentalChainRepository(dir);

        // 2. Setup autonomous subsystems
        reservationService = new ReservationService(reservationRepo, new FleetReservationValidator());
        depositService = new DepositService(depositRepo, new DepositRiskCalculator());
        dispatchService = new DispatchService(dispatchRepo, new PreTripInspectionEngine());

        // 3. Setup Facades
        facade = new CarRentalFacade(reservationService, depositService, dispatchService, chainRepo);
        adminFacade = new RentalAdminFacade(reservationRepo, depositRepo, dispatchRepo, chainRepo);
    }

    /**
     * SCENARIO: Multi-order isolation.
     * Order 1 succeeds through the full A -> B -> C chain.
     * Order 2 fails at Step B (credit score / banking card decline).
     *
     * Verification:
     * - Order 1's durable states in Subsystems A, B, C remain untouched and COMPLETED.
     * - Order 2's Step A reservation is compensated (CANCELLED).
     * - Order 2 produced zero commits in B and C.
     * - The disk store contains exactly two chain records reflecting these exact end states.
     */
    @Test
    @DisplayName("Examiner Scenario: Order 1 succeeds, Order 2 fails at Step B -> verify isolated compensation and persisted disk states")
    void testOrder1SucceedsOrder2FailsAtStepB_AssertIsolatedDurableEndStates() {
        // --- 1. TRIGGER ORDER 1 (Happy Path) ---
        RentalRequest request1 = RentalRequest.standard(
                "CUST-ALICE", 29, 760, "CAR-TESLA-303", 5, 85.0
        );
        RentalResult result1 = facade.rentVehicle(request1);

        assertNotNull(result1);
        assertTrue(result1.isSuccess());
        assertEquals(ChainStepStatus.COMPLETED, result1.getFinalStatus());

        // --- 2. TRIGGER ORDER 2 (Step B failure: insufficient credit / card declined) ---
        RentalRequest request2 = RentalRequest.forceDepositFailure(
                "CUST-BOB", 24, 510, "CAR-SUV-202", 3, 75.0
        );

        assertThrows(DepositDeclinedException.class, () -> facade.rentVehicle(request2));

        // --- 3. ASSERT PERSISTED END STATE DIRECTLY FROM DURABLE REPOSITORIES ---

        // A) Chain Orders on Disk (exactly 2 orders persisted)
        List<RentalChainOrder> allChains = chainRepo.findAll();
        assertEquals(2, allChains.size(), "Durable chain log must contain exactly 2 orders");

        RentalChainOrder chain1 = chainRepo.findById(result1.getChainId()).orElseThrow();
        assertEquals(ChainStepStatus.COMPLETED, chain1.getStatus());

        RentalChainOrder chain2 = allChains.stream()
                .filter(c -> !c.getChainId().equals(result1.getChainId()))
                .findFirst()
                .orElseThrow();
        assertEquals(ChainStepStatus.COMPENSATED_A_ONLY, chain2.getStatus(),
                "Order 2 must end in COMPENSATED_A_ONLY on disk");

        // B) Subsystem A: Exactly 2 reservations on disk
        List<VehicleReservation> allReservations = reservationRepo.findAll();
        assertEquals(2, allReservations.size(), "Subsystem A repository must have exactly 2 records");

        VehicleReservation res1 = reservationRepo.findById(chain1.getReservationId()).orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, res1.getStatus(),
                "Order 1 reservation must remain CONFIRMED");

        VehicleReservation res2 = reservationRepo.findById(chain2.getReservationId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, res2.getStatus(),
                "Order 2 reservation must be rolled back to CANCELLED");

        // C) Subsystem B: Exactly 1 deposit on disk (belonging to Order 1 only)
        List<DepositTransaction> allDeposits = depositRepo.findAll();
        assertEquals(1, allDeposits.size(), "Subsystem B must contain exactly 1 deposit record");
        assertEquals(chain1.getDepositId(), allDeposits.get(0).getTransactionId());
        assertEquals(DepositStatus.HELD, allDeposits.get(0).getStatus(),
                "Order 1 deposit must remain HELD");

        // D) Subsystem C: Exactly 1 dispatch record on disk (belonging to Order 1 only)
        List<DispatchRecord> allDispatches = dispatchRepo.findAll();
        assertEquals(1, allDispatches.size(), "Subsystem C must contain exactly 1 dispatch record");
        assertEquals(chain1.getDispatchId(), allDispatches.get(0).getDispatchId());
        assertEquals(DispatchStatus.DISPATCHED, allDispatches.get(0).getStatus(),
                "Order 1 dispatch must remain DISPATCHED");
    }

    /**
     * SCENARIO: Step A failure (Requested vehicle does not exist in fleet catalog).
     *
     * Verification:
     * - VehicleUnavailableException is thrown immediately.
     * - Subsystems A, B, and C have ZERO committed entries on disk.
     * - The chain tracker records FAILED_AT_START with the exact error message.
     */
    @Test
    @DisplayName("Examiner Scenario: Unknown vehicle requested -> Step A fails -> verify zero commits in A, B, C")
    void testUnknownVehicleFailsAtStepA_AssertNoDurableSideEffectsInSubsystems() {
        RentalRequest invalidVehicleRequest = RentalRequest.standard(
                "CUST-DAVE", 30, 700, "NON-EXISTENT-TRUCK-999", 3, 100.0
        );

        assertThrows(VehicleUnavailableException.class, () -> facade.rentVehicle(invalidVehicleRequest));

        // Subsystems A, B, C must have ZERO persisted records
        assertEquals(0, reservationRepo.findAll().size(), "Subsystem A must have 0 persisted records");
        assertEquals(0, depositRepo.findAll().size(), "Subsystem B must have 0 persisted records");
        assertEquals(0, dispatchRepo.findAll().size(), "Subsystem C must have 0 persisted records");

        // Chain log must record FAILED_AT_START
        List<RentalChainOrder> chains = chainRepo.findAll();
        assertEquals(1, chains.size(), "Chain log must record the failed request");
        RentalChainOrder failedChain = chains.get(0);
        assertEquals(ChainStepStatus.FAILED_AT_START, failedChain.getStatus());
        assertTrue(failedChain.getFailureReason().contains("NON-EXISTENT-TRUCK-999"));
    }
}
