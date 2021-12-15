package exam;

/**
 * Contains every state a Node can be.
 * <p>
 * FOLLOWER: This node is given tasks and works on them
 * <p>
 * CANDIDATE: This node wants to be the new leader
 * <p>
 * LEADER: This node is the leader and distributes work
 */
public enum State {
    FOLLOWER, CANDIDATE, LEADER
}
