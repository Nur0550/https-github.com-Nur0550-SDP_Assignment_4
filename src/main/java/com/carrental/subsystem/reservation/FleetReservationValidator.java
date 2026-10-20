package com.carrental.subsystem.reservation;

import com.carrental.exception.InvalidRentalRequestException;
import com.carrental.exception.VehicleUnavailableException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure business logic class for Subsystem A.
 * Evaluates driver eligibility, fleet inventory rules, and pricing calculations.
 */
public class FleetReservationValidator {

    private static final int MINIMUM_DRIVER_AGE = 21;
    private static final Set<String> ACTIVE_FLEET = ConcurrentHashMap.newKeySet();

    static {
        ACTIVE_FLEET.add("CAR-SEDAN-101");
        ACTIVE_FLEET.add("CAR-SUV-202");
        ACTIVE_FLEET.add("CAR-TESLA-303");
        ACTIVE_FLEET.add("CAR-LUX-404");
    }

    public void validateDriverEligibility(int driverAge) {
        if (driverAge < MINIMUM_DRIVER_AGE) {
            throw new InvalidRentalRequestException(
                    "Driver age " + driverAge + " is below minimum rental age requirement of " + MINIMUM_DRIVER_AGE);
        }
    }

    public void validateVehicleAvailability(String vehicleId) {
        if (vehicleId == null || !ACTIVE_FLEET.contains(vehicleId)) {
            throw new VehicleUnavailableException("Requested vehicle " + vehicleId + " is not in the active fleet inventory");
        }
    }

    public double calculateTotalCost(int rentalDays, double dailyRate) {
        if (rentalDays <= 0) {
            throw new InvalidRentalRequestException("Rental duration must be at least 1 day");
        }
        if (dailyRate <= 0) {
            throw new InvalidRentalRequestException("Daily rate must be positive");
        }
        return Math.round(rentalDays * dailyRate * 100.0) / 100.0;
    }
}
