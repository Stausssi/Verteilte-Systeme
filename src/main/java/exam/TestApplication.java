package exam;

import java.net.UnknownHostException;

public class TestApplication {
    public static void main(String[] args) throws InterruptedException, UnknownHostException {
        Node node1 = new Node(4444, "Node1");
        Node node2 = new Node(4445, "Node2");
        Node node3 = new Node(2342, "Node3");

        Thread thread1 = new Thread(node1);
        Thread thread2 = new Thread(node2);
        Thread thread3 = new Thread(node3);

        thread1.start();
        Thread.sleep(1000);

        thread2.start();
        Thread.sleep(1000);
        node2.connectTo("localhost", 4444);

        thread3.start();
        Thread.sleep(1000);
        node3.connectTo("localhost", 4445);
        node1.connectTo("localhost", 2342);

        try {
            thread1.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
