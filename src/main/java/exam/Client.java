package exam;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.IOException;
import java.net.Socket;

/**
 * This class represents the client, which will connect to the cluster of Nodes and send the RSA request.
 */
public class Client {
    public static void main(String[] args) {
        // Connect to any socket in the system
        try {
            Socket cluster = new Socket("localhost", 4444);
            ObjectMessageHandler messageHandler = new ObjectMessageHandler(cluster);

            // On connect, a port prompt will be received
            // Since this is not a Node, simply ignore this request
            Message incomingMessage = messageHandler.read();

            if (MessageHelper.isPortRequest(incomingMessage)) {
                System.out.println("Connected to the cluster!");
                // TODO: Send the RSA details to the cluster
                while (cluster.isConnected()) {
                    incomingMessage = messageHandler.read();
                    System.out.println("Received: " + incomingMessage);
                }
            } else {
                System.out.println("Received a invalid message from the Cluster!");
                cluster.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
