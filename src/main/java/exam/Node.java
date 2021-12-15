package exam;

import org.apache.commons.cli.*;
import tasks.io.InputOutput;

import java.io.File;
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
    private static final int MAX_INCOMING_CLIENTS = 100;

    private final Logger logger;

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
    private String primesFile;
    public volatile ConcurrentHashMap<Integer, PrimeState> primeMap = new ConcurrentHashMap<>();
    private final ArrayList<String> primeList = new ArrayList<>();
    private int workSize;

    // Callbacks for the prime worker. These are different for LEADER and FOLLOWER
    private static final HashMap<State, WorkerCallback> callbackMap = new HashMap<>();

    {
        callbackMap.put(State.FOLLOWER, new WorkerCallback() {
            @Override
            public void resultFound(String p, String q) {
                // Notify the leader of the result
                // Check for null in case the leader disconnected
                if (leaderConnection != null) {
                    addOutgoingMessage(leaderConnection, createMessage(leaderConnection.getName(), MessageType.PRIMES, p + "," + q));
                }
            }

            @Override
            public void workerFinished(String range) {
                // Tell the leader this range is finished
                if (leaderConnection != null) {
                    addOutgoingMessage(leaderConnection, createMessage(leaderConnection.getName(), MessageType.WORK_STATE, range + ":" + PrimeState.CLOSED));
                }
            }

            @Override
            public void onError(Exception e) {
                logError("working on prime range", e, true);
            }
        });

        // A Candidate has the same callbacks
        callbackMap.put(State.CANDIDATE, callbackMap.get(State.FOLLOWER));

        callbackMap.put(State.LEADER, new WorkerCallback() {
            @Override
            public void resultFound(String p, String q) {
                // Create a dummy message to handle
                communicationHandler.handlePrimeMessage(createMessage(
                        clientConnection.getName(),
                        MessageType.PRIMES,
                        p + "," + q
                ));
            }

            @Override
            public void workerFinished(String range) {
                // Set the state of the range to closed
                communicationHandler.handleWorkStateMessage(createMessage(
                        name,
                        MessageType.WORK_STATE,
                        range + ":" + PrimeState.CLOSED
                ), null);

                // Distribute new work to myself
                distributeWorkToMyself();
            }

            @Override
            public void onError(Exception e) {
                logError("working on prime range", e, true);
            }
        });
    }

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
        fillPrimesMap();
    }


    /**
     * Creates a new Node running on a given address and port.
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
        fillPrimesMap();
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
            // Wait for them to finish
            serverThread.join();
            communicatorThread.join();
            raftThread.join();
            logger.info("Node shutdown complete!");
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

                            Connection nodeConnection = new Connection(newConnection.getInetAddress(), port, connectionName, newConnection, tempHandler);

                            // Add newConnection to the HashMap
                            connections.put(createConnectionKey(newConnection.getInetAddress(), port), nodeConnection);

                            // Inform the new node of the current state of this node
                            addOutgoingMessage(nodeConnection, createMessage(connectionName, MessageType.STATE, state));

                            // Send the new node the public key and client connection, if it exists -> Rejoin
                            if (state == State.LEADER && publicKey.length() > 0) {
                                addOutgoingMessage(nodeConnection, createMessage(connectionName, MessageType.RSA, publicKey));
                                addOutgoingMessage(nodeConnection, createMessage(
                                        connectionName,
                                        MessageType.CLIENT_CONNECTION,
                                        createConnectionKey(getClientConnectionAddress(), getClientConnectionPort())
                                ));
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
                        clientConnection = new Connection(newConnection.getInetAddress(), -1, "Client", newConnection, tempHandler);

                        // Broadcast the client connection
                        addBroadcastMessage(MessageType.CLIENT_CONNECTION, createConnectionKey(address, port));

                        if (publicKey.length() == 0) {
                            // Forward the RSA information to the leader
                            if (leaderConnection != null) {
                                logger.info("Forwarding RSA information to the leader");
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
            switch (incomingMessage.getMessageType()) {
                case RSA:
                    handleRSAMessage(incomingMessage);
                    break;
                case PRIMES:
                    handlePrimeMessage(incomingMessage);
                    break;
                case RAFT_ELECTION:
                    handleRaftElectionMessage(incomingMessage, connection);
                    break;
                case RAFT_VOTE:
                    handleRaftVoteMessage(incomingMessage);
                    break;
                case RAFT_HEARTBEAT:
                    handleRaftHeartbeatMessage(incomingMessage, connection);
                    break;
                case STATE:
                    handleStateMessage(incomingMessage, connection);
                    break;
                case WORK:
                    handleWorkMessage(incomingMessage);
                    break;
                case WORK_STATE:
                    handleWorkStateMessage(incomingMessage, connection);
                    break;
                case CLIENT_CONNECTION:
                    handleClientConnectionMessage(incomingMessage);
                    break;
                case DISCONNECT:
                    handleDisconnectMessage(incomingMessage);
                    break;
                case FINISHED:
                    stopNode();
                    break;
                default:
                    logger.warning("Message fits no type " + incomingMessage);
                    break;
            }
        }

        /*
         * Every one of the following methods is following the same scheme:
         * For every valid MessageType, a method is created which handles the incoming message with the corresponding type.
         * Some Methods are given the connection object as well, since they directly respond to the message or need access
         * to variables of the Connection.
         */

        private void handleRSAMessage(Message incomingMessage) {
            logger.info("PublicKey received: " + incomingMessage.getPayload());

            // Save the public key
            publicKey = (String) incomingMessage.getPayload();

            // Create the prime worker
            // Use the callbacks of the role
            primeWorker = new PrimeWorker(publicKey, primeList, callbackMap.get(state), logger);

            if (state == State.LEADER) {
                // Broadcast the public key
                broadcastMessages.add(incomingMessage);

                // Distribute the work packages after a second to ensure that the public key has arrived
                Timer distributeTimer = new Timer();
                distributeTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        distributeWork = true;
                    }
                }, 1000);

                // Start Working myself
                distributeWorkToMyself();
            }
        }

        private void handleDisconnectMessage(Message incomingMessage) {
            logger.info("Node with key " + incomingMessage.getPayload() + " disconnected!");

            handleNodeTimeout(connections.get((String) incomingMessage.getPayload()));
        }

        private void handleClientConnectionMessage(Message incomingMessage) {
            // Only update the client connection if there is none yet or the client is connected to a different node
            if (!isClientConnectedToMe()) {
                clientConnection = connections.get((String) incomingMessage.getPayload());

                logger.info("Client is connected to " + clientConnection.getName());
            }
        }

        private void handleWorkStateMessage(Message incomingMessage, Connection connection) {
            // Get the range and new State
            String[] information = ((String) incomingMessage.getPayload()).split(":");
            int[] range = createRangeFromString(information[0]);
            PrimeState newState = PrimeState.valueOf(information[1]);

            // Update the state of the primes
            updatePrimeState(range, newState, connection);
        }

        private void handleWorkMessage(Message incomingMessage) {
            logger.info("Received work package: " + incomingMessage.getPayload());

            // Tell the leader this Node will work on the range, if there is no other task running
            if (leaderConnection != null && primeWorker != null) {
                String stringRange = (String) incomingMessage.getPayload();
                int[] intRange = createRangeFromString(stringRange);

                // Check whether this range is already closed
                boolean isClosed = true;
                for (int i = intRange[0]; i < intRange[1]; ++i) {
                    isClosed = isClosed && primeMap.get(i) == PrimeState.CLOSED;
                }

                if (!isClosed) {
                    if (primeWorker.startWorking(stringRange)) {
                        addOutgoingMessage(leaderConnection, createMessage(leaderConnection.getName(), MessageType.WORK_STATE, createPrimeStateString(intRange, PrimeState.WORKING)));
                    }
                } else {
                    addOutgoingMessage(leaderConnection, createMessage(leaderConnection.getName(), MessageType.WORK_STATE, createPrimeStateString(intRange, PrimeState.CLOSED)));
                }
            }
        }

        private void handleStateMessage(Message incomingMessage, Connection connection) {
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
        }

        private void handleRaftHeartbeatMessage(Message incomingMessage, Connection connection) {
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
                    }, Raft.timeoutTolerance));
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
                    }, Raft.timeoutTolerance);

                    // Send heartbeat back
                    addOutgoingMessage(connection, createMessage(incomingMessage.getSender(), MessageType.RAFT_HEARTBEAT, state));
                }
            }
        }

        private void handleRaftVoteMessage(Message incomingMessage) {
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

                    // Update the callbacks of the prime worker and start working
                    if (primeWorker != null) {
                        primeWorker.updateCallbacks(callbackMap.get(state));
                        distributeWorkToMyself();
                    }
                } else if (votesReceived == connections.size()) {
                    logger.info("I lost the election");

                    // Reset own state and values
                    resetRaftElection();
                } else {
                    return;
                }

                // Inform everyone of the new state
                addBroadcastMessage(MessageType.STATE, state);
            }
        }

        private void handleRaftElectionMessage(Message incomingMessage, Connection connection) {
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
        }

        private void handlePrimeMessage(Message incomingMessage) {
            if (state == State.LEADER) {
                distributeWork = false;
                logger.info("Forwarding primes to the client connection!");

                // Forward the primes to the Node connected to the Client, which will forward the received
                // primes to the client
                Message primeMessage = createMessage("Client", MessageType.PRIMES, incomingMessage.getPayload());

                // Check whether the client is connected to the leader
                if (isClientConnectedToMe()) {
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
            } else if (isClientConnectedToMe()) {
                logger.info("Received Primes. Sending them to the Client now!");
                incomingMessage.setSender(name);

                // Send the primes to the client
                try {
                    clientConnection.getMessageHandler().write(incomingMessage);
                } catch (IOException e) {
                    logError("sending primes to the client!", e, false);
                }
            }
        }
    }

    // -------------------- [Primes related] -------------------- //

    /**
     * Iterates over the prime map and returns the next range, which is yet to be worked on.
     *
     * @return a range [lower, upper] which is next in line. [-1], if all indexes are either currently worked on or closed.
     */
    private int[] getNextWorkingRange() {
        // Get the starting point of the range
        int startRange = 0;
        while (primeMap.get(startRange) != PrimeState.OPEN && primeMap.get(startRange) != null) {
            ++startRange;
        }

        if (primeMap.get(startRange) != null) {
            int endRange = startRange + workSize - 1;

            // Adjust the range if it's bigger than the size of the prime map
            if (endRange >= primeMap.size()) {
                endRange = primeMap.size() - 1;

                logger.warning(startRange + " + " + (workSize - 1) + "is bigger than " + (primeMap.size() - 1) + "! Only working from " + startRange + " to " + endRange);
            }

            return new int[]{startRange, endRange};
        } else {
            logger.info("Every package distributed!");
            distributeWork = false;

            // Let everyone know that we are finished
            addBroadcastMessage(MessageType.FINISHED, "");

            // Also stop this node
            stopNode();

            return new int[]{-1};
        }
    }

    /**
     * Distributes a working range to the given connection.
     *
     * @param connection the connection object to distribute work to
     */
    private void distributeWork(Connection connection) {
        // Get the next range
        int[] nextRange = getNextWorkingRange();

        // Check whether the range is valid
        if (nextRange[0] >= 0) {
            // Update the state
            modifyPrimesMap(nextRange, PrimeState.TENTATIVE);

            // The node has 5 seconds to confirm they're working on it
            Timer workTimeout = new Timer();
            TimerTask workTimeoutTask = new NodeTimeoutTask(connection) {
                @Override
                public void run() {
                    logger.info(this.node.getName() + " failed to response in time!");
                    this.node.setShouldBeWorking(false);

                    modifyPrimesMap(nextRange[0], nextRange[1], PrimeState.OPEN);
                }
            };
            workTimeout.schedule(workTimeoutTask, 5000);
            connection.setWorkTimeout(workTimeout);

            // Convert the range to a string
            String stringRange = createStringFromRange(nextRange);

            // Force the Node to work
            addOutgoingMessage(connection, createMessage(connection.getName(), MessageType.WORK, stringRange));
            connection.setShouldBeWorking(true);

            logger.info("Distributed work [" + stringRange + "] to " + connection.getName());
        }
    }

    /**
     * Distributed work to the PrimeWorker of the Leader. This can't follow the same protocol used for other nodes, since
     * there is no connection to send a message to.
     */
    private void distributeWorkToMyself() {
        // Get the next range
        int[] nextRange = getNextWorkingRange();

        if (nextRange[0] >= 0) {
            // Update the state to working directly
            modifyPrimesMap(nextRange, PrimeState.WORKING);

            // Start working
            primeWorker.startWorking(createStringFromRange(nextRange));

            logger.info("Distributed " + Arrays.toString(nextRange) + " to myself.");
        }
    }

    /**
     * Updates the state of the primes in the given range
     *
     * @param range      the range of primes to update
     * @param state      the new state of the primes
     * @param connection the connection which caused the state change
     */
    private void updatePrimeState(int[] range, PrimeState state, Connection connection) {
        logger.info("Prime range " + Arrays.toString(range) + " changed to " + state);

        // Change the states of the indexes
        modifyPrimesMap(range, state);

        if (this.state == State.LEADER) {
            // Notify everyone of the change
            addBroadcastMessage(MessageType.WORK_STATE, createPrimeStateString(range, state));

            if (connection != null) {
                stopTimer(connection.getWorkTimeout());

                connection.setShouldBeWorking(state == PrimeState.WORKING);
                connection.setWorkRange(state == PrimeState.WORKING ? range : null);
            }

            if (!distributeWork) {
                // Let everyone know that we are finished
                addBroadcastMessage(MessageType.FINISHED, "");

                // Also stop this node
                stopNode();
            }
        }
    }

    /**
     * Modifies the indexes in the prime map.
     *
     * @param range the range of indexes to modify
     * @param state the new state of the indexes
     */
    private synchronized void modifyPrimesMap(int[] range, PrimeState state) {
        modifyPrimesMap(range[0], range[1], state);
    }

    /**
     * Modifies the indexes in the prime map.#+
     *
     * @param lower the lower border of the range
     * @param upper the upper border of the range
     * @param state the new state of the indexes in the given range
     */
    private synchronized void modifyPrimesMap(int lower, int upper, PrimeState state) {
        for (int i = lower; i <= upper; ++i) {
            primeMap.put(i, state);
        }
    }

    /**
     * Loads the primes from the file into the variables
     */
    private void fillPrimesMap() {
        try {
            URL defaultImage = Node.class.getResource(primesFile);
            assert defaultImage != null;
            File imageFile = new File(defaultImage.toURI());
            String primes = InputOutput.readFile(imageFile);
            String[] primesList = primes.split(String.valueOf('\n'));
            for (int i = 0; i < primesList.length; i++) {
                primeList.add(primesList[i]);
                primeMap.put(i, PrimeState.OPEN);
            }
        } catch (Exception e) {
            logError("filling primes map", e, true);
        }
    }

    public void setPrimesFile(String primesFile) {
        this.primesFile = "/primes" + primesFile + ".txt";
    }

    public void setWorkSize(int workSize) {
        this.workSize = workSize;
    }

    // -------------------- [Connection related] -------------------- //

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
                    connections.put(createConnectionKey(address, port), new Connection(address, port, welcome.getSender(), clientSocket, tempHandler));

                    // Go through every given combination of IP:Port by splitting at the comma
                    for (String connectionInformation : ((String) welcome.getPayload()).split(",")) {
                        if (connectionInformation.length() > 0 && !connections.containsKey(connectionInformation)) {
                            logger.fine("Received the following connections: " + connectionInformation);
                            String[] connection = connectionInformation.split(":");
                            String[] ipParts = connection[0].split("\\.");

                            // Connect to the new node
                            connectTo(
                                    // Create the InetAddress object
                                    InetAddress.getByAddress(new byte[]{(byte) Integer.parseInt(ipParts[0]), (byte) Integer.parseInt(ipParts[1]), (byte) Integer.parseInt(ipParts[2]), (byte) Integer.parseInt(ipParts[3])}),
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
     * Gets the address of the client connection. This is either the address of the current node, or the node the client is connected to.
     *
     * @return the address of the node to send the information to
     */
    private InetAddress getClientConnectionAddress() {
        return isClientConnectedToMe() ? address : clientConnection.getAddress();
    }

    /**
     * Gets the port of the client connection. This is either the port of the current node, or the node the client is connected to.
     *
     * @return the port of the node to send the information to
     */
    private int getClientConnectionPort() {
        return isClientConnectedToMe() ? port : clientConnection.getPort();
    }

    /**
     * Checks whether the client is connected to this node.
     *
     * @return true, if the client is connected to this node.
     */
    private boolean isClientConnectedToMe() {
        return clientConnection != null && clientConnection.getPort() == -1;
    }

    // -------------------- [Logging related] -------------------- //

    /**
     * Logs an error to the console and stops the node if the error is critical.
     *
     * @param errorOccurrence A String containing information where the error occurred
     * @param e               The exception that was thrown
     * @param critical        Whether this error is critical to the Nodes' functionality
     */
    public void logError(String errorOccurrence, Exception e, boolean critical) {
        logger.log(critical ? Level.SEVERE : Level.WARNING, "Encountered an error while " + errorOccurrence + ": " + e.toString());

        if (critical) {
            stopNode();
        }
    }

    /**
     * Prints out the connections of this node.
     */
    public void logConnections() {
        logger.info("Connected to: " + connections);
    }

    // -------------------- [Messaging] -------------------- //

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
        // Check whether the queue is empty
        ConcurrentLinkedQueue<Message> messages = outgoingMessages.get(receiver);
        if (messages == null) {
            // Create a new queue
            messages = new ConcurrentLinkedQueue<>();

            outgoingMessages.put(receiver, messages);
        }

        // Add the message to the queue
        outgoingMessages.get(receiver).add(message);
    }

    // -------------------- [Raft] -------------------- //

    /**
     * Handles the timeout of a Node by removing it from the connections map and broadcasting the disconnect if this Node
     * is the leader.
     *
     * @param connection the connection of the disconnected Node.
     */
    private void handleNodeTimeout(Connection connection) {
        // Check whether the connection is in the hash map
        if (!connections.containsValue(connection)) {
            try {
                connection = connections.get(createConnectionKey(connection));
            } catch (NullPointerException e) {
                logger.warning("Passed connection was null!");
            }
        }

        if (connection != null) {
            String connectionKey = createConnectionKey(connection.getAddress(), connection.getPort());
            logger.info(connection.getName() + " (" + connection.getState() + ") disconnected!");

            // Check whether the disconnected node was the leader
            if (connection.getState() == State.LEADER) {
                if (primeWorker != null) {
                    // Stop the prime worker
                    primeWorker.stopWorking();
                }

                // Reset every WORKING Index to OPEN
                for (Map.Entry<Integer, PrimeState> entry : primeMap.entrySet()) {
                    int index = entry.getKey();
                    if (entry.getValue() == PrimeState.WORKING) {
                        modifyPrimesMap(index, index, PrimeState.OPEN);
                    }
                }

                leaderConnection = null;
            } else if (state == State.LEADER) {
                // Inform everyone of the disconnect
                addBroadcastMessage(MessageType.DISCONNECT, connectionKey);

                // Change the indexes this Node was working on to open
                if (connection.getWorkRange() != null) {
                    updatePrimeState(connection.getWorkRange(), PrimeState.OPEN, connection);
                }
            }

            // Remove the connection
            connections.remove(connectionKey);

            // Remove every outgoing message to the dead connection
            outgoingMessages.remove(connection);

            // Abort, if there is no one other than me
            if (connections.isEmpty()) {
                logger.severe("I'm alone here...");
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

    // -------------------- [Other] -------------------- //

    /**
     * Stops the execution of the SocketServer, CommunicationHandler, Raft protocol and the PrimeWorker.
     */
    public void stopNode() {
        if (nodeRunning) {
            logger.info("Stopping this node!");

            nodeRunning = false;

            // Stop the leader timeout
            stopTimer(leaderTimeout);

            // Stop the raft tasks
            raft.stop();

            if (primeWorker != null) {
                primeWorker.stopWorking();
            }

            if (state == State.LEADER) {
                // Stop the disconnection timeouts of the other nodes
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
        Option primeList = new Option("pr", "primes", true, "Number of how many primes to use for calculation (100/1000/10000/100000");
        primeList.setRequired(true);

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
        String primes = cl.getOptionValue("primes");

        // Get parsed strings for cluster connection
        String host_Port = cl.getOptionValue("host_port");
        String host_Address = cl.getOptionValue("host_address");

        System.out.println("Node: " + nodePort + " " + nodeName + " " + nodeAddress);
        System.out.println("Cluster: " + host_Port + " " + host_Address);

        Node clusterNode = new Node(Integer.parseInt(nodePort), nodeName);
        clusterNode.setPrimesFile(primes);
        clusterNode.setWorkSize(Integer.parseInt(primes)/100);
        Thread nodeThread = new Thread(clusterNode);
        nodeThread.start();
        Thread.sleep(1000);

        //If

        if (null != host_Port && null != host_Address) {
            clusterNode.connectTo(host_Address, Integer.parseInt(host_Port));
        }

        try {
            nodeThread.join();
            System.exit(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
