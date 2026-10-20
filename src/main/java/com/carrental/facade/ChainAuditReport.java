package com.carrental.facade;

import com.carrental.orchestration.RentalChainOrder;
import com.carrental.subsystem.dispatch.DispatchRecord;
import com.carrental.subsystem.finance.DepositTransaction;
import com.carrental.subsystem.reservation.VehicleReservation;

/**
 * Audit snapshot consolidating the persisted states of all three subsystems for an order.
 */
public class ChainAuditReport {

    private final RentalChainOrder chainOrder;
    private final VehicleReservation reservation;
    private final DepositTransaction deposit;
    private final DispatchRecord dispatch;

    public ChainAuditReport(RentalChainOrder chainOrder, VehicleReservation reservation,
                            DepositTransaction deposit, DispatchRecord dispatch) {
        this.chainOrder = chainOrder;
        this.reservation = reservation;
        this.deposit = deposit;
        this.dispatch = dispatch;
    }

    public RentalChainOrder getChainOrder() {
        return chainOrder;
    }

    public VehicleReservation getReservation() {
        return reservation;
    }

    public DepositTransaction getDeposit() {
        return deposit;
    }

    public DispatchRecord getDispatch() {
        return dispatch;
    }

    public void printDiagnosticSummary() {
        System.out.println("================================================================================");
        System.out.println("                 CHAIN AUDIT REPORT: " + (chainOrder != null ? chainOrder.getChainId() : "UNKNOWN"));
        System.out.println("================================================================================");
        System.out.println("Chain Status   : " + (chainOrder != null ? chainOrder.getStatus() : "N/A"));
        System.out.println("Customer       : " + (chainOrder != null ? chainOrder.getCustomerId() : "N/A"));
        System.out.println("Vehicle        : " + (chainOrder != null ? chainOrder.getVehicleId() : "N/A"));
        System.out.println("Failure Reason : " + (chainOrder != null ? chainOrder.getFailureReason() : "None"));
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("[Subsystem A - Reservation]: " + (reservation != null ? reservation.toString() : "(None recorded)"));
        System.out.println("[Subsystem B - Deposit]    : " + (deposit != null ? deposit.toString() : "(None recorded)"));
        System.out.println("[Subsystem C - Dispatch]   : " + (dispatch != null ? dispatch.toString() : "(None recorded)"));
        System.out.println("================================================================================");
    }
}
