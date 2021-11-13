package exam;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A Node represents a working element of the decryption task.
 * It has a server socket , to which other nodes can connect and send messages to, and a client socket, which will
 * connect and send messages to other nodes.
 * One Node in the system is the coordinator, which will distribute the tasks to the other nodes (so-called workers)
 */
public class Node implements Runnable {
    private static final int MAX_INCOMING_CLIENTS = 100;
    private boolean nodeRunning = true;

    private final InetAddress address;
    private final int port;
    protected final String name;
    protected State state;

    public volatile ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    public volatile ConcurrentHashMap<Connection, Message> outgoingMessages = new ConcurrentHashMap<>();
    public volatile ConcurrentLinkedQueue<Message> broadcastMessages = new ConcurrentLinkedQueue<>();

    // Raft stuff
    protected boolean hasVoted = false;
    private int votesReceived = 0;
    private int voteCount = 0;

    private Timer leaderHeartbeat;
    private Timer leaderTimeout;

    // Create threads for socket server and client
    protected final SocketServer socketServer = new SocketServer();
    private final Raft raft = new Raft(this);

    public Node(int port, String name) throws UnknownHostException {
        this.address = InetAddress.getByName("localhost");
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
    }

    public Node(InetAddress address, int port, String name) {
        this.address = address;
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
    }

