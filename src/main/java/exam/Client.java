package exam;

import eu.boxwork.dhbw.examhelpers.rsa.RSAHelper;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Logger;

import static exam.Utility.*;

/**
 * This class represents the client, which will connect to the cluster of Nodes and send the RSA request.
 */
public class Client {
    private final Logger logger = initializeLogger("Client");

    private int primeCount;

    private static final HashMap<Integer, String> cipherMap;
    private static final HashMap<Integer, String> keyMap;

    //For cluster connection
    private InetAddress address;
    private int port;

    static {
        // Initialize the maps containing the cipher text and public key depending on the number of primes
        cipherMap = new HashMap<>();
        cipherMap.put(100, "b4820013b07bf8513ee59a905039fb631203c8b38ca3d59b475b4e4e092d3979");
        cipherMap.put(1000, "55708f0326a16870b299f913984922c7b5b37725ce0f6670d963adc0dc3451c8");
        cipherMap.put(10000, "a9fc180908ad5f60556fa42b3f76e30f48bcddfad906f312b6ca429f25cebbd0");
        cipherMap.put(100000, "80f7b3b84e8354b36386c6833fe5c113445ce74cd30a21236a5c70f5fdca7208");

        keyMap = new HashMap<>();
        keyMap.put(100, "298874689697528581074572362022003292763");
        keyMap.put(1000, "249488851623337787855631201847950907117");
        keyMap.put(10000, "237023640130486964288372516117459992717");
        keyMap.put(100000, "174351747363332207690026372465051206619");
    }

    private String encrypted;
    private String publicKey;

    private final RSAHelper helper = new RSAHelper();
    private final Stack<Map.Entry<InetAddress, Integer>> otherConnections = new Stack<>();
    private boolean primesFound = false;

    /**
     * Main loop for the client.
     */
    private void work() {
        logger.info("Started the client!");

        // Add the default node to the connections
        otherConnections.add(new AbstractMap.SimpleEntry<>(address, port));

        // Needed for the timing
        int reconnectCount = -1;
        double previousCalculation;
        long startTime = 0;
        long endTime = 0;

        do {
            previousCalculation = startTime == 0 ? 0 : System.currentTimeMillis() - startTime;

            // Connect to any socket in the system
            try {
                // Get the first element on the stack
                Map.Entry<InetAddress, Integer> clusterConnection = otherConnections.pop();
                Socket cluster = new Socket(clusterConnection.getKey(), clusterConnection.getValue());
                ObjectMessageHandler messageHandler = new ObjectMessageHandler(cluster, "Client");
                logger.info("Connected to: " + cluster);

                ++reconnectCount;

                // Send the RSA information
                messageHandler.write(createMessage("Cluster", MessageType.RSA, publicKey));
                logger.fine("Public key sent!");

                // Reset the list
                otherConnections.clear();

                // Read welcome message
                Message welcome = messageHandler.read();
                if (welcome.getMessageType() == MessageType.WELCOME) {
                    logger.fine("Received cluster welcome!");

                    // Save the given node connections
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


                    logger.fine("Received the connections " + otherConnections + " from the Cluster.");
                    logger.info("Waiting for the cluster to solve the problem...");

                    startTime = System.currentTimeMillis();
                    // Now wait for the cluster to solve the key
                    while (!cluster.isClosed()) {
                        Message incomingMessage = messageHandler.read();

                        // Only react to message of the type PRIMES
                        if (incomingMessage.getMessageType() == MessageType.PRIMES) {
                            // Get the primes which should decrypt the text
                            String[] primes = ((String) incomingMessage.getPayload()).replace(" ", "").split(",");
                            logger.info("Received the primes " + Arrays.toString(primes) + " from the cluster!");

                            String p = primes[0];
                            String q = primes[1];

                            primesFound = helper.isValid(p, q, publicKey);
                            if (primesFound) {
                                endTime = System.currentTimeMillis();

                                logger.info("Primes are valid!");
                                logger.info("Decrypted text is: " + helper.decrypt(p, q, encrypted));
                            } else {
                                logger.info("Primes dont fit!");
                            }

                            logger.info("Closing connection to the cluster!");

                            // Let the cluster know that the client received the primes
                            messageHandler.write(createMessage(
                                    "Cluster", MessageType.PRIMES_RECEIVED, ""
                            ));

                            cluster.close();
                        }
                    }
                }
            } catch (IOException | IllegalArgumentException | SecurityException e) {
                if (!primesFound) {
                    logger.warning(e.getClass().getName() + " while communicating with the cluster: " + e.getMessage());
                }
            }
        } while (!otherConnections.isEmpty() && !primesFound);

        if (primesFound) {
            // Calculate the duration it took the cluster to solve the problem
            double calcDuration = (endTime - startTime + previousCalculation) / 1000;

            logger.info("The calculation took " + calcDuration + " second(s) with " + reconnectCount + " reconnect(s)!");
        } else if (reconnectCount >= 0) {
            logger.severe("The cluster couldn't solve the problem!");
        } else {
            logger.info("Couldn't reach the cluster!");
        }
    }

    public static void main(String[] args) {
        // CLI options to create Client
        Options options = new Options();
        Option primeList = new Option("pr", "primes", true, "Number of how many primes to use for calculation (100/1000/10000/100000)");
        primeList.setRequired(true);
        options.addOption(primeList);
        Option clusterAddress = new Option("i", "caddress", true, "Define address of a Cluster node to address");
        clusterAddress.setRequired(true);
        options.addOption(clusterAddress);
        Option clusterPort = new Option("p", "cport", true, "Port of Node in cluster to connect to");
        clusterPort.setRequired(true);
        options.addOption(clusterPort);

        // Command parsing
        CommandLine cl = parseArguments(options, args);

        try {
            Client client = new Client();

            // Get parsed strings for client
            client.address = InetAddress.getByName(cl.getOptionValue("caddress"));
            client.port = Integer.parseInt(cl.getOptionValue("cport"));

            int primeCount = Integer.parseInt(cl.getOptionValue("primes"));

            if (isValidPrimeCount(primeCount)) {
                client.primeCount = primeCount;
                client.encrypted = cipherMap.get(client.primeCount);
                client.publicKey = keyMap.get(client.primeCount);

                client.work();
            }
        } catch (Exception e) {
            System.out.println(e.getClass().getName() + " while parsing command line arguments: " + e);
            System.exit(1);
        }
    }
}
