package com.carrental.app;

import com.carrental.exception.CarRentalException;
import com.carrental.facade.*;
import com.carrental.orchestration.RentalChainOrder;
import com.carrental.orchestration.RentalChainRepository;
import com.carrental.subsystem.dispatch.DispatchFileRepository;
import com.carrental.subsystem.dispatch.DispatchService;
import com.carrental.subsystem.dispatch.PreTripInspectionEngine;
import com.carrental.subsystem.finance.DepositFileRepository;
import com.carrental.subsystem.finance.DepositRiskCalculator;
import com.carrental.subsystem.finance.DepositService;
import com.carrental.subsystem.reservation.FleetReservationValidator;
import com.carrental.subsystem.reservation.ReservationFileRepository;
import com.carrental.subsystem.reservation.ReservationService;

import java.util.List;
import java.util.Scanner;

/**
 * Interactive Command-Line Interface application for live defense demonstration.
 * Allows examiner to trigger happy path, B-failure rollback, C-failure two-step rollback,
 * inspect persisted files, simulate restarts, and reset data stores.
 */
public class CarRentalCLI {

    private final CarRentalFacade rentalFacade;
    private final RentalAdminFacade adminFacade;
    private final Scanner scanner;

    public CarRentalCLI(String dataDir) {
        ReservationFileRepository resRepo = new ReservationFileRepository(dataDir);
        FleetReservationValidator resValidator = new FleetReservationValidator();
        ReservationService resService = new ReservationService(resRepo, resValidator);

        DepositFileRepository depRepo = new DepositFileRepository(dataDir);
        DepositRiskCalculator depCalc = new DepositRiskCalculator();
        DepositService depService = new DepositService(depRepo, depCalc);

        DispatchFileRepository dispRepo = new DispatchFileRepository(dataDir);
        PreTripInspectionEngine dispEngine = new PreTripInspectionEngine();
        DispatchService dispService = new DispatchService(dispRepo, dispEngine);

        RentalChainRepository chainRepo = new RentalChainRepository(dataDir);

        this.rentalFacade = new CarRentalFacade(resService, depService, dispService, chainRepo);
        this.adminFacade = new RentalAdminFacade(resRepo, depRepo, dispRepo, chainRepo);
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        CarRentalCLI cli = new CarRentalCLI("data");
        if (args.length > 0 && args[0].equalsIgnoreCase("--demo")) {
            cli.runAutomatedDemo();
        } else {
            cli.runInteractiveLoop();
        }
    }

