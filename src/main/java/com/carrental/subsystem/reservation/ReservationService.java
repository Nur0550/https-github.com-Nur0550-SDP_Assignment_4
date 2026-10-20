package com.carrental.subsystem.reservation;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service gateway for Subsystem A (Vehicle Intake and Reservation).
 * Usable directly on its own outside the facade.
 */
public class ReservationService {

    private final ReservationFileRepository repository;
    private final FleetReservationValidator validator;

    public ReservationService(ReservationFileRepository repository, FleetReservationValidator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    public VehicleReservation createReservation(String customerId, int driverAge, String vehicleId,
                                                int rentalDays, double dailyRate) {
        validator.validateDriverEligibility(driverAge);
        validator.validateVehicleAvailability(vehicleId);
        double totalCost = validator.calculateTotalCost(rentalDays, dailyRate);

        String id = "RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        VehicleReservation reservation = new VehicleReservation(
                id, customerId, vehicleId, rentalDays, dailyRate, totalCost,
                LocalDateTime.now(), ReservationStatus.PENDING
        );
        repository.save(reservation);
        return reservation;
    }

    public void confirmReservation(String reservationId) {
        VehicleReservation reservation = repository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
        reservation.confirm();
        repository.save(reservation);
    }

    public void cancelReservation(String reservationId) {
        VehicleReservation reservation = repository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
        reservation.cancel();
        repository.save(reservation);
    }

    public Optional<VehicleReservation> findById(String reservationId) {
        return repository.findById(reservationId);
    }

    public ReservationFileRepository getRepository() {
        return repository;
    }
}
