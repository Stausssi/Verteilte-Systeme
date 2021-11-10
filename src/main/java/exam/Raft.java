package exam;

import tasks.messages.Message;
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


        //Create random timeout for thread for election between 5-10 seconds
        long randomTimeout = (long) (Math.random() * (10000 - 5000 + 1) + 5000);
        electionTimeout.schedule(timeoutTask,0,randomTimeout);
    }


    private boolean checkLeader() {
        return false;
    }

    private void startElection(){
//        System.out.println("Election started!");
        raftNode.state = State.CANDIDATE;

        // Create the message object
        Message election = new Message();
        election.setSender(raftNode.name);
        election.setMessageType(MessageType.RAFT_ELECTION);

        // Send a broadcast message
        raftNode.broadcastMessages.add(election);
    }

    private void voteLeader() {

    }

    private void nodeHeartbeat(){
        //System.out.println("Heartbeat" + raftNode.name);

        // Create the message object
        Message heartbeat = new Message();
        heartbeat.setSender(raftNode.name);
        heartbeat.setMessageType(MessageType.RAFT_HEARTBEAT);

        // Send a broadcast message
        raftNode.broadcastMessages.add(heartbeat);
    }

    private void writeEntry() {

    }
}