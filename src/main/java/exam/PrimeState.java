package exam;

/**
 * Represents the states a prime index can have.
 * <p>
 * OPEN: This range was not worked on
 * <p>
 * TENTATIVE: This range should be worked on by a node. Waiting for confirmation
 * <p>
 * WORKING: This range is actively being worked on
 * <p>
 * CLOSED: This range was worked on and is finished
 */
public enum PrimeState {
    OPEN,
    TENTATIVE,
    WORKING,
    CLOSED
}
