package exam;

import com.sun.org.apache.xml.internal.security.algorithms.MessageDigestAlgorithm;
import tasks.messages.ObjectMessageHandler;
import tasks.messages.Message;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Node represents a working element of the decryption task.
 * It has a server socket , to which other nodes can connect and send messages to, and a client socket, which will
 * connect and send messages to other nodes.
 * One Node in the system is the coordinator, which will distribute the tasks to the other nodes (so-called workers)
 */
public class Node implements Runnable {
    private static final int MAX_INCOMING_CLIENTS = 100;
    private final boolean allowNewConnections = true;

    private final int port;
    protected final String name;
    private State state;

    public volatile ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    // Create threads for socket server and client
    protected final SocketServer socketServer = new SocketServer();
//    private final Raft leaderElection = new Raft(this);

    public Node(int port, String name) {
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
    }

    @Override
    public void run() {
        Thread serverThread = new Thread(socketServer);
//        Thread clientThread = new Thread(socketClient);
        Thread communicator = new Thread(new CommunicationHandler());

        // TODO: Eventuell Kommunikation hier handeln
        serverThread.start();
        communicator.start();

        try {
            serverThread.join();
            communicator.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * This class will handle the incoming connections and receive messages.
     */
    protected class SocketServer implements Runnable {

        @Override
        public void run() {
            // Open the ServerSocket
            try {
                ServerSocket serverSocket = new ServerSocket(
                        port,
                        MAX_INCOMING_CLIENTS,
                        InetAddress.getByName("localhost")
                );

                logConsole("Started server socket on: " + serverSocket);

                while (allowNewConnections) {
                    Socket newConnection = serverSocket.accept();
                    ObjectMessageHandler tempHandler = new ObjectMessageHandler(newConnection);

                    // Send the node a serialized version of IP:Port combinations in the connections object
                    Message serConnections = new Message();
                    serConnections.setSender(name);
                    serConnections.setMessageType(MessageType.CLUSTER);
                    serConnections.setType("cluster");
                    logConsole("Cluster Keys: " + connections.keys());
//                    serConnections.setPayload(connections.keys());
//                    tempHandler.write(serConnections);

                    // Also send a port request
                    Message request = new Message();
                    request.setSender(name);
//                    request.setType("request");
                    request.setMessageType(MessageType.REQUEST);
                    request.setPayload("port");
                    tempHandler.write(request);

                    // Add newConnection to the HashMap
                    connections.put(
                            createConnectionKey(newConnection.getInetAddress()),
                            new Connection(newConnection.getInetAddress(), -1, "", newConnection)
                    );
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This class is used to handle the communication between the Nodes.
     * It reads incoming messages and sends the corresponding responses.
     */
    private class CommunicationHandler implements Runnable {
        private final List<Message> broadcastMessages = new ArrayList<>();
        private final HashMap<Connection, Message> outgoingMessages = new HashMap<>();

        @Override
        public void run() {
            logConsole("The CommunicationHandler was started");
            while (true) {
                // Iterate over every connection
                for (Map.Entry<String, Connection> entry : connections.entrySet()) {
                    // Get the connection object and the message handler
                    Connection c = entry.getValue();
                    ObjectMessageHandler messageHandler = c.getMessageHandler();

                    try {
                        // Check whether any messages are available
                        if (messageHandler.isMessageAvailable()) {
                            // Parse the incoming message
                            parseMessage(messageHandler.read(), c);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    // Send the node each broadcast message
                    for (Message broadcast : broadcastMessages) {
                        messageHandler.write(broadcast);
                    }

                    // Send the messages which are only directed at the connection
                    if (outgoingMessages.containsKey(c)) {
                        messageHandler.write(outgoingMessages.get(c));
                        outgoingMessages.remove(c);
                    }
                }
            }
        }

        private void parseMessage(Message incomingMessage, Connection connection) {
            logConsole("Incoming " + incomingMessage + "\nFrom Connection: " + connection.getName());
            // Update the name of the connection
            connection.setName(incomingMessage.getSender());

            switch (incomingMessage.getMessageType()) {
                case REQUEST:
                    logConsole("This is a request");

                    switch ((RequestTypes) incomingMessage.getPayload()) {
                        case PORT:
                            Message reply = new Message();
                            reply.setSender(name);
                            reply.setReceiver(incomingMessage.getSender());
                            reply.setMessageType(MessageType.PORT);
                            reply.setPayload(port);

                            // Save the message to the outgoing hashmap
                            outgoingMessages.put(
                                    connection,
                                    reply
                            );
                            break;
                        default:
                            logConsole("Unknown request type");
                    }
                    break;
                case CLUSTER:
                    logConsole("Cluster information received");

                    // TODO: Connect to each given IP:Port combination
                    break;
                case PORT:
                    int port = (Integer) incomingMessage.getPayload();
                    logConsole("Port received: " + port);

                    // Check whether the there is a connection entry with a missing port
                    if (connections.containsKey(connection.getSocket().getInetAddress() + ":NaN")) {
                        logConsole("This is a port message to a new connection!");

                        Connection c = connections.get(connection.getSocket().getInetAddress() + ":NaN");

                        // Update the name of the connection
                        c.setName(incomingMessage.getSender());

                        // Remove the existing entry and add a new one with the correct key
                        connections.remove(connection.getSocket().getInetAddress() + ":NaN");
                        connections.put(connection.getSocket().getInetAddress() + ":" + port, c);

                        // Connect to the new node
//                    socketClient.connectTo(connection.getInetAddress(), (Integer) incomingMessage.getPayload());
                    }
                    break;
                case RSA:
                    logConsole("Received RSA information from the client!");

                    String publicKey = (String) incomingMessage.getPayload();

                    logConsole("Public Key: " + publicKey);
//                // TODO: Either forward message to the coordinator or delegate tasks to every worker
//                // For now, just answer with the primes
//                Message primes = new Message();
//                primes.setType("primes");
//                primes.setPayload("17594063653378370033, 15251864654563933379");
//
//                messageHandler.write(primes);
//
//                logConsole("Primes sent!");
//                connection.close();
                    break;
                default:
                    logConsole("Message fits no type");
            }
        }
    }

    /**
     * This method creates a new socket and connects to the given address and port.
     *
     * @param address The address of the socket server to connect to
     * @param port The port of the socket server to connect to
     */
    public void connectTo(InetAddress address, int port) {
        try {
            // Try connecting to the given socket
            Socket clientSocket = new Socket(address, port);

            logConsole("Connected to: " + clientSocket);

            // Save that connection
            Connection connectionInformation = new Connection(address, port, "", clientSocket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method logs a given message to the console and prepends the node name in front of the message
     * @param log The message to log to sysout
     */
    private void logConsole(String log) {
        System.out.println("[" + this.name + "]: " + log);
    }

    /**
     * This method creates a connection key consisting of IP and port
     * @param address The address of the connection
     * @param port The port of the connection
     * @return "address:port"
     */
    public String createConnectionKey(InetAddress address, int port) {
        return address + ":" + port;
    }

    /**
     * This method creates a connection key for a connection whose port is unknown
     * @param address The address of the connection
     * @return "address:NaN"
     */
    public String createConnectionKey(InetAddress address) {
        return address + ":Nan";
    }
}
