package exam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.UnknownHostException;

public class TestApplication {
    public static void main(String[] args) throws InterruptedException, UnknownHostException {
        Node node1 = new Node(4444, "Node1");
        Node node2 = new Node(4445, "Node2");
        Node node3 = new Node(2342, "Node3");
        Node node4 = new Node(3333, "Node4");

        Thread thread1 = new Thread(node1);
        Thread thread2 = new Thread(node2);
        Thread thread3 = new Thread(node3);
        Thread thread4 = new Thread(node4);

        thread1.start();
        thread2.start();
        thread3.start();
        thread4.start();

        Thread.sleep(1000);
        node2.connectTo("localhost", 4444);

        node3.connectTo("localhost", 4445);
        node1.connectTo("localhost", 2342);

        node4.connectTo("localhost", 4445);

//        Thread.sleep(1000);
//        node1.logConnections();
//        node2.logConnections();
//        node3.logConnections();
//        node4.logConnections();

        try {
            thread1.join();
            thread2.join();
            thread3.join();
            thread4.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
