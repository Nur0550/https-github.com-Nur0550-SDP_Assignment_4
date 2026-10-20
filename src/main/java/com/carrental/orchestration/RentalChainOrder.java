package com.carrental.orchestration;

import java.time.LocalDateTime;

/**
 * Entity tracking the durable lifecycle, subsystem foreign keys, and progress
 * of a multi-stage car rental chain operation.
 */
public class RentalChainOrder {

    private final String chainId;
    private final String customerId;
    private final String vehicleId;
    private String reservationId;
    private String depositId;
    private String dispatchId;
    private ChainStepStatus status;
    private String failureReason;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RentalChainOrder(String chainId, String customerId, String vehicleId,
                            String reservationId, String depositId, String dispatchId,
                            ChainStepStatus status, String failureReason,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.chainId = chainId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.reservationId = reservationId;
        this.depositId = depositId;
        this.dispatchId = dispatchId;
        this.status = status != null ? status : ChainStepStatus.INITIALIZED;
        this.failureReason = failureReason != null ? failureReason : "";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public synchronized void updateStatus(ChainStepStatus newStatus, String failureReason) {
        this.status = newStatus;
        if (failureReason != null) {
            this.failureReason = failureReason;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public String getChainId() {
        return chainId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDepositId() {
        return depositId;
    }

    public void setDepositId(String depositId) {
        this.depositId = depositId;
        this.updatedAt = LocalDateTime.now();
    }

    public String getDispatchId() {
        return dispatchId;
    }

    public void setDispatchId(String dispatchId) {
        this.dispatchId = dispatchId;
        this.updatedAt = LocalDateTime.now();
    }

    public ChainStepStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return String.format("RentalChainOrder[id=%s, cust=%s, veh=%s, resId=%s, depId=%s, dispId=%s, status=%s, reason='%s']",
                chainId, customerId, vehicleId, reservationId, depositId, dispatchId, status, failureReason);
    }
}
