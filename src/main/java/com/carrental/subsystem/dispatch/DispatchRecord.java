package com.carrental.subsystem.dispatch;

import java.time.LocalDateTime;

/**
 * Entity representing final vehicle inspection, telematics verification, and dispatch.
 * Strictly enforces legal lifecycle transitions on its own:
 * PENDING_INSPECTION -> DISPATCHED -> ABORTED.
 */
public class DispatchRecord {

    private final String dispatchId;
    private final String reservationId;
    private final String vehicleId;
    private final double odometerKm;
    private final int fuelLevelPercent;
    private final String gpsTrackerId;
    private final boolean inspectionPassed;
    private final LocalDateTime dispatchTime;
    private DispatchStatus status;

    public DispatchRecord(String dispatchId, String reservationId, String vehicleId,
                          double odometerKm, int fuelLevelPercent, String gpsTrackerId,
                          boolean inspectionPassed, LocalDateTime dispatchTime, DispatchStatus status) {
        this.dispatchId = dispatchId;
        this.reservationId = reservationId;
        this.vehicleId = vehicleId;
        this.odometerKm = odometerKm;
        this.fuelLevelPercent = fuelLevelPercent;
        this.gpsTrackerId = gpsTrackerId;
        this.inspectionPassed = inspectionPassed;
        this.dispatchTime = dispatchTime != null ? dispatchTime : LocalDateTime.now();
        this.status = status != null ? status : DispatchStatus.PENDING_INSPECTION;
    }

    public synchronized void markDispatched() {
        if (!inspectionPassed) {
            throw new IllegalStateException("Cannot dispatch vehicle that failed safety inspection");
        }
        if (this.status != DispatchStatus.PENDING_INSPECTION) {
            throw new IllegalStateException("Cannot dispatch vehicle in status: " + this.status);
        }
        this.status = DispatchStatus.DISPATCHED;
    }

    public synchronized void abort() {
        if (this.status == DispatchStatus.ABORTED) {
            throw new IllegalStateException("Dispatch record is already aborted");
        }
        this.status = DispatchStatus.ABORTED;
    }

    public String getDispatchId() {
        return dispatchId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public double getOdometerKm() {
        return odometerKm;
    }

    public int getFuelLevelPercent() {
        return fuelLevelPercent;
    }

    public String getGpsTrackerId() {
        return gpsTrackerId;
    }

    public boolean isInspectionPassed() {
        return inspectionPassed;
    }

    public LocalDateTime getDispatchTime() {
        return dispatchTime;
    }

    public DispatchStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("DispatchRecord[id=%s, resId=%s, vehicle=%s, gps=%s, passed=%s, status=%s]",
                dispatchId, reservationId, vehicleId, gpsTrackerId, inspectionPassed, status);
    }
}