    @Override
    public void run() {
        // Create the threads for server, communicator and Raft protocol
        Thread serverThread = new Thread(socketServer);
        Thread communicatorThread = new Thread(new CommunicationHandler());
        Thread raftThread = new Thread(raft);

        serverThread.start();
        communicatorThread.start();
        raftThread.start();

        try {
            serverThread.join();
            communicatorThread.join();
            raftThread.join();
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

                while (nodeRunning) {
                    // Accept a new connection
                    Socket newConnection = serverSocket.accept();
                    ObjectMessageHandler tempHandler = new ObjectMessageHandler(newConnection);

                    // Read the "hello" message
                    Message firstMessage = tempHandler.read();
                    if (firstMessage.getMessageType() == MessageType.HELLO) {
//                        logConsole("Hello message received: " + hello);

                        String connectionName = firstMessage.getSender();
                        int port = (Integer) firstMessage.getPayload();

                        if (connectionName != null && port > 0) {
                            // Send the node a serialized version of IP:Port combinations in the connections object
                            // Add each connection key IP:Port to a string, separated by ,
                            StringBuilder connectionsBuilder = new StringBuilder();
                            for (String key : Collections.list(connections.keys())) {
                                connectionsBuilder.append(key);
                                connectionsBuilder.append(",");
                            }

                            // Remove the trailing comma
                            if (connectionsBuilder.length() > 0) {
                                connectionsBuilder.deleteCharAt(connectionsBuilder.lastIndexOf(","));
                            }

//                            logConsole("Cluster Keys: " + connectionsBuilder);
                            tempHandler.write(createMessage(
                                    connectionName,
                                    MessageType.WELCOME,
                                    connectionsBuilder.toString()
                            ));

                            // Add newConnection to the HashMap
                            connections.put(
                                    createConnectionKey(newConnection.getInetAddress(), port),
                                    new Connection(newConnection.getInetAddress(), port, connectionName, newConnection)
                            );

                            // Inform the new node of the current state of this node
                            tempHandler.write(createMessage(
                                    connectionName,
                                    MessageType.STATE,
                                    state
                            ));
                        }
                    } else if (firstMessage.getMessageType() == MessageType.RSA && "Client".equalsIgnoreCase(firstMessage.getSender())) {
//                        logConsole("Received RSA information from the client!");
//                        String publicKey = (String) firstMessage.getPayload();
//                        logConsole("Public Key: " + publicKey);

                        // Broadcast the public key
                        broadcastMessages.add(firstMessage);

                        // For now, just answer with the primes
                        tempHandler.write(createMessage(
                                firstMessage.getSender(),
                                MessageType.PRIMES,
                                "17594063653378370033, 15251864654563933379"
                        ));
                    } else {
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
        @Override
        public void run() {
            logConsole("The CommunicationHandler was started");
            while (nodeRunning) {
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

                    // Send the messages which are only directed at the connection
                    if (outgoingMessages.containsKey(c)) {
                        messageHandler.write(outgoingMessages.get(c));
                        outgoingMessages.remove(c);
                    }
                }

                // Go through every broadcast message
                for (Message broadcast : broadcastMessages) {
                    for (Connection c : connections.values()) {
                        c.getMessageHandler().write(broadcast);

//                        logConsole("Sent broadcast " + b + " to " + c.getName() + " (" + c.getSocket() + ")");
                    }

                    // Remove the head. Should always be the element which was sent
                    broadcastMessages.remove();
                }
//
//                try {
//                    Thread.sleep(200);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
            }
        }

        private void parseMessage(Message incomingMessage, Connection connection) {
//            logConsole("Incoming " + incomingMessage + "\nFrom Connection: " + connection.getName());

            switch (incomingMessage.getMessageType()) {
                case REQUEST:
                    logConsole("This is a request");
                    break;
                case RSA:
                    logConsole("PublicKey received: " + incomingMessage.getPayload());
                    break;
                case RAFT_ELECTION:
                    logConsole("Raft Election started by " + incomingMessage.getSender());

                    // Reply to the candidate with whether we already voted.
                    // Already voted -> false
                    // Not voted -> true
                    outgoingMessages.put(connection, createMessage(
                            incomingMessage.getSender(),
                            MessageType.RAFT_VOTE,
                            !hasVoted
                    ));

                    if (!hasVoted) {
                        logConsole(incomingMessage.getSender() + " has my vote!");
                        connection.setState(State.CANDIDATE);
                        hasVoted = true;
                    } else {
                        logConsole(incomingMessage.getSender() + " does not have my vote!");
                    }
                    break;
                case RAFT_VOTE:
                    if (state == State.CANDIDATE) {
                        // Add one to the vote count if the node elected this node
                        voteCount = (boolean) incomingMessage.getPayload() ? voteCount + 1 : voteCount;
                        votesReceived++;

                        // Check whether this node has enough votes
                        if (voteCount > connections.size() / 2) {
                            logConsole("Im the boss in town");

                            state = State.LEADER;

                            // Start the heartbeat task
                            if (leaderHeartbeat != null) {
                                leaderHeartbeat.cancel();
                                leaderHeartbeat.purge();
                            }

                            leaderHeartbeat = new Timer();
                            leaderHeartbeat.schedule(raft.createHeartbeatTask(), 50, 500);
                        } else if (votesReceived == connections.size()) {
                            logConsole("I was not elected Sadge");

                            // Reset own state and values
                            state = State.FOLLOWER;
                            hasVoted = false;
                            voteCount = 0;
                            votesReceived = 0;
                        } else {
                            break;
                        }

                        // Inform everyone of the new state
                        addBroadcastMessage(
                                MessageType.STATE,
                                state
                        );
                    }
                    break;
                case RAFT_HEARTBEAT:
//                    logConsole("Heartbeat received by " + incomingMessage.getSender());
                    if (state == State.LEADER) {
                        if (incomingMessage.getPayload() == State.FOLLOWER) {
                            // Reset timer for node closure
                            Timer temp = connection.getNodeTimeout();
                            if (temp != null) {
                                temp.cancel();
                                temp.purge();
                            }

                            temp = new Timer();
                            temp.schedule(new NodeTimeoutTask(connection) {
                                @Override
                                public void run() {
                                    logConsole(this.node.getName() + " disconnected!");
                                    handleNodeTimeout(this.node);
                                }
                            }, 2000);

                            connection.setNodeTimeout(temp);
//                            logConsole(connection.getName() + " is still alive");
                        }
                    } else {
                        if (incomingMessage.getPayload() == State.LEADER) {
                            // Reset timer for reelection
                            if (leaderTimeout != null) {
                                leaderTimeout.cancel();
                                leaderTimeout.purge();
                            }

                            leaderTimeout = new Timer();
                            leaderTimeout.schedule(new NodeTimeoutTask(connection) {
                                @Override
                                public void run() {
                                    logConsole("The leader has died!");
                                    handleNodeTimeout(this.node);
                                }
                            }, 2000);

                            // Send heartbeat back
                            outgoingMessages.put(connection, createMessage(
                                    incomingMessage.getSender(),
                                    MessageType.RAFT_HEARTBEAT,
                                    state
                            ));

//                            logConsole("Leader is still alive!");
                        }
                    }
                    break;
                case STATE:
                    logConsole("State of connection " + connection.getName() + " changed to " + incomingMessage.getPayload());

                    // Grab the new state
                    State incomingState = (State) incomingMessage.getPayload();
                    connection.setState(incomingState);

                    // Reset election stuff
                    if (incomingState == State.LEADER) {
                        state = State.FOLLOWER;
                        voteCount = 0;
                        hasVoted = false;
                    }
                    break;
                case DISCONNECT:
                    logConsole("Node with key " + incomingMessage.getPayload() + " disconnected!");
                    connections.remove((String) incomingMessage.getPayload());
                    break;
                default:
                    logConsole("Message fits no type " + incomingMessage);
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

                    // Get the state of the new node
                    Message state = tempHandler.read();
                    if (state.getMessageType() == MessageType.STATE) {
                        // Save the connection
                        connections.put(
                                createConnectionKey(address, port),
                                new Connection(address, port, connectionName, clientSocket, (State) state.getPayload())
                        );

                        // Go through every given combination of IP:Port by splitting at the comma
                        for (String connectionInformation : ((String) welcome.getPayload()).split(",")) {
                            if (connectionInformation.length() > 0 && !connections.containsKey(connectionInformation)) {
//                            logConsole("new connection information: " + connectionInformation);
                                String[] connection = connectionInformation.split(":");
                                String[] ipParts = connection[0].split("\\.");

                                // Connect to the new node
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

    /**
     * This method creates a Message object for you with the given params
     *
     * @param receiver the name of the receiver of the message
     * @param type     the type of the message
     * @param payload  the payload of the message
     * @return the Message object
     */
    protected Message createMessage(String receiver, MessageType type, Object payload) {
        Message msg = new Message();
        msg.setSender(name);
        msg.setReceiver(receiver);
        msg.setMessageType(type);
        msg.setPayload(payload);

        return msg;
    }

    protected void addBroadcastMessage(MessageType messageType, Object payload) {
        broadcastMessages.add(createMessage(
                "",
                messageType,
                payload
        ));
    }

    public void stopNode() {
        nodeRunning = false;

        if (leaderTimeout != null) {
            leaderTimeout.cancel();
            leaderTimeout.purge();
        }
    }

    private void handleNodeTimeout(Connection connection) {
        // Broadcast the timeout if this node is the leader
        String connectionKey = createConnectionKey(connection.getAddress(), connection.getPort());
        if (state == State.LEADER) {
            addBroadcastMessage(MessageType.DISCONNECT, connectionKey);
        }

        connections.remove(connectionKey);
    }
}
