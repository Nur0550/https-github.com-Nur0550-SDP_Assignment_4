package com.carrental;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class PersistenceReloadCrashRecoveryTest {

    @TempDir
    Path sharedStorageDir;

    @Test
    @DisplayName("Persistence Reload Test: committed records survive application termination and reload from disk")
    void testCommittedStateSurvivesApplicationRestart() {
        String dir = sharedStorageDir.toString();

        // RUNTIME SESSION 1: Process a successful rental order and a failed/compensated order
        ReservationFileRepository resRepo1 = new ReservationFileRepository(dir);
        ReservationService resService1 = new ReservationService(resRepo1, new FleetReservationValidator());
        DepositFileRepository depRepo1 = new DepositFileRepository(dir);
        DepositService depService1 = new DepositService(depRepo1, new DepositRiskCalculator());
        DispatchFileRepository dispRepo1 = new DispatchFileRepository(dir);
        DispatchService dispService1 = new DispatchService(dispRepo1, new PreTripInspectionEngine());
        RentalChainRepository chainRepo1 = new RentalChainRepository(dir);
        CarRentalFacade facade1 = new CarRentalFacade(resService1, depService1, dispService1, chainRepo1);

        RentalRequest request1 = RentalRequest.standard("CUST-P1", 26, 730, "CAR-TESLA-303", 3, 85.0);
        RentalResult result1 = facade1.rentVehicle(request1);
        String completedChainId = result1.getChainId();

        // Verify count before shutdown
        assertEquals(1, chainRepo1.findAll().size());
        assertEquals(1, resRepo1.findAll().size());
        assertEquals(1, depRepo1.findAll().size());
        assertEquals(1, dispRepo1.findAll().size());

        // SIMULATE CRASH & RESTART (Destroy Session 1 objects completely)
        resRepo1 = null;
        depRepo1 = null;
        dispRepo1 = null;
        chainRepo1 = null;
        facade1 = null;

        // RUNTIME SESSION 2: Brand new facade and repositories created against the existing directory
        ReservationFileRepository resRepo2 = new ReservationFileRepository(dir);
        DepositFileRepository depRepo2 = new DepositFileRepository(dir);
        DispatchFileRepository dispRepo2 = new DispatchFileRepository(dir);
        RentalChainRepository chainRepo2 = new RentalChainRepository(dir);
        RentalAdminFacade adminFacade2 = new RentalAdminFacade(resRepo2, depRepo2, dispRepo2, chainRepo2);

        // Assert all committed records were read back accurately from disk
        assertEquals(1, chainRepo2.findAll().size(), "Chain orders must survive process restart");
        assertEquals(1, resRepo2.findAll().size(), "Reservations must survive process restart");
        assertEquals(1, depRepo2.findAll().size(), "Deposits must survive process restart");
        assertEquals(1, dispRepo2.findAll().size(), "Dispatches must survive process restart");

        RentalChainOrder reloadedChain = chainRepo2.findById(completedChainId).orElseThrow();
        assertEquals(ChainStepStatus.COMPLETED, reloadedChain.getStatus());

        VehicleReservation reloadedRes = resRepo2.findById(reloadedChain.getReservationId()).orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, reloadedRes.getStatus());
        assertEquals(255.0, reloadedRes.getTotalCost(), 0.01);

        DepositTransaction reloadedDep = depRepo2.findById(reloadedChain.getDepositId()).orElseThrow();
        assertEquals(DepositStatus.HELD, reloadedDep.getStatus());

        DispatchRecord reloadedDisp = dispRepo2.findById(reloadedChain.getDispatchId()).orElseThrow();
        assertEquals(DispatchStatus.DISPATCHED, reloadedDisp.getStatus());
    }

    @Test
    @DisplayName("Crash Recovery Test: Admin facade resumes interrupted compensation mid-chain after restart")
    void testCrashRecoveryPicksUpInterruptedCompensationFromDisk() {
        String dir = sharedStorageDir.toString();

        // Manually simulate an interrupted state on disk (crash occurred while executing 2-step rollback)
        ReservationFileRepository resRepo = new ReservationFileRepository(dir);
        DepositFileRepository depRepo = new DepositFileRepository(dir);
        DispatchFileRepository dispRepo = new DispatchFileRepository(dir);
        RentalChainRepository chainRepo = new RentalChainRepository(dir);

        // Create a reservation in CONFIRMED state
        VehicleReservation res = new VehicleReservation("RES-CRASH", "CUST-CRASH", "CAR-SUV-202", 3, 70.0, 210.0,
                LocalDateTime.now(), ReservationStatus.CONFIRMED);
        resRepo.save(res);

        // Create a deposit in HELD state
        DepositTransaction dep = new DepositTransaction("DEP-CRASH", "RES-CRASH", "CUST-CRASH", 440.0, 54.0, 680,
                LocalDateTime.now(), DepositStatus.HELD);
        depRepo.save(dep);

        // Create chain in COMPENSATING_B_THEN_A status (system was killed before completing unwinding)
        RentalChainOrder interruptedChain = new RentalChainOrder("CHAIN-CRASH-01", "CUST-CRASH", "CAR-SUV-202",
                "RES-CRASH", "DEP-CRASH", null, ChainStepStatus.COMPENSATING_B_THEN_A, "Server crash mid-rollback",
                LocalDateTime.now(), LocalDateTime.now());
        chainRepo.save(interruptedChain);

        // SYSTEM RESTARTS: Admin Facade inspects disk and executes recovery
        RentalAdminFacade adminFacade = new RentalAdminFacade(resRepo, depRepo, dispRepo, chainRepo);
        boolean recovered = adminFacade.recoverOrCompleteCompensation("CHAIN-CRASH-01");

        assertTrue(recovered, "Admin facade must successfully recover interrupted chain");

        // Assert that B was refunded and A was cancelled based on persisted status!
        DepositTransaction recoveredDep = depRepo.findById("DEP-CRASH").orElseThrow();
        assertEquals(DepositStatus.REFUNDED, recoveredDep.getStatus(), "Deposit must be refunded during recovery");

        VehicleReservation recoveredRes = resRepo.findById("RES-CRASH").orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, recoveredRes.getStatus(), "Reservation must be cancelled during recovery");

        RentalChainOrder recoveredChain = chainRepo.findById("CHAIN-CRASH-01").orElseThrow();
        assertEquals(ChainStepStatus.COMPENSATED_FULL, recoveredChain.getStatus(), "Chain status must transition to COMPENSATED_FULL");
    }
}
