package exam;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

class Raft implements Runnable {
    Node raftNode;
    final Timer electionTimeout = new Timer();
    private final TimerTask timeoutTask = new TimerTask() {
        @Override
        public void run() {

            while (raftNode.connectedTo.isEmpty()) {
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
        System.out.println(raftNode.connectedTo.keySet());
        return false;
    }

    private void startElection() throws IOException {
        System.out.println("Election started!");
        for (Map.Entry<String, Node.SocketClient.ClientCommunicator> entry : raftNode.connectedTo.entrySet()) {
            Node.SocketClient.ClientCommunicator communicator = entry.getValue();

            // Create the message object
            Message newConnection = new Message();
            newConnection.setSender(raftNode.name);
            newConnection.setType("connection");
            newConnection.setPayload(raftNode.name);
            newConnection.setType("election");
            // Send it
            communicator.sendMessage(newConnection);
        }

        String[] connect = raftNode.connectedTo.keySet().toString().split(":");
        System.out.println();
        int port = Integer.parseInt(connect[1].substring(0,4));
        Socket followers = new Socket("localhost", port );
        ObjectMessageHandler messageHandler = new ObjectMessageHandler(followers);
        Message election = new Message();
        election.setReceiver(raftNode.connectedTo.keySet().toString());
        election.setSender(raftNode.name);
        election.setType("election");
        election.setPayload(raftNode.name);
        messageHandler.write(election);
    }

    private void voteLeader() {

    }

    private void nodeHeartbeat() {

    }

    private void writeEntry() {

    }
}