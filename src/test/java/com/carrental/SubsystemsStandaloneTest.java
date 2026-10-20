package com.carrental;

import com.carrental.subsystem.dispatch.*;
import com.carrental.subsystem.finance.*;
import com.carrental.subsystem.reservation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class SubsystemsStandaloneTest {

    @TempDir
    Path tempDir;

    private ReservationService reservationService;
    private DepositService depositService;
    private DispatchService dispatchService;

    @BeforeEach
    void setUp() {
        String dir = tempDir.toString();
        reservationService = new ReservationService(new ReservationFileRepository(dir), new FleetReservationValidator());
        depositService = new DepositService(new DepositFileRepository(dir), new DepositRiskCalculator());
        dispatchService = new DispatchService(new DispatchFileRepository(dir), new PreTripInspectionEngine());
    }

    @Test
    @DisplayName("Subsystem A standalone: creates, confirms, and enforces legal state transitions")
    void testSubsystemAStandalone() {
        VehicleReservation res = reservationService.createReservation("CUST-A1", 25, "CAR-SEDAN-101", 3, 50.0);
        assertEquals(ReservationStatus.PENDING, res.getStatus());
        assertEquals(150.0, res.getTotalCost(), 0.01);

        reservationService.confirmReservation(res.getReservationId());
        VehicleReservation confirmed = reservationService.findById(res.getReservationId()).orElseThrow();
        assertEquals(ReservationStatus.CONFIRMED, confirmed.getStatus());

        // Cancel confirmed reservation
        reservationService.cancelReservation(res.getReservationId());
        VehicleReservation cancelled = reservationService.findById(res.getReservationId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());

        // Enforce entity invariant: cannot cancel already cancelled reservation
        assertThrows(IllegalStateException.class, cancelled::cancel);
    }

    @Test
    @DisplayName("Subsystem B standalone: authorizes, holds, refunds, and enforces legal state transitions")
    void testSubsystemBStandalone() {
        DepositTransaction dep = depositService.authorizeAndHoldDeposit("RES-B1", "CUST-B1", 720, 60.0, 4, false);
        assertEquals(DepositStatus.HELD, dep.getStatus());
        assertTrue(dep.getDepositAmount() > 0.0);

        depositService.refundDeposit(dep.getTransactionId());
        DepositTransaction refunded = depositService.findById(dep.getTransactionId()).orElseThrow();
        assertEquals(DepositStatus.REFUNDED, refunded.getStatus());

        // Enforce entity invariant: cannot refund already refunded deposit
        assertThrows(IllegalStateException.class, refunded::refund);
    }

    @Test
    @DisplayName("Subsystem C standalone: inspects, dispatches, aborts, and enforces legal state transitions")
    void testSubsystemCStandalone() {
        DispatchRecord disp = dispatchService.inspectAndDispatch("RES-C1", "CAR-TESLA-303", false);
        assertEquals(DispatchStatus.DISPATCHED, disp.getStatus());
        assertTrue(disp.isInspectionPassed());

        dispatchService.abortDispatch(disp.getDispatchId());
        DispatchRecord aborted = dispatchService.findById(disp.getDispatchId()).orElseThrow();
        assertEquals(DispatchStatus.ABORTED, aborted.getStatus());

        // Enforce entity invariant: cannot abort already aborted record
        assertThrows(IllegalStateException.class, aborted::abort);
    }
}
