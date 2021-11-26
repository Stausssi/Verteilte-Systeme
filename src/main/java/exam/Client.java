package exam;

import eu.boxwork.dhbw.examhelpers.rsa.RSAHelper;
import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Stack;

/**
 * This class represents the client, which will connect to the cluster of Nodes and send the RSA request.
 */
public class Client {
    private static final String encrypted = "2d80afa14a65a7bf26636f97c89b43d5";
    private static final String publicKey = "268342277565109549360836262560222031507";

    private final RSAHelper helper = new RSAHelper();
    private final Stack<Map.Entry<InetAddress, Integer>> otherConnections = new Stack<>();
    private boolean primesFound = false;

    private void work() {
        try {
            // Add the default node to the connections
            otherConnections.add(new AbstractMap.SimpleEntry<>(InetAddress.getByName("localhost"), 4444));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        do {
            // Connect to any socket in the system
            try {
                // Get the first element on the stack
                Map.Entry<InetAddress, Integer> clusterConnection = otherConnections.pop();

                Socket cluster = new Socket(clusterConnection.getKey(), clusterConnection.getValue());
                ObjectMessageHandler messageHandler = new ObjectMessageHandler(cluster);

                // Send the RSA information
                Message rsaInfo = new Message();
                rsaInfo.setSender("Client");
                rsaInfo.setMessageType(MessageType.RSA);
                rsaInfo.setPayload(publicKey);
                messageHandler.write(rsaInfo);

                // Reset the list
                otherConnections.clear();

                // Read welcome message
                Message welcome = messageHandler.read();
                if (welcome.getMessageType() == MessageType.WELCOME) {
                    for (String connectionInformation : ((String) welcome.getPayload()).split(",")) {
                        if (connectionInformation.length() > 0) {
                            String[] connection = connectionInformation.split(":");
                            String[] ipParts = connection[0].split("\\.");

                            otherConnections.push(new AbstractMap.SimpleEntry<>(
                                    // Create the InetAddress object
                                    InetAddress.getByAddress(
                                            new byte[]{
                                                    (byte) Integer.parseInt(ipParts[0]),
                                                    (byte) Integer.parseInt(ipParts[1]),
                                                    (byte) Integer.parseInt(ipParts[2]),
                                                    (byte) Integer.parseInt(ipParts[3]),
                                            }
                                    ),
                                    // Parse the port
                                    Integer.parseInt(connection[1])
                            ));
                        }
                    }
                }

//            System.out.println("Received the connections " + otherConnections + " from the Cluster.");

                // Now wait for the cluster to solve the key
                while (!cluster.isClosed()) {
                    Message incomingMessage = messageHandler.read();
//                    System.out.println("Received: " + incomingMessage);

                    if (incomingMessage.getMessageType() == MessageType.PRIMES) {
                        String[] primes = ((String) incomingMessage.getPayload()).replace(" ", "").split(",");
                        System.out.println("Received the primes " + Arrays.toString(primes) + " from the cluster!");

                        String p = primes[0];
                        String q = primes[1];

                        primesFound = helper.isValid(p, q, publicKey);
                        if (primesFound) {
                            System.out.println("Primes are valid!");
                            System.out.println("Decrypted text is: " + helper.decrypt(p, q, encrypted));
                        } else {
                            System.out.println("Primes dont fit!");
                        }

                        cluster.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } while (!otherConnections.isEmpty() && !primesFound);

        System.out.println(("I'm done now thanks"));
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.work();
    }
}
