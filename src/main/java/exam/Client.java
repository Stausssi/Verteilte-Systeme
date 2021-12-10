package exam;

import eu.boxwork.dhbw.examhelpers.rsa.RSAHelper;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;

/**
 * This class represents the client, which will connect to the cluster of Nodes and send the RSA request.
 */
public class Client {
    private static final int primeCount = 10000;

    private static final HashMap<Integer, String> cipherMap;
    static {
        cipherMap = new HashMap<>();
        cipherMap.put(100, "b4820013b07bf8513ee59a905039fb631203c8b38ca3d59b475b4e4e092d3979");
        cipherMap.put(1000, "55708f0326a16870b299f913984922c7b5b37725ce0f6670d963adc0dc3451c8");
        cipherMap.put(10000, "a9fc180908ad5f60556fa42b3f76e30f48bcddfad906f312b6ca429f25cebbd0");
        cipherMap.put(100000, "80f7b3b84e8354b36386c6833fe5c113445ce74cd30a21236a5c70f5fdca7208");
    }

    private static final HashMap<Integer, String> keyMap;
    static {
        keyMap = new HashMap<>();
        keyMap.put(100, "298874689697528581074572362022003292763");
        keyMap.put(1000, "249488851623337787855631201847950907117");
        keyMap.put(10000, "237023640130486964288372516117459992717");
        keyMap.put(100000, "174351747363332207690026372465051206619");
    }

    private static final String encrypted = cipherMap.get(primeCount);
    private static final String publicKey = keyMap.get(primeCount);

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

        // Needed for the timing
        int reconnectCount = -1;
        double previousCalculation;
        long startTime = 0;
        long endTime = 0;

        do {
            ++reconnectCount;
            previousCalculation = startTime == 0 ? 0 : System.currentTimeMillis() - startTime;

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
                System.out.println("Connected to the cluster!");
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

//                System.out.println("Received the connections " + otherConnections + " from the Cluster.");

                startTime = System.currentTimeMillis();
                System.out.println("Waiting for the cluster to solve the problem...");
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
                            endTime = System.currentTimeMillis();

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

        // Calculate the duration it took the cluster to solve the problem
        double calcDuration = (endTime - startTime + previousCalculation) / 1000;

        System.out.println("The calculation took " + calcDuration + " second(s) with " + reconnectCount + " reconnects!");
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.work();
    }
}
