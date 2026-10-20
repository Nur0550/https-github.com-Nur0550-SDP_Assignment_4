package com.carrental.facade;

/**
 * Data Transfer Object encapsulating client parameters for a car rental transaction.
 */
public class RentalRequest {

    private final String customerId;
    private final int driverAge;
    private final int creditScore;
    private final String vehicleId;
    private final int rentalDays;
    private final double dailyRate;
    private final boolean forceDepositDecline;
    private final boolean forceInspectionDefect;

    public RentalRequest(String customerId, int driverAge, int creditScore, String vehicleId,
                         int rentalDays, double dailyRate,
                         boolean forceDepositDecline, boolean forceInspectionDefect) {
        this.customerId = customerId;
        this.driverAge = driverAge;
        this.creditScore = creditScore;
        this.vehicleId = vehicleId;
        this.rentalDays = rentalDays;
        this.dailyRate = dailyRate;
        this.forceDepositDecline = forceDepositDecline;
        this.forceInspectionDefect = forceInspectionDefect;
    }

    public static RentalRequest standard(String customerId, int driverAge, int creditScore,
                                         String vehicleId, int rentalDays, double dailyRate) {
        return new RentalRequest(customerId, driverAge, creditScore, vehicleId, rentalDays, dailyRate, false, false);
    }

    public static RentalRequest forceDepositFailure(String customerId, int driverAge, int creditScore,
                                                    String vehicleId, int rentalDays, double dailyRate) {
        return new RentalRequest(customerId, driverAge, creditScore, vehicleId, rentalDays, dailyRate, true, false);
    }

    public static RentalRequest forceInspectionDefect(String customerId, int driverAge, int creditScore,
                                                      String vehicleId, int rentalDays, double dailyRate) {
        return new RentalRequest(customerId, driverAge, creditScore, vehicleId, rentalDays, dailyRate, false, true);
    }

    public String getCustomerId() {
        return customerId;
    }

    public int getDriverAge() {
        return driverAge;
    }

    public int getCreditScore() {
        return creditScore;
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

    public boolean isForceDepositDecline() {
        return forceDepositDecline;
    }

    public boolean isForceInspectionDefect() {
        return forceInspectionDefect;
    }
}
