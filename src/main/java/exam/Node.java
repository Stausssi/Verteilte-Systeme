package exam;

import org.apache.commons.cli.*;
import tasks.io.InputOutput;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static exam.Utility.*;

/**
 * A Node represents a working element of the decryption task.
 * It consists of a SocketServer, which will allow incoming connections, and a CommunicationHandler, which will parse
 * incoming messages and respond / send messages.
 * One Node in the system is the Leader, which will distribute the tasks to the other nodes (so-called workers)
 */
public class Node implements Runnable {
    private final Logger logger;
    private static final int MAX_INCOMING_CLIENTS = 100;

    private boolean nodeRunning = true;
    private boolean distributeWork = false;

    // Connection information of the node
    private final InetAddress address;
    private final int port;
    protected final String name;
    protected State state;

    // Connections to the leader and client
    private Connection leaderConnection;
    private Connection clientConnection;

    // Vars for primes
    private static final String primesFile = "/primes10000.txt";
    public volatile ConcurrentHashMap<Integer, Index> primeMap = new ConcurrentHashMap<>();
    private final ArrayList<String> primeList = new ArrayList<>();
    private static final int workSize = 250;

    // Concurrent data storage for connections and messages
    public volatile ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    public volatile ConcurrentHashMap<Connection, ConcurrentLinkedQueue<Message>> outgoingMessages = new ConcurrentHashMap<>();
    public volatile ConcurrentLinkedQueue<Message> broadcastMessages = new ConcurrentLinkedQueue<>();

    // Raft stuff
    protected boolean hasVoted = false;
    private int votesReceived = 0;
    private int voteCount = 0;
    private Timer leaderTimeout;

    // RSA Stuff
    private String publicKey = "";
    private PrimeWorker primeWorker;

    // Create threads for socket server and client
    protected final SocketServer socketServer = new SocketServer();
    private final CommunicationHandler communicationHandler = new CommunicationHandler();
    private final Raft raft = new Raft(this);

    /**
     * Creates a new Node which will run on localhost by default.
     *
     * @param port the port the socket server will run on
     * @param name the name of the node
     * @throws UnknownHostException Never.
     */
    public Node(int port, String name) throws UnknownHostException {
        logger = initializeLogger(name);

        this.address = InetAddress.getByName("localhost");
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
        try {
            fillPrimesMap();
        } catch (Exception e) {
            logError("filling primes map", e, true);
        }
    }


    /**
     * Creates a new Node running on a given adress and port.
     *
     * @param address the address of the socket server
     * @param port    the port the socket server will run on
     * @param name    the name of the node
     */
    public Node(InetAddress address, int port, String name) {
        logger = initializeLogger(name);

        this.address = address;
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
        try {
            fillPrimesMap();
        } catch (Exception e) {
            logError("filling primes map", e, true);
        }
    }

    @Override
    public void run() {
        // Create the threads for server, communicator and Raft protocol
        Thread serverThread = new Thread(socketServer);
        Thread communicatorThread = new Thread(communicationHandler);
        Thread raftThread = new Thread(raft);

        // Start the threads
        serverThread.start();
        communicatorThread.start();
        raftThread.start();

        try {
            serverThread.join();
            communicatorThread.join();
            raftThread.join();
        } catch (InterruptedException e) {
            logError("waiting for threads to finish", e, false);
        }
    }


    /**
     * This class will accept incoming connections and save them to a Connection class, which will be later used for
     * communication.
     */
    private class SocketServer implements Runnable {
        // The socket instance
        private ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                try {
                    // Open the ServerSocket
                    this.serverSocket = new ServerSocket(port, MAX_INCOMING_CLIENTS, address);
                } catch (IOException e) {
                    logError("starting the SocketServer", e, true);
                }

                logger.info("Started server socket on: " + serverSocket);

