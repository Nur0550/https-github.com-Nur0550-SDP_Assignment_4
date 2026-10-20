package com.carrental.orchestration;

/**
 * Status tracking the lifecycle and compensation state of the three-stage chain.
 * Persisted durably to disk so progress is never lost across application restarts.
 */
public enum ChainStepStatus {
    INITIALIZED,
    STEP_A_COMMITTED,
    STEP_B_COMMITTED,
    COMPLETED,
    COMPENSATING_A,
    COMPENSATED_A_ONLY,
    COMPENSATING_B_THEN_A,
    COMPENSATED_FULL,
    FAILED_AT_START
}
