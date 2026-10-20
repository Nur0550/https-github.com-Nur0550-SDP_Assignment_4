package com.carrental.subsystem.dispatch;

import com.carrental.exception.VehicleSafetyInspectionException;

/**
 * Pure business logic class for Subsystem C.
 * Executes mechanical safety checklists and telematics signal verification.
 */
public class PreTripInspectionEngine {

    public void verifyVehicleSafetyAndGps(String vehicleId, boolean forceInspectionDefect) {
        if (forceInspectionDefect) {
            throw new VehicleSafetyInspectionException(
                    "Critical safety defect detected on " + vehicleId + ": hydraulic brake pressure loss during pre-trip inspection");
        }

        // Standard diagnostic checklist
        if (vehicleId != null && vehicleId.endsWith("-DEFECTIVE")) {
            throw new VehicleSafetyInspectionException(
                    "Pre-trip sensor fault on " + vehicleId + ": tire pressure monitoring warning and OBD-II trouble codes");
        }
    }

    public double readCurrentOdometer(String vehicleId) {
        // Simulated telematic odometer reading
        return 24500.0;
    }

    public int readCurrentFuelLevel(String vehicleId) {
        // Full tank requirement for dispatch
        return 100;
    }

    public String pairGpsTracker(String vehicleId) {
        return "GPS-UNIT-" + Math.abs(vehicleId.hashCode() % 10000);
    }
}
