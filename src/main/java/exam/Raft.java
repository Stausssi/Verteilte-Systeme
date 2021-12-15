package exam;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import static exam.Utility.*;

/**
 * Implements the raft protocol
 */
class Raft implements Runnable {
    public static final int heartbeatInterval = 500;
    public static final int timeoutTolerance = 5000;

    private final Node raftNode;
    private Logger logger;

    final Timer electionTimeout = new Timer();
    private final TimerTask timeoutTask = new TimerTask() {
        @Override
        public void run() {
            // Only run if there is at least a single node connected
            while (raftNode.connections.isEmpty()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Start the election if there is no leader
            if (!checkLeader()) {
                startElection();
            }
        }
    };

    private Timer leaderHeartbeat;

    /**
     * Creates the raft protocol of the given node.
     *
     * @param node the node
     */
    public Raft(Node node) {
        this.raftNode = node;
    }

    @Override
    public void run() {
        logger = initializeLogger(raftNode.name);

        logger.info("Raft started!");

        // Create random timeout for thread for election between 130 and 300 milliseconds
        long randomTimeout = (long) (Math.random() * (300 - 130 + 1) + 130);
        electionTimeout.schedule(timeoutTask, 2000, randomTimeout);
    }

    /**
     * Checks whether there is a leader in the current cluster.
     *
     * @return true, if there is a leader, false otherwise
     */
    private boolean checkLeader() {
        // Check whether there is a node which is not a Follower
        for (Connection c : raftNode.connections.values()) {
            if (c.getState() != State.FOLLOWER || raftNode.state != State.FOLLOWER) {
                logger.fine("Cluster has a leader!");
                return true;
            }
        }

        logger.fine("Cluster has no leader!");
        return false;
    }

    /**
     * Starts the Raft election by suggesting this node as the new leader
     */
    private void startElection() {
        logger.info("Raft election started!");
        raftNode.state = State.CANDIDATE;
        raftNode.hasVoted = true;

        // Send a broadcast message
        raftNode.addBroadcastMessage(
                MessageType.RAFT_ELECTION,
                ""
        );
    }

    /**
     * Starts the heartbeat of the leader.
     */
    public void initLeaderHeartbeat() {
        leaderHeartbeat = restartTimer(
                leaderHeartbeat,
                new TimerTask() {
                    @Override
                    public void run() {
                        // Send a broadcast message
                        raftNode.addBroadcastMessage(
                                MessageType.RAFT_HEARTBEAT,
                                raftNode.state
                        );
                    }
                },
                10,
                heartbeatInterval
        );
    }

    /**
     * Stops the raft protocol.
     */
    public void stop() {
        stopTimer(leaderHeartbeat);
        stopTimer(electionTimeout);

        logger.info("Raft shutdown!");
    }
}