package exam;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class Raft implements Runnable {
    public Node_OLD raftNodeOLD;
    final Timer electionTimeout = new Timer();
    private final TimerTask timeoutTask = new TimerTask() {
        @Override
        public void run() {

            while (raftNodeOLD.connectedTo.isEmpty()) {
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

    public Raft(Node_OLD nodeOLD) {
        this.raftNodeOLD = nodeOLD;
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
        System.out.println(raftNodeOLD.connectedTo.keySet());
        return false;
    }

    private void startElection() throws IOException {
        System.out.println("Election started!");
        for (Map.Entry<String, Node_OLD.SocketClient.ClientCommunicator> entry : raftNodeOLD.connectedTo.entrySet()) {
            Node_OLD.SocketClient.ClientCommunicator communicator = entry.getValue();

            // Create the message object
            Message newConnection = new Message();
            newConnection.setSender(raftNodeOLD.name);
            newConnection.setType("connection");
            newConnection.setPayload(raftNodeOLD.name);
            newConnection.setType("election");
            // Send it
            communicator.sendMessage(newConnection);
        }

        String[] connect = raftNodeOLD.connectedTo.keySet().toString().split(":");
        System.out.println();
        int port = Integer.parseInt(connect[1].substring(0,4));
        Socket followers = new Socket("localhost", port );
        ObjectMessageHandler messageHandler = new ObjectMessageHandler(followers);
        Message election = new Message();
        election.setReceiver(raftNodeOLD.connectedTo.keySet().toString());
        election.setSender(raftNodeOLD.name);
        election.setType("election");
        election.setPayload(raftNodeOLD.name);
        messageHandler.write(election);
    }

    private void voteLeader() {

    }

    private void nodeHeartbeat() {

    }

    private void writeEntry() {

    }
}