                while (nodeRunning) {
                    // Accept a new connection
                    Socket newConnection = serverSocket.accept();
                    ObjectMessageHandler tempHandler = new ObjectMessageHandler(newConnection, name);

                    // Read the first message the new connection sent
                    Message firstMessage = tempHandler.read();

                    // If it's a "hello" message, the new connection is a Node
                    if (firstMessage.getMessageType() == MessageType.HELLO) {
                        logger.fine("Hello message received!");

                        String connectionName = firstMessage.getSender();
                        int port = (Integer) firstMessage.getPayload();

                        if (connectionName != null && port > 0) {
                            // Send the node a serialized version of IP:Port combinations in the connections object
                            tempHandler.write(createWelcomeMessage(connectionName));

                            Connection nodeConnection = new Connection(
                                    newConnection.getInetAddress(), port, connectionName, newConnection, tempHandler
                            );

                            // Add newConnection to the HashMap
                            connections.put(createConnectionKey(newConnection.getInetAddress(), port), nodeConnection);

                            // Inform the new node of the current state of this node
                            addOutgoingMessage(nodeConnection, createMessage(connectionName, MessageType.STATE, state));

                            // Send the new node the public key, if it exists -> Rejoin
                            if (state == State.LEADER && publicKey.length() > 0) {
                                addOutgoingMessage(nodeConnection, createMessage(connectionName, MessageType.RSA, publicKey));
                            }

                            logger.fine("New Node was successfully added!");
                            logger.fine(nodeConnection.toString());
                        }
                    }
                    // Otherwise, the new connection might be a client
                    else if (firstMessage.getMessageType() == MessageType.RSA && "Client".equalsIgnoreCase(firstMessage.getSender())) {
                        if (publicKey.length() > 0) {
                            logger.warning("Client reconnected to me!");
                        } else {
                            logger.info("Received RSA information from the client!");
                        }

                        // Send the client a serialized version of IP:Port combinations in the connections object
                        tempHandler.write(createWelcomeMessage("Client"));

                        // Save the client connection
                        clientConnection = new Connection(
                                newConnection.getInetAddress(), -1, "Client", newConnection, tempHandler
                        );

                        if (publicKey.length() == 0) {
                            // Forward the RSA information to the leader
                            if (leaderConnection != null) {
                                logger.info("Forwarded RSA information to the leader");
                                addOutgoingMessage(leaderConnection, firstMessage);
                            } else {
                                // Parse the message from the client directly
                                communicationHandler.parseMessage(firstMessage, clientConnection);
                            }
                        }
                    }
                    // Otherwise, close the connection
                    else {
                        logger.warning("This connections is neither a node nor the client! " + newConnection);
                        newConnection.close();
                    }
                }
            } catch (IOException e) {
                if (nodeRunning) {
                    logError("handling new connection in the SocketServer", e, false);
                }
            }
            logger.info("SocketServer shutdown!");
        }

        /**
         * Creates a welcome message for a connection with the given name.
         * It contains every node this node is connected to.
         *
         * @param connectionName The name of the connection
         * @return The generated Message
         */
        private Message createWelcomeMessage(String connectionName) {
            // Add each connection key IP:Port to a string, separated by ,
            StringBuilder connectionsBuilder = new StringBuilder();
            for (String key : Collections.list(connections.keys())) {
                if (key.contains(":")) {
                    connectionsBuilder.append(key);
                    connectionsBuilder.append(",");
                }
            }

            // Remove the trailing comma
            if (connectionsBuilder.length() > 0) {
                connectionsBuilder.deleteCharAt(connectionsBuilder.lastIndexOf(","));
            }

            return createMessage(connectionName, MessageType.WELCOME, connectionsBuilder.toString());
        }
    }

    /**
     * This class is used to handle the communication between the Nodes.
     * It reads incoming messages and sends the corresponding responses.
     */
    private class CommunicationHandler implements Runnable {
        @Override
        public void run() {
            logger.info("The CommunicationHandler was started");

            while (nodeRunning || !broadcastMessages.isEmpty()) {
                // Go through every broadcast message
                for (Message broadcast : broadcastMessages) {
                    for (Connection c : connections.values()) {
                        addOutgoingMessage(c, broadcast);
                    }

                    logger.finer("Broadcasted " + broadcast);

                    // Remove the head. Should always be the element which was sent
                    broadcastMessages.remove();
                }

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
                        logError("checking for available messages", e, false);
                    }

                    // Check whether the node is working
                    if (state == State.LEADER && !c.isWorking() && !c.shouldBeWorking() && distributeWork) {
                        distributeWork(c);
                    }

                    // Send the messages which are only directed at the connection
                    try {
                        if (outgoingMessages.containsKey(c)) {
                            for (Message msg : outgoingMessages.get(c)) {
                                messageHandler.write(msg);
                                outgoingMessages.get(c).remove();
                            }
                        }
                    } catch (IOException e) {
                        logError("sending message to connection", e, false);

                        // Close this connection
                        handleNodeTimeout(c);
                    }
                }
            }
            logger.info("CommunicationHandler shutdown!");
        }

        /**
         * Parsed the given message by using the MessageType and acts accordingly.
         *
         * @param incomingMessage the message to parse and handle
         * @param connection      the connection the message came from
         */
        private void parseMessage(Message incomingMessage, Connection connection) {
            if (incomingMessage != null) {

                switch (incomingMessage.getMessageType()) {
                    case RSA:
                        logger.info("PublicKey received: " + incomingMessage.getPayload());

                        // Save the public key
                        publicKey = (String) incomingMessage.getPayload();

                        if (state == State.LEADER) {
                            // Broadcast the public key
                            broadcastMessages.add(incomingMessage);

                            clientConnection = connection;

                            // Broadcast the client connection
                            String keyOfClientConnection = "";
                            for (Map.Entry<String, Connection> entry : connections.entrySet()) {
                                if (entry.getValue() == connection) {
                                    keyOfClientConnection = entry.getKey();
                                }
                            }

                            if (!"".equalsIgnoreCase(keyOfClientConnection)) {
                                addBroadcastMessage(MessageType.CLIENT_CONNECTION, keyOfClientConnection);
                            }

                            // Distribute the work packages after a second to ensure that the public key has arrived
                            Timer distributeTimer = new Timer();
                            distributeTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    distributeWork = true;
                                }
                            }, 1000);
                        }
                        break;
                    case PRIMES:
                        if (state == State.LEADER) {
                            distributeWork = false;
                            logger.info("Forwarding primes to the client connection!");

                            // Forward the primes to the Node connected to the Client, which will forward the received
                            // primes to the client
                            Message primeMessage = createMessage("Client", MessageType.PRIMES, incomingMessage.getPayload());

                            // Check whether the client is connected to the leader
                            if (clientConnection.getPort() == -1) {
                                try {
                                    clientConnection.getMessageHandler().write(primeMessage);
                                } catch (IOException e) {
                                    logError("Sending primes to client. Assuming he is gone...", e, false);
                                }
                            } else {
                                addOutgoingMessage(clientConnection, primeMessage);
                            }

                            // Let everyone know that we are finished
                            addBroadcastMessage(MessageType.FINISHED, "");

                            // Also stop this node
                            stopNode();
                        } else if (clientConnection != null) {
                            logger.info("Received Primes. Sending them to the Client now!");
                            incomingMessage.setSender(name);

                            // Send the primes to the client
                            try {
                                clientConnection.getMessageHandler().write(incomingMessage);
                            } catch (IOException e) {
                                logError("sending primes to the client!", e, false);
                            }
                        }
                        break;
                    case RAFT_ELECTION:
                        logger.info("Raft Election started by " + incomingMessage.getSender());

                        // Reply to the candidate with whether we already voted.
                        // Already voted -> false
                        // Not voted -> true
                        addOutgoingMessage(connection, createMessage(incomingMessage.getSender(), MessageType.RAFT_VOTE, !hasVoted));

                        // The connection is a leader candidate
                        connection.setState(State.CANDIDATE);

                        if (!hasVoted) {
                            hasVoted = true;
                        }
                        break;
                    case RAFT_VOTE:
                        if (state == State.CANDIDATE) {
                            // Add one to the vote count if the node elected this node
                            voteCount = (boolean) incomingMessage.getPayload() ? voteCount + 1 : voteCount;
                            votesReceived++;

                            // Check whether this node has enough votes
                            if (voteCount > connections.size() / 2) {
                                logger.info("Im the boss in town");

                                state = State.LEADER;
                                leaderConnection = null;

                                // Distribute the work packages if a public key exists
                                distributeWork = publicKey.length() > 0;

                                // Start the heartbeat task
                                raft.initLeaderHeartbeat();
                            } else if (votesReceived == connections.size()) {
                                logger.info("I lost the election");

                                // Reset own state and values
                                resetRaftElection();
                            } else {
                                break;
                            }

                            // Inform everyone of the new state
                            addBroadcastMessage(MessageType.STATE, state);
                        }
                        break;
                    case RAFT_HEARTBEAT:
                        logger.fine("Heartbeat received by " + incomingMessage.getSender());
                        if (state == State.LEADER) {
                            if (incomingMessage.getPayload() == State.FOLLOWER) {
                                // Reset timer for node disconnection
                                connection.setNodeTimeout(restartTimer(connection.getNodeTimeout(), new NodeTimeoutTask(connection) {
                                    @Override
                                    public void run() {
                                        if (nodeRunning) {
                                            handleNodeTimeout(this.node);
                                        }
                                    }
                                }, 2000));
                            }
                        } else {
                            if (incomingMessage.getPayload() == State.LEADER) {
                                // Reset timer for reelection
                                leaderTimeout = restartTimer(leaderTimeout, new NodeTimeoutTask(connection) {
                                    @Override
                                    public void run() {
                                        if (nodeRunning) {
                                            handleNodeTimeout(this.node);
                                        }
                                    }
                                }, 2000);

                                // Send heartbeat back
                                addOutgoingMessage(connection, createMessage(incomingMessage.getSender(), MessageType.RAFT_HEARTBEAT, state));
                            }
                        }
                        break;
                    case STATE:
                        logger.info("State of " + connection.getName() + " changed to " + incomingMessage.getPayload());

                        // Grab the new state
                        State incomingState = (State) incomingMessage.getPayload();
                        connection.setState(incomingState);

                        if (incomingState == State.LEADER) {
                            // Save leader connection to variable
                            leaderConnection = connection;

                            // Reset election stuff
                            resetRaftElection();
                        }
                        break;
                    case WORK:
                        logger.info("Received work package: " + incomingMessage.getPayload());

                        // Tell the leader this Node will work on the range, if there is no other task running
                        if (primeWorker == null && publicKey.length() > 0) {
                            addOutgoingMessage(leaderConnection, createMessage(leaderConnection.getName(), MessageType.WORK_STATE, incomingMessage.getPayload() + ":" + Index.WORKING));

                            primeWorker = new PrimeWorker((String) incomingMessage.getPayload(), publicKey, primeList, new WorkerCallback() {
                                @Override
                                public void resultFound(String p, String q) {
                                    logger.info("Worker found the result!");

                                    // Notify the leader of the result
                                    // Check for null in case the leader disconnected
                                    if (leaderConnection != null) {
                                        addOutgoingMessage(leaderConnection, createMessage(leaderConnection.getName(), MessageType.PRIMES, p + "," + q));
                                    }
                                }

                                @Override
                                public void workerFinished(String range) {
                                    logger.fine("Worker is finished!");
                                    primeWorker = null;

                                    // Tell the leader this range is finished
                                    if (leaderConnection != null) {
                                        addOutgoingMessage(leaderConnection, createMessage(leaderConnection.getName(), MessageType.WORK_STATE, range + ":" + Index.CLOSED));
                                    }
                                }
                            });

                            new Thread(primeWorker).start();
                        }
                        break;
                    case WORK_STATE:
                        // Get the range and new State
                        String[] information = ((String) incomingMessage.getPayload()).split(":");
                        int[] range = Arrays.stream(information[0].trim().split(",")).mapToInt(Integer::parseInt).toArray();
                        Index newState = Index.valueOf(information[1]);

                        logger.fine("Prime range [" + information[0] + "] changed to " + newState);

                        // Change the states of the indexes
                        for (int i = range[0]; i <= range[1]; ++i) {
                            primeMap.put(i, newState);
                        }

                        if (state == State.LEADER) {
                            stopTimer(connection.getWorkTimeout());

                            if (newState == Index.CLOSED) {
                                logger.fine("Primes in range " + Arrays.toString(range) + " are done!");
                            }

                            // Inform every node of the state change
                            addBroadcastMessage(MessageType.WORK_STATE, incomingMessage.getPayload());

                            connection.setIsWorking(newState == Index.WORKING);
                            connection.setShouldBeWorking(newState == Index.WORKING);
                        }
                        break;
                    case CLIENT_CONNECTION:
                        if (clientConnection == null) {
                            clientConnection = connections.get((String) incomingMessage.getPayload());
                            logger.info("Client is connected to " + clientConnection.getName());
                        }
                        break;
                    case DISCONNECT:
                        logger.info("Node with key " + incomingMessage.getPayload() + " disconnected!");

                        handleNodeTimeout(connections.get((String) incomingMessage.getPayload()));
                        break;
                    case FINISHED:
                        stopNode();
                        break;
                    default:
                        logger.warning("Message fits no type " + incomingMessage);
                        break;
                }
            } else {
                logger.warning("Incoming Message was null!");
            }
        }
    }


    /**
     * Distributes a working range to the given connection.
     *
     * @param connection the connection object to distribute work to
     */
    public void distributeWork(Connection connection) {
        int index = 0;
        while (primeMap.get(index) != Index.OPEN && primeMap.get(index) != null) ++index;

        if (primeMap.get(index) != null) {
            if (index > 0) --index;

            // Update the working packages
            int actualWorkSize = workSize;
            if (index + workSize > primeMap.size()) {
                actualWorkSize = primeMap.size() - index;
                distributeWork = false;
            }

            for (int i = index; i <= index + actualWorkSize; ++i) {
                primeMap.put(i, Index.TENTATIVE);
            }

            int finalIndex = index;
            int finalActualWorkSize = actualWorkSize;

            // The node has 5 seconds to confirm they're working on it
            Timer workTimeout = new Timer();
            TimerTask workTimeoutTask = new NodeTimeoutTask(connection) {
                @Override
                public void run() {
                    logger.warning(this.node.getName() + " failed to response! Therefore, the working range is reset");
                    this.node.setShouldBeWorking(false);

                    for (int i = finalIndex; i <= finalIndex + finalActualWorkSize; ++i) {
                        if (primeMap.get(i) == Index.TENTATIVE) {
                            primeMap.put(i, Index.OPEN);
                        }
                    }
                }
            };
            workTimeout.schedule(workTimeoutTask, 5000);
            connection.setWorkTimeout(workTimeout);

            // Convert the range to a string
            String stringRange = index + "," + (index + actualWorkSize - 1);

            // Force the Node to work
            addOutgoingMessage(connection, createMessage(connection.getName(), MessageType.WORK, stringRange));

            connection.setShouldBeWorking(true);
            logger.info("Distributed work [" + stringRange + "] to " + connection.getName());
        } else {
            logger.info("Every package distributed!");
            distributeWork = false;
        }
    }

    /**
     * Creates a new socket and connects to the given address and port.
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
                ObjectMessageHandler tempHandler = new ObjectMessageHandler(clientSocket, name);

                logger.fine("Connected to: " + clientSocket);

                // Send a hello message
                Message hello = new Message();
                hello.setMessageType(MessageType.HELLO);
                hello.setSender(name);
                hello.setPayload(this.port);

                tempHandler.write(hello);

                // Read the welcome message
                Message welcome = tempHandler.read();
                if (welcome.getMessageType() == MessageType.WELCOME) {
                    // Save the connection
                    connections.put(
                            createConnectionKey(address, port),
                            new Connection(address, port, welcome.getSender(), clientSocket, tempHandler)
                    );

                    // Go through every given combination of IP:Port by splitting at the comma
                    for (String connectionInformation : ((String) welcome.getPayload()).split(",")) {
                        if (connectionInformation.length() > 0 && !connections.containsKey(connectionInformation)) {
                            logger.fine("Received the following connections: " + connectionInformation);
                            String[] connection = connectionInformation.split(":");
                            String[] ipParts = connection[0].split("\\.");

                            // Connect to the new node
                            connectTo(
                                    // Create the InetAddress object
                                    InetAddress.getByAddress(new byte[]{
                                            (byte) Integer.parseInt(ipParts[0]),
                                            (byte) Integer.parseInt(ipParts[1]),
                                            (byte) Integer.parseInt(ipParts[2]),
                                            (byte) Integer.parseInt(ipParts[3])
                                    }),
                                    // Parse the port
                                    Integer.parseInt(connection[1]));
                        }
                    }
                }
            } catch (IOException e) {
                logError("connecting to a new connection", e, true);
            }
        }

    }

    /**
     * Connects to a socket with a given host name. It converts the host name and calls connectTo with an
     * InetAddress.
     *
     * @param host The hostname to convert to an address.
     * @param port The port of the socket connection.
     */
    public void connectTo(String host, int port) {
        try {
            connectTo(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            logError("parsing the host name", e, true);
        }
    }

    /**
     * Logs an error to the console and stops the node if the error is critical.
     *
     * @param errorOccurrence A String containing information where the error occurred
     * @param e               The exception that was thrown
     * @param critical        Whether this error is critical to the Nodes' functionality
     */
    public void logError(String errorOccurrence, Exception e, boolean critical) {
        logger.log(
                critical ? Level.SEVERE : Level.WARNING,
                "Encountered an error while " + errorOccurrence + ": " + e.toString()
        );

        if (critical) {
            stopNode();
        }
    }

    /**
     * Creates a connection key consisting of IP and port
     *
     * @param address The address of the connection
     * @param port    The port of the connection
     * @return "address:port"
     */
    public String createConnectionKey(InetAddress address, int port) {
        return removeHostFromAddress(address) + ":" + port;
    }

    /**
     * Removes the host name from the string representation of an InetAddress.
     *
     * @param address The address
     * @return A String containing the IP-Address of the InetAddress.
     */
    private String removeHostFromAddress(InetAddress address) {
        return address.toString().split("/")[1];
    }

    /**
     * Prints out the connections of this node.
     */
    public void logConnections() {
        logger.info("Connected to: " + connections);
    }

    /**
     * Creates a broadcast method with the given type and payload.
     *
     * @param messageType the type of the broadcast message
     * @param payload     the payload of the broadcast
     */
    protected synchronized void addBroadcastMessage(MessageType messageType, Object payload) {
        broadcastMessages.add(createMessage("", messageType, payload));
    }

    /**
     * Adds a given message object to the list of outgoing messages for the given connection and therefore schedules it
     * for sending.
     *
     * @param receiver the connection to send the message to
     * @param message  the message to send
     */
    protected synchronized void addOutgoingMessage(Connection receiver, Message message) {
        ConcurrentLinkedQueue<Message> messages = outgoingMessages.get(receiver);
        if (messages == null) {
            messages = new ConcurrentLinkedQueue<>();

            outgoingMessages.put(receiver, messages);
        }

        outgoingMessages.get(receiver).add(message);
    }

    /**
     * Handles the timeout of a Node by removing it from the connections map and broadcasting the disconnect if this Node
     * is the leader.
     *
     * @param connection the connection of the disconnected Node.
     */
    private void handleNodeTimeout(Connection connection) {
        // Broadcast the timeout if this node is the leader
        String connectionKey = createConnectionKey(connection.getAddress(), connection.getPort());
        connection = connections.get(connectionKey);

        if (connection != null) {
            logger.info(connection.getName() + " (" + connection.getState() + ") disconnected!");
            if (state == State.LEADER) {
                addBroadcastMessage(MessageType.DISCONNECT, connectionKey);
            }

            // Remove the connection
            connections.remove(connectionKey);

            // Remove every outgoing message to the dead connection
            outgoingMessages.remove(connection);

            // Abort, if there is no one other than me
            if (connections.isEmpty()) {
                logger.severe("Im alone here");
                stopNode();
            }
        }
    }

    /**
     * Resets the variables needed for the raft election.
     */
    private void resetRaftElection() {
        state = State.FOLLOWER;
        voteCount = 0;
        votesReceived = 0;
        hasVoted = false;
    }

    /**
     * Stops the execution of the SocketServer, CommunicationHandler and Raft protocol.
     */
    public void stopNode() {
        if (nodeRunning) {
            logger.info("Stopping this node!");

            nodeRunning = false;

            // Stop the leader timeout
            stopTimer(leaderTimeout);

            // Stop the raft tasks
            raft.stop();

            // TODO: Stop the prime worker

            if (state == State.LEADER) {
                // Stop the disconnection timeouts
                for (Connection c : connections.values()) {
                    stopTimer(c.getNodeTimeout());
                }
            }

            try {
                socketServer.serverSocket.close();
            } catch (IOException e) {
                logError("closing the ServerSocket", e, false);
            }
        }
    }


    private void fillPrimesMap() throws URISyntaxException, FileNotFoundException {
        URL defaultImage = Node.class.getResource(primesFile);
        assert defaultImage != null;
        File imageFile = new File(defaultImage.toURI());
        String primes = InputOutput.readFile(imageFile);
        String[] primesList = primes.split(String.valueOf('\n'));
        for (int i = 0; i < primesList.length; i++) {
            primeList.add(primesList[i]);
            primeMap.put(i, Index.OPEN);
        }
    }

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        // CLI options to create Node
        Options options = new Options();
        Option port = new Option("p", "port", true, "Node port");
        port.setRequired(true);
        options.addOption(port);
        Option name = new Option("n", "name", true, "Node name");
        name.setRequired(true);
        options.addOption(name);
        Option address = new Option("i", "address", true, "Node IP-address");
        address.setRequired(true);
        options.addOption(address);

        // CLI options for cluster connection
        Option hostPort = new Option("hp", "host_port", true, "Port of Node to connect to");
        hostPort.setRequired(false);
        options.addOption(hostPort);
        Option hostAddress = new Option("ha", "host_address", true, "Address of Node to connect to");
        hostAddress.setRequired(false);
        options.addOption(hostAddress);

        // Command parsing
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cl = null;

        try {
            cl = parser.parse(options, args);

        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Invalid argument list", options);
            System.exit(0);
        }

        // Get parsed strings for node
        String nodePort = cl.getOptionValue("port");
        String nodeName = cl.getOptionValue("name");
        String nodeAddress = cl.getOptionValue("address");

        // Get parsed strings for cluster connection
        String host_Port = cl.getOptionValue("host_port");
        String host_Address = cl.getOptionValue("host_address");

        System.out.println("Node: " + nodePort + " " + nodeName + " " + nodeAddress);
        System.out.println("Cluster: " + host_Port + " " + host_Address);

        Node clusterNode = new Node(Integer.parseInt(nodePort), nodeName);
        Thread nodeThread = new Thread(clusterNode);
        nodeThread.start();
        Thread.sleep(1000);

        //If

        if (null != host_Port && null != host_Address) {
            clusterNode.connectTo(host_Address, Integer.parseInt(host_Port));
        }

        try {
            nodeThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
