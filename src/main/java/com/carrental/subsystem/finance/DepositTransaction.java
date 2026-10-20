package com.carrental.subsystem.finance;

import java.time.LocalDateTime;

/**
 * Entity representing a security deposit and insurance hold transaction.
 * Strictly enforces legal transitions on its own:
 * PENDING_AUTHORIZATION -> HELD -> REFUNDED.
 */
public class DepositTransaction {

    private final String transactionId;
    private final String reservationId;
    private final String customerId;
    private final double depositAmount;
    private final double insurancePremium;
    private final int creditScore;
    private final LocalDateTime timestamp;
    private DepositStatus status;

    public DepositTransaction(String transactionId, String reservationId, String customerId,
                              double depositAmount, double insurancePremium, int creditScore,
                              LocalDateTime timestamp, DepositStatus status) {
        this.transactionId = transactionId;
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.depositAmount = depositAmount;
        this.insurancePremium = insurancePremium;
        this.creditScore = creditScore;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.status = status != null ? status : DepositStatus.PENDING_AUTHORIZATION;
    }

    public synchronized void hold() {
        if (this.status != DepositStatus.PENDING_AUTHORIZATION) {
            throw new IllegalStateException("Cannot hold deposit in status: " + this.status);
        }
        this.status = DepositStatus.HELD;
    }

    public synchronized void refund() {
        if (this.status != DepositStatus.HELD) {
            throw new IllegalStateException("Cannot refund deposit that is not currently HELD. Current status: " + this.status);
        }
        this.status = DepositStatus.REFUNDED;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public double getDepositAmount() {
        return depositAmount;
    }

    public double getInsurancePremium() {
        return insurancePremium;
    }

    public int getCreditScore() {
        return creditScore;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public DepositStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("DepositTransaction[id=%s, resId=%s, deposit=$%.2f, ins=$%.2f, status=%s]",
                transactionId, reservationId, depositAmount, insurancePremium, status);
    }
}
