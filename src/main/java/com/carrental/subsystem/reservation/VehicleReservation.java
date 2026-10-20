package com.carrental.subsystem.reservation;

import java.time.LocalDateTime;

/**
 * Entity representing a vehicle intake reservation.
 * Strictly enforces legal lifecycle transitions on its own:
 * PENDING -> CONFIRMED -> CANCELLED.
 */
public class VehicleReservation {

    private final String reservationId;
    private final String customerId;
    private final String vehicleId;
    private final int rentalDays;
    private final double dailyRate;
    private final double totalCost;
    private final LocalDateTime createdAt;
    private ReservationStatus status;

    public VehicleReservation(String reservationId, String customerId, String vehicleId,
                              int rentalDays, double dailyRate, double totalCost,
                              LocalDateTime createdAt, ReservationStatus status) {
        this.reservationId = reservationId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.rentalDays = rentalDays;
        this.dailyRate = dailyRate;
        this.totalCost = totalCost;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.status = status != null ? status : ReservationStatus.PENDING;
    }

    public synchronized void confirm() {
        if (this.status != ReservationStatus.PENDING) {
            throw new IllegalStateException("Cannot confirm reservation in status: " + this.status);
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public synchronized void cancel() {
        if (this.status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Reservation is already cancelled");
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public String getReservationId() {
        return reservationId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public int getRentalDays() {
        return rentalDays;
    }

    public double getDailyRate() {
        return dailyRate;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("VehicleReservation[id=%s, customer=%s, vehicle=%s, days=%d, cost=$%.2f, status=%s]",
                reservationId, customerId, vehicleId, rentalDays, totalCost, status);
    }
}
