package exam;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class TestApplication {
    public static void main(String[] args) throws InterruptedException, UnknownHostException {
        int[] ports = {4445, 2342, 3333, 6969, 6900, 1234};
        List<Thread> openedThreads = new ArrayList<>();

        Node defaultNode = new Node(4444, "Main Node");
        Thread defaultThread = new Thread(defaultNode);
        defaultThread.start();
        openedThreads.add(defaultThread);

        Thread.sleep(1000);

        for (int port : ports) {
            Node node = new Node(port, "Node " + port);
            Thread thread = new Thread(node);
            thread.start();

            Thread.sleep(500);
            node.connectTo("localhost", 4444);
            openedThreads.add(thread);
        }

//        defaultNode.logConnections();

        try {
            for (Thread t : openedThreads) {
                t.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
