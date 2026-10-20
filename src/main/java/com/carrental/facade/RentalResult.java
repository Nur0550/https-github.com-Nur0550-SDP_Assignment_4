package com.carrental.facade;

import com.carrental.orchestration.ChainStepStatus;

/**
 * Data Transfer Object returned to client after orchestration.
 */
public class RentalResult {

    private final boolean success;
    private final String chainId;
    private final String reservationId;
    private final String depositId;
    private final String dispatchId;
    private final ChainStepStatus finalStatus;
    private final String message;

    public RentalResult(boolean success, String chainId, String reservationId,
                        String depositId, String dispatchId, ChainStepStatus finalStatus, String message) {
        this.success = success;
        this.chainId = chainId;
        this.reservationId = reservationId;
        this.depositId = depositId;
        this.dispatchId = dispatchId;
        this.finalStatus = finalStatus;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getChainId() {
        return chainId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getDepositId() {
        return depositId;
    }

    public String getDispatchId() {
        return dispatchId;
    }

    public ChainStepStatus getFinalStatus() {
        return finalStatus;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("RentalResult[success=%b, chainId=%s, status=%s, msg='%s']",
                success, chainId, finalStatus, message);
    }
}