    public void runInteractiveLoop() {
        System.out.println("================================================================================");
        System.out.println("       CAR RENTAL FACADE: THREE-STAGE DURABLE ORCHESTRATION DEMO");
        System.out.println("================================================================================");

        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Select an option (1-8): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    handleHappyPath();
                    break;
                case "2":
                    handleStepBFailure();
                    break;
                case "3":
                    handleStepCFailure();
                    break;
                case "4":
                    handlePreFlightFailure();
                    break;
                case "5":
                    handleInspectPersistedStore();
                    break;
                case "6":
                    handleSimulateRestart();
                    break;
                case "7":
                    handleResetStore();
                    break;
                case "8":
                    System.out.println("Exiting demo application. Goodbye!");
                    running = false;
                    break;
                default:
                    System.out.println("Invalid option. Please enter a number between 1 and 8.");
            }
        }
    }

    public void runAutomatedDemo() {
        System.out.println("\n[AUTOMATED DEFENSE DEMO RUNNING]");
        handleResetStore();
        System.out.println("\n--- RUNNING SCENARIO 1: Happy Path A -> B -> C ---");
        handleHappyPath();
        System.out.println("\n--- RUNNING SCENARIO 2: Step B Failure (Deposit Declines) -> 1-Step Rollback (A) ---");
        handleStepBFailure();
        System.out.println("\n--- RUNNING SCENARIO 3: Step C Failure (Brake Defect) -> 2-Step Rollback (B then A) ---");
        handleStepCFailure();
        System.out.println("\n--- RUNNING SCENARIO 4: Pre-flight Rejection (Driver Age < 21) ---");
        handlePreFlightFailure();
        System.out.println("\n--- RUNNING SCENARIO 5: Inspecting All Persisted Storage on Disk ---");
        handleInspectPersistedStore();
        System.out.println("\n--- RUNNING SCENARIO 6: Simulating Crash and Persistence Reload ---");
        handleSimulateRestart();
    }

    private void printMenu() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("1. Trigger Full A -> B -> C Chain (Happy Path)");
        System.out.println("2. Trigger Step B Failure (Deposit Declined -> Compensate A alone)");
        System.out.println("3. Trigger Step C Failure (Safety Defect -> Two-Step Rollback: B then A)");
        System.out.println("4. Trigger Pre-flight Validation Failure (Driver Age < 21, Step 3.6)");
        System.out.println("5. Inspect Persisted Disk Storage & Chain Audit Logs");
        System.out.println("6. Simulate Application Restart & Reload State from Disk");
        System.out.println("7. Reset / Clean All Persisted Data Stores");
        System.out.println("8. Exit");
        System.out.println("--------------------------------------------------------------------------------");
    }

    private void handleHappyPath() {
        System.out.println("\nExecuting Happy Path Rental:");
        RentalRequest request = RentalRequest.standard("CUST-ALICE", 28, 740, "CAR-TESLA-303", 5, 85.0);
        try {
            RentalResult result = rentalFacade.rentVehicle(request);
            System.out.println("SUCCESS: " + result.getMessage());
            adminFacade.auditChain(result.getChainId()).printDiagnosticSummary();
        } catch (Exception e) {
            System.err.println("Unexpected failure: " + e.getMessage());
        }
    }

    private void handleStepBFailure() {
        System.out.println("\nExecuting Scenario: Step B Failure (Card declined / Credit limit exceeded):");
        RentalRequest request = RentalRequest.forceDepositFailure("CUST-BOB", 25, 520, "CAR-SUV-202", 3, 70.0);
        try {
            rentalFacade.rentVehicle(request);
        } catch (CarRentalException e) {
            System.out.println("EXPECTED EXCEPTION CAUGHT: " + e.getMessage());
            List<RentalChainOrder> chains = adminFacade.listAllChains();
            if (!chains.isEmpty()) {
                RentalChainOrder last = chains.get(chains.size() - 1);
                adminFacade.auditChain(last.getChainId()).printDiagnosticSummary();
            }
        }
    }

    private void handleStepCFailure() {
        System.out.println("\nExecuting Scenario: Step C Failure (Brake pressure failure during inspection):");
        RentalRequest request = RentalRequest.forceInspectionDefect("CUST-CHARLIE", 32, 710, "CAR-LUX-404", 4, 120.0);
        try {
            rentalFacade.rentVehicle(request);
        } catch (CarRentalException e) {
            System.out.println("EXPECTED EXCEPTION CAUGHT: " + e.getMessage());
            List<RentalChainOrder> chains = adminFacade.listAllChains();
            if (!chains.isEmpty()) {
                RentalChainOrder last = chains.get(chains.size() - 1);
                adminFacade.auditChain(last.getChainId()).printDiagnosticSummary();
            }
        }
    }

    private void handlePreFlightFailure() {
        System.out.println("\nExecuting Scenario: Pre-flight Validation Failure (Underage Driver):");
        RentalRequest request = RentalRequest.standard("CUST-UNDERAGE", 19, 680, "CAR-SEDAN-101", 2, 45.0);
        try {
            rentalFacade.rentVehicle(request);
        } catch (CarRentalException e) {
            System.out.println("EXPECTED EXCEPTION CAUGHT: " + e.getMessage());
        }
    }

    private void handleInspectPersistedStore() {
        List<RentalChainOrder> chains = adminFacade.listAllChains();
        System.out.println("\nTotal Chains Persisted on Disk: " + chains.size());
        for (RentalChainOrder c : chains) {
            adminFacade.auditChain(c.getChainId()).printDiagnosticSummary();
        }
    }

    private void handleSimulateRestart() {
        System.out.println("\nSimulating Application Restart...");
        System.out.println("Closing connections, clearing in-memory caches, and reloading directly from disk files...");
        adminFacade.reloadAllFromDisk();
        System.out.println("Reload complete! Persisted records recovered from disk:");
        List<RentalChainOrder> chains = adminFacade.listAllChains();
        System.out.println("Successfully reloaded " + chains.size() + " chains from disk store.");
    }

    private void handleResetStore() {
        System.out.println("\nResetting all persisted files in data/ directory...");
        adminFacade.resetAllStores();
        System.out.println("All data stores successfully cleared.");
    }
}
