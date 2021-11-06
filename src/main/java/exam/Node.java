package exam;

import tasks.messages.ObjectMessageHandler;
import tasks.messages.Message;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Node represents a working element of the decryption task.
 * It has a server socket , to which other nodes can connect and send messages to, and a client socket, which will
 * connect and send messages to other nodes.
 * One Node in the system is the coordinator, which will distribute the tasks to the other nodes (so-called workers)
 */
public class Node implements Runnable {
    private static final int MAX_INCOMING_CLIENTS = 100;
    private boolean allowNewConnections = true;

    private final InetAddress address;
    private final int port;
    protected final String name;
    private State state;

    public volatile ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

    // Create threads for socket server and client
    protected final SocketServer socketServer = new SocketServer();
//    private final Raft leaderElection = new Raft(this);

    public Node(int port, String name) throws UnknownHostException {
        this.address = InetAddress.getByName("localhost");
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
    }

    @Override
    public void run() {
        Thread serverThread = new Thread(socketServer);
        Thread communicator = new Thread(new CommunicationHandler());

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
                        address
                );

                logConsole("Started server socket on: " + serverSocket);

                while (allowNewConnections) {
                    Socket newConnection = serverSocket.accept();
                    ObjectMessageHandler tempHandler = new ObjectMessageHandler(newConnection);

                    // Read the "hello" message
                    // TODO: Exception handling
                    Message hello = tempHandler.read();
                    if (hello.getMessageType() == MessageType.HELLO) {
//                        logConsole("Hello message received: " + hello);

                        String connectionName = hello.getSender();
                        int port = (Integer) hello.getPayload();

                        // Send the node a serialized version of IP:Port combinations in the connections object
                        Message welcome = new Message();
                        welcome.setSender(name);
                        welcome.setReceiver(connectionName);
                        welcome.setMessageType(MessageType.WELCOME);

                        StringBuilder connectionsBuilder = new StringBuilder();
                        for (String key : Collections.list(connections.keys())) {
                            connectionsBuilder.append(key);
                            connectionsBuilder.append(",");
                        }

                        if (connectionsBuilder.length() > 0) {
                            connectionsBuilder.deleteCharAt(connectionsBuilder.lastIndexOf(","));
                        }

//                        logConsole("Cluster Keys: " + connectionsBuilder);
                        welcome.setPayload(connectionsBuilder.toString());
                        tempHandler.write(welcome);

                        // Add newConnection to the HashMap
                        connections.put(
                                createConnectionKey(newConnection.getInetAddress(), port),
                                new Connection(newConnection.getInetAddress(), port, connectionName, newConnection)
                        );
                    } else {
                        logConsole("Wrong " + hello);
                        newConnection.close();
                    }
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
                    logConsole("Message fits no type" + incomingMessage);
                    break;
            }
        }
    }

    /**
     * This method creates a new socket and connects to the given address and port.
     *
     * @param address The address of the socket server to connect to
     * @param port    The port of the socket server to connect to
     */
    public void connectTo(InetAddress address, int port) {
        // Only connect if either the given address or the port differs from the own
        if (port != this.port || !removeHostFromAddress(address).equals(removeHostFromAddress(this.address))) {
            try {
                // Try connecting to the given socket
                Socket clientSocket = new Socket(address, port);
                ObjectMessageHandler tempHandler = new ObjectMessageHandler(clientSocket);

//                logConsole("Connected to: " + clientSocket);

                // Send a hello message
                Message hello = new Message();
                hello.setMessageType(MessageType.HELLO);
                hello.setSender(name);
                hello.setPayload(this.port);

                tempHandler.write(hello);
//                logConsole("Hello message sent: " + hello);

                // Read the welcome message
                Message welcome = tempHandler.read();
                if (welcome.getMessageType() == MessageType.WELCOME) {
//                    logConsole("Welcome message received: " + welcome);
                    String connectionName = welcome.getSender();

                    // Save the connection
                    connections.put(
                            createConnectionKey(address, port),
                            new Connection(address, port, connectionName, clientSocket)
                    );

                    for (String connectionInformation : ((String) welcome.getPayload()).split(",")) {
                        if (connectionInformation.length() > 0 && !connections.containsKey(connectionInformation)) {
//                            logConsole("new connection information: " + connectionInformation);
                            String[] connection = connectionInformation.split(":");
                            String[] ipParts = connection[0].split("\\.");

                            // Connect to the connection
                            connectTo(
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
                            );
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This method connects to a socket with a given host name. It converts the host name and calls connectTo with an
     * InetAddress.
     *
     * @param host The hostname to convert to an address.
     * @param port The port of the socket connection.
     */
    public void connectTo(String host, int port) {
        try {
            connectTo(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method logs a given message to the console and prepends the node name in front of the message
     *
     * @param log The message to log to sysout
     */
    private void logConsole(String log) {
        System.out.println("[" + this.name + "]: " + log);
    }

    /**
     * This method creates a connection key consisting of IP and port
     *
     * @param address The address of the connection
     * @param port    The port of the connection
     * @return "address:port"
     */
    public String createConnectionKey(InetAddress address, int port) {
        return removeHostFromAddress(address) + ":" + port;
    }

    /**
     * This method creates a connection key for a connection whose port is unknown
     *
     * @param address The address of the connection
     * @return "address:NaN"
     */
    public String createConnectionKey(InetAddress address) {
        return removeHostFromAddress(address) + ":Nan";
    }

    /**
     * This small helper removes the host name from the string representation of an InetAddress.
     *
     * @param address The address
     * @return A String containing the IP-Address of the InetAddress.
     */
    private String removeHostFromAddress(InetAddress address) {
        return address.toString().split("/")[1];
    }

    public void logConnections() {
        logConsole("Connected to: " + connections);
    }
}
