package exam;

import java.util.Timer;
import java.util.TimerTask;

class Raft implements Runnable {
    public Node raftNode;
    final Timer electionTimeout = new Timer();
    private final TimerTask timeoutTask = new TimerTask() {
        @Override
        public void run() {
            //System.out.println("Raft: Waiting for connection!");
            while (raftNode.connections.isEmpty()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //System.out.println("Raft: Connection found!");
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
        //System.out.println("Raft: Thread started!");
        //Delay start of Raft to allow for connections
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Create random timeout for thread for election between 130 and 300 milliseconds
        long randomTimeout = (long) (Math.random() * (300 - 130 + 1) + 130);
        electionTimeout.schedule(timeoutTask, 5, randomTimeout);
    }


    private boolean checkLeader() {
        // Check whether there is a node which is not a Follower
        for (Connection c : raftNode.connections.values()) {
            if (c.getState() != State.FOLLOWER || raftNode.state != State.FOLLOWER) {
                return true;
            }
        }

        return false;
    }

    private void startElection() {
//        System.out.println("Election started!");
        raftNode.state = State.CANDIDATE;
        raftNode.hasVoted = true;

        // Send a broadcast message
        raftNode.addBroadcastMessage(
                MessageType.RAFT_ELECTION,
                ""
        );
    }

    private void writeEntry() {

    }

    public void initLeaderHeartbeat() {
        leaderHeartbeat = raftNode.restartTimer(
                leaderHeartbeat,
                new TimerTask() {
                    @Override
                    public void run() {
                        //System.out.println("Heartbeat" + raftNode.name);
                        // Send a broadcast message
                        raftNode.addBroadcastMessage(
                                MessageType.RAFT_HEARTBEAT,
                                raftNode.state
                        );
                    }
                },
                50,
                500
        );
    }

    public void stop() {
        raftNode.stopTimer(leaderHeartbeat);
        raftNode.stopTimer(electionTimeout);

//        raftNode.logConsole("Raft is stopped!");
    }
}