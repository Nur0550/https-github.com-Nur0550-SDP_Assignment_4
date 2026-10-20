package com.carrental.subsystem.dispatch;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service gateway for Subsystem C (Vehicle Pre-trip Inspection & Dispatch).
 * Usable directly on its own outside the facade.
 */
public class DispatchService {

    private final DispatchFileRepository repository;
    private final PreTripInspectionEngine inspectionEngine;

    public DispatchService(DispatchFileRepository repository, PreTripInspectionEngine inspectionEngine) {
        this.repository = repository;
        this.inspectionEngine = inspectionEngine;
    }

    public DispatchRecord inspectAndDispatch(String reservationId, String vehicleId, boolean forceInspectionDefect) {
        // Step 1: Run safety diagnostics (throws VehicleSafetyInspectionException on failure)
        inspectionEngine.verifyVehicleSafetyAndGps(vehicleId, forceInspectionDefect);

        // Step 2: Read telematics
        double odometer = inspectionEngine.readCurrentOdometer(vehicleId);
        int fuelLevel = inspectionEngine.readCurrentFuelLevel(vehicleId);
        String gpsId = inspectionEngine.pairGpsTracker(vehicleId);

        String id = "DISP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DispatchRecord record = new DispatchRecord(
                id, reservationId, vehicleId, odometer, fuelLevel, gpsId,
                true, LocalDateTime.now(), DispatchStatus.PENDING_INSPECTION
        );
        record.markDispatched();
        repository.save(record);
        return record;
    }

    public void abortDispatch(String dispatchId) {
        DispatchRecord record = repository.findById(dispatchId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch record not found: " + dispatchId));
        record.abort();
        repository.save(record);
    }

    public Optional<DispatchRecord> findById(String dispatchId) {
        return repository.findById(dispatchId);
    }

    public Optional<DispatchRecord> findByReservationId(String reservationId) {
        return repository.findByReservationId(reservationId);
    }

    public DispatchFileRepository getRepository() {
        return repository;
    }
}
