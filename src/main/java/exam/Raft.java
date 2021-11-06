package exam;

import tasks.messages.Message;

import java.io.IOException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

class Raft implements Runnable {
    public Node raftNode;
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
                System.out.println("test2");
                try {
                    startElection();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else{
                System.out.println("else2");
            }
        }
    };

    public Raft(Node node) {
        this.raftNode = node;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Raft thread started!");
        electionTimeout.schedule(timeoutTask, new Date());
    }


    private boolean checkLeader() {
        System.out.println(raftNode.connections.keySet());
        return false;
    }

    private void startElection() throws IOException {
        System.out.println("Election started!");

        // Create the message object
        Message election = new Message();
        election.setPayload(raftNode.name);
        election.setType("election");

        // Send a broadcast message
        raftNode.broadcastMessages.add(election);

//        String[] connect = raftNode.connections.keySet().toString().split(":");
//        System.out.println();
//        int port = Integer.parseInt(connect[1].substring(0,4));
//        Socket followers = new Socket("localhost", port );
//        ObjectMessageHandler messageHandler = new ObjectMessageHandler(followers);
//        Message election = new Message();
//        election.setReceiver(raftNode.connections.keySet().toString());
//        election.setSender(raftNode.name);
//        election.setType("election");
//        election.setPayload(raftNode.name);
//        messageHandler.write(election);
    }

    private void voteLeader() {

    }

    private void nodeHeartbeat() {

    }

    private void writeEntry() {

    }
}