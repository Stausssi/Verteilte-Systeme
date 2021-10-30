package exam;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

class Raft implements Runnable {
    Node raftNode;
    final Timer electionTimeout = new Timer();
    private TimerTask timeoutTask = new TimerTask() {
        @Override
        public void run() {
            while (raftNode.connectedTo.isEmpty()) {
                System.out.println(raftNode.connectedTo.keySet());
                try {
                    wait(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            boolean leader = checkLeader();
            if (!leader) {
                startElection();
            }
        }
    };

    public Raft(Node node) {
        this.raftNode = node;
    }

    @Override
    public void run() {
        System.out.println("Raft thread started!");
        electionTimeout.schedule(timeoutTask, new Date());
    }


    private boolean checkLeader() {
        System.out.println(raftNode.connectedTo.keySet());
        return false;
    }

    private void startElection() {

    }

    private void voteLeader() {

    }

    private void nodeHeartbeat() {

    }

    private void writeEntry() {

    }
}