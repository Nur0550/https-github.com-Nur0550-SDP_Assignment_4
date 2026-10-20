package com.carrental.subsystem.finance;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Service gateway for Subsystem B (Financial Security Deposit & Insurance).
 * Usable directly on its own outside the facade.
 */
public class DepositService {

    private final DepositFileRepository repository;
    private final DepositRiskCalculator calculator;

    public DepositService(DepositFileRepository repository, DepositRiskCalculator calculator) {
        this.repository = repository;
        this.calculator = calculator;
    }

    public DepositTransaction authorizeAndHoldDeposit(String reservationId, String customerId,
                                                      int creditScore, double vehicleDailyRate,
                                                      int rentalDays, boolean forceDecline) {
        calculator.evaluateCreditWorthiness(creditScore, forceDecline);

        double deposit = calculator.calculateSecurityDeposit(creditScore, vehicleDailyRate);
        double insurance = calculator.calculateInsurancePremium(rentalDays, creditScore);

        String id = "DEP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DepositTransaction transaction = new DepositTransaction(
                id, reservationId, customerId, deposit, insurance, creditScore,
                LocalDateTime.now(), DepositStatus.PENDING_AUTHORIZATION
        );
        transaction.hold();
        repository.save(transaction);
        return transaction;
    }

    public void refundDeposit(String transactionId) {
        DepositTransaction transaction = repository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Deposit transaction not found: " + transactionId));
        transaction.refund();
        repository.save(transaction);
    }

    public Optional<DepositTransaction> findById(String transactionId) {
        return repository.findById(transactionId);
    }

    public Optional<DepositTransaction> findByReservationId(String reservationId) {
        return repository.findByReservationId(reservationId);
    }

    public DepositFileRepository getRepository() {
        return repository;
    }
}
