package com.carrental.subsystem.finance;

import com.carrental.exception.DepositDeclinedException;

/**
 * Pure business logic class for Subsystem B.
 * Evaluates financial risk, required security deposit, and insurance rates.
 */
public class DepositRiskCalculator {

    private static final int MINIMUM_CREDIT_SCORE = 580;
    private static final double BASE_DEPOSIT = 300.0;
    private static final double BASE_INSURANCE_PER_DAY = 18.0;

    public void evaluateCreditWorthiness(int creditScore, boolean forceDecline) {
        if (forceDecline) {
            throw new DepositDeclinedException("Deposit authorization declined by banking merchant: card rejected or credit limit exceeded");
        }
        if (creditScore < MINIMUM_CREDIT_SCORE) {
            throw new DepositDeclinedException(
                    "Credit score " + creditScore + " is below minimum authorization threshold of " + MINIMUM_CREDIT_SCORE);
        }
    }

    public double calculateSecurityDeposit(int creditScore, double vehicleDailyRate) {
        double deposit = BASE_DEPOSIT + (vehicleDailyRate * 2.0);
        if (creditScore < 650) {
            deposit += 200.0; // Higher security deposit for moderate credit risk
        }
        return Math.round(deposit * 100.0) / 100.0;
    }

    public double calculateInsurancePremium(int rentalDays, int creditScore) {
        double dailyRate = BASE_INSURANCE_PER_DAY;
        if (creditScore > 750) {
            dailyRate -= 3.0; // Tier 1 discount
        }
        return Math.round(dailyRate * rentalDays * 100.0) / 100.0;
    }
}
