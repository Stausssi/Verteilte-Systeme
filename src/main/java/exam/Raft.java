package exam;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import static exam.Utility.*;

class Raft implements Runnable {
    public static final int heartbeatInterval = 500;
    public static final int timeoutTolerance = 2500;

    private final Node raftNode;
    private Logger logger;

    final Timer electionTimeout = new Timer();
    private final TimerTask timeoutTask = new TimerTask() {
        @Override
        public void run() {
            while (raftNode.connections.isEmpty()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (!checkLeader()) {
                startElection();
            }
        }
    };

    private Timer leaderHeartbeat;

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

    public void stop() {
        stopTimer(leaderHeartbeat);
        stopTimer(electionTimeout);

        logger.info("Raft shutdown!");
    }
}