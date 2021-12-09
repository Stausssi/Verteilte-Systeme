package exam;

import tasks.io.InputOutput;
import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.*;
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
    private boolean distributeWork = false;

    private final InetAddress address;
    private final int port;
    protected final String name;
    protected State state;

    private Connection leaderConnection;
    private Connection clientConnection;

    //Vars for primes
    private static final String primesFile = "/primes10000.txt";
    public volatile ConcurrentHashMap<Integer, Index> primeMap = new ConcurrentHashMap<>();
    private final ArrayList<String> primeList = new ArrayList<>();
    private static final int workSize = 250;

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
     * Creates a new Node.
     *
     * @param port the port the socket server will run on
     * @param name the name of the node
     * @throws UnknownHostException if the host name is invalid
     */
    public Node(int port, String name) throws UnknownHostException {
        this.address = InetAddress.getByName("localhost");
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
        try {
            fillPrimesMap();
        } catch (Exception e) {
            logConsole(e, "filling primes map");
        }
    }

    /**
     * Creates a new Node.
     *
     * @param address the address of the socket server
     * @param port    the port the socket server will run on
     * @param name    the name of the node
     */
    public Node(InetAddress address, int port, String name) {
        this.address = address;
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
        try {
            fillPrimesMap();
        } catch (Exception e) {
            logConsole(e, "filling primes map");
        }
    }

    @Override
    public void run() {
        // Create the threads for server, communicator and Raft protocol
        Thread serverThread = new Thread(socketServer);
        Thread communicatorThread = new Thread(communicationHandler);
        Thread raftThread = new Thread(raft);

        serverThread.start();
        communicatorThread.start();
        raftThread.start();

        try {
            serverThread.join();
            communicatorThread.join();
            raftThread.join();
        } catch (InterruptedException e) {
            logConsole(e, "waiting for threads to finish");
        }
    }


    /**
     * This class will accept incoming connections and save them to a Connection class, which will be later used for
     * communication.
     */
    private class SocketServer implements Runnable {
        private ServerSocket serverSocket;

        @Override
        public void run() {
            // Open the ServerSocket
            try {
                try {
                    this.serverSocket = new ServerSocket(
                            port,
                            MAX_INCOMING_CLIENTS,
                            address
                    );
                } catch (IOException e) {
                    logConsole(e, "starting the SocketServer");
                }

//                logConsole("Started server socket on: " + serverSocket);

                while (nodeRunning) {
                    // Accept a new connection
                    Socket newConnection = serverSocket.accept();
                    ObjectMessageHandler tempHandler = new ObjectMessageHandler(newConnection);

                    // Read the first message the new connection sent
                    Message firstMessage = tempHandler.read();

                    // If it's a "hello" message, the new connection is a Node
                    if (firstMessage.getMessageType() == MessageType.HELLO) {
//                        logConsole("Hello message received: " + hello);

                        String connectionName = firstMessage.getSender();
                        int port = (Integer) firstMessage.getPayload();

                        if (connectionName != null && port > 0) {
                            // Send the node a serialized version of IP:Port combinations in the connections object
                            tempHandler.write(createWelcomeMessage(connectionName));

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

                        // Otherwise, the new connection might be a client
                    } else if (firstMessage.getMessageType() == MessageType.RSA && "Client".equalsIgnoreCase(firstMessage.getSender())) {
                        logConsole("Received RSA information from the client!");
//                        String publicKey = (String) firstMessage.getPayload();
//                        logConsole("Public Key: " + publicKey);

                        // Send the client a serialized version of IP:Port combinations in the connections object
                        tempHandler.write(createWelcomeMessage("Client"));

                        // Save the client connection
                        clientConnection = new Connection(newConnection.getInetAddress(), -1, "Client", newConnection);

                        // Forward the RSA information to the leader
                        if (leaderConnection != null) {
                            addOutgoingMessage(leaderConnection, firstMessage);
                        } else {
                            // Parse the message from the client directly
                            communicationHandler.parseMessage(firstMessage, clientConnection);
                        }

                        // Otherwise, close the connection
                    } else {
                        newConnection.close();
                    }
                }
            } catch (IOException e) {
                if (!(e instanceof SocketException)) {
                    logConsole(e, "handling connections in the SocketServer");
                }
            }
//            logConsole("SocketServer is gone");
        }

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

            return createMessage(
                    connectionName,
                    MessageType.WELCOME,
                    connectionsBuilder.toString()
            );
        }
    }

    /**
     * This class is used to handle the communication between the Nodes.
     * It reads incoming messages and sends the corresponding responses.
     */
    private class CommunicationHandler implements Runnable {
        @Override
        public void run() {
//            logConsole("The CommunicationHandler was started");

            while (nodeRunning || !broadcastMessages.isEmpty()) {
                // Go through every broadcast message
                for (Message broadcast : broadcastMessages) {
                    for (Connection c : connections.values()) {
                        addOutgoingMessage(c, broadcast);

//                        logConsole("Sent broadcast " + b + " to " + c.getName() + " (" + c.getSocket() + ")");
                    }

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
                        logConsole(e, "checking for available messages");
                    }

                    // Check whether the node is working
                    if (state == State.LEADER && !c.isWorking() && !c.shouldBeWorking() && distributeWork) {
                        distributeWork(c);
                    }

                    // Send the messages which are only directed at the connection
                    if (outgoingMessages.containsKey(c)) {
                        for (Message msg : outgoingMessages.get(c)) {
                            messageHandler.write(msg);
                            outgoingMessages.get(c).remove();
                        }
                    }
                }
            }
//            logConsole("CommunicationHandler is gone");
        }

        /**
         * Parsed the given message by using the MessageType and acts accordingly.
         *
         * @param incomingMessage the message to parse and handle
         * @param connection      the connection the message came from
         */
        private void parseMessage(Message incomingMessage, Connection connection) {
//            logConsole("Incoming " + incomingMessage + "\nFrom Connection: " + connection.getName());

            switch (incomingMessage.getMessageType()) {
                case REQUEST:
                    logConsole("This is a request");
                    break;
                case RSA:
                    logConsole("PublicKey received: " + incomingMessage.getPayload());

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
                            addBroadcastMessage(
                                    MessageType.CLIENT_CONNECTION,
                                    keyOfClientConnection
                            );
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
                        logConsole("Forwarding primes to the client connection!");

                        // Forward the primes to the Node connected to the Client, which will forward the received
                        // primes to the client
                        Message primeMessage = createMessage(
                                "Client",
                                MessageType.PRIMES,
                                incomingMessage.getPayload()
                        );

                        // Check whether the client is connected to the leader
                        if (clientConnection.getPort() == -1) {
                            clientConnection.getMessageHandler().write(primeMessage);
                        } else {
                            addOutgoingMessage(clientConnection, primeMessage);
                        }

                        // Let everyone know that we are finished
                        addBroadcastMessage(MessageType.FINISHED, "");

                        // Also stop this node
                        stopNode();
                    } else if (clientConnection != null) {
                        logConsole("Received Primes. Forwarding to client now!");
                        incomingMessage.setSender(name);

                        // Send the primes to the client
                        clientConnection.getMessageHandler().write(incomingMessage);
                    }
                    break;
                case RAFT_ELECTION:
//                    logConsole("Raft Election started by " + incomingMessage.getSender());

                    // Reply to the candidate with whether we already voted.
                    // Already voted -> false
                    // Not voted -> true
                    addOutgoingMessage(connection, createMessage(
                            incomingMessage.getSender(),
                            MessageType.RAFT_VOTE,
                            !hasVoted
                    ));

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
                            logConsole("Im the boss in town");

                            state = State.LEADER;
                            leaderConnection = null;

                            // Distribute the work packages if a public key exists
                            distributeWork = publicKey.length() > 0;

                            // Start the heartbeat task
                            raft.initLeaderHeartbeat();
                        } else if (votesReceived == connections.size()) {
//                            logConsole("I was not elected Sadge");

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
//                    logConsole("Heartbeat received by " + incomingMessage.getSender());
                    if (state == State.LEADER) {
                        if (incomingMessage.getPayload() == State.FOLLOWER) {
                            // Reset timer for node disconnection
                            Timer temp = connection.getNodeTimeout();
                            stopTimer(temp);

                            temp = new Timer();
                            temp.schedule(new NodeTimeoutTask(connection) {
                                @Override
                                public void run() {
                                    if (nodeRunning) {
                                        logConsole(this.node.getName() + " disconnected!");
                                        handleNodeTimeout(this.node);
                                    }
                                }
                            }, 2000);

                            connection.setNodeTimeout(temp);
                        }
                    } else {
                        if (incomingMessage.getPayload() == State.LEADER) {
//                            logConsole("Received leader heartbeat!");
                            // Reset timer for reelection
                            stopTimer(leaderTimeout);

                            leaderTimeout = new Timer();
                            leaderTimeout.schedule(new NodeTimeoutTask(connection) {
                                @Override
                                public void run() {
                                    logConsole("The leader has died!");
                                    handleNodeTimeout(this.node);
                                }
                            }, 2000);

                            // Send heartbeat back
                            addOutgoingMessage(connection, createMessage(
                                    incomingMessage.getSender(),
                                    MessageType.RAFT_HEARTBEAT,
                                    state
                            ));
                        }
                    }
                    break;
                case STATE:
//                    logConsole("State of connection " + connection.getName() + " changed to " + incomingMessage.getPayload());

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
//                    logConsole("I dont want to work but i have to in order to survive:");

                    // Tell the leader this Node will work on the range, if there is no other task running
                    if (primeWorker == null && publicKey.length() > 0) {
                        addOutgoingMessage(leaderConnection, createMessage(
                                leaderConnection.getName(),
                                MessageType.WORK_STATE,
                                incomingMessage.getPayload() + ":" + Index.WORKING
                        ));

                        primeWorker = new PrimeWorker(
                                (String) incomingMessage.getPayload(),
                                publicKey,
                                primeList,
                                new WorkerCallback() {
                                    @Override
                                    public void resultFound(String p, String q) {
                                        logConsole("Worker found the result!");

                                        // Notify the leader of the result
                                        addOutgoingMessage(leaderConnection, createMessage(
                                                leaderConnection.getName(),
                                                MessageType.PRIMES,
                                                p + "," + q
                                        ));
                                    }

                                    @Override
                                    public void workerFinished(String range) {
//                                        logConsole("Worker is finished!");
                                        primeWorker = null;

                                        // Tell the leader this range is finished
                                        addOutgoingMessage(leaderConnection, createMessage(
                                                leaderConnection.getName(),
                                                MessageType.WORK_STATE,
                                                range + ":" + Index.CLOSED
                                        ));
                                    }
                                }
                        );

                        new Thread(primeWorker).start();
                    }
                    break;
                case WORK_STATE:
                    // Get the range and new State
                    String[] information = ((String) incomingMessage.getPayload()).split(":");
                    int[] range = Arrays.stream(information[0].trim().split(",")).mapToInt(Integer::parseInt).toArray();
                    Index newState = Index.valueOf(information[1]);

                    // Change the states of the indexes
                    for (int i = range[0]; i <= range[1]; ++i) {
                        primeMap.put(i, newState);
                    }

                    if (state == State.LEADER) {
                        stopTimer(connection.workTimeout);

                        if (newState == Index.CLOSED) {
                            logConsole("Primes in range " + Arrays.toString(range) + " are done!");
                        }

                        // Inform every node of the state change
                        addBroadcastMessage(MessageType.WORK_STATE, incomingMessage.getPayload());

                        connection.setIsWorking(newState == Index.WORKING);
                        connection.setShouldBeWorking(newState == Index.WORKING);
//                        logConsole(connection + " has just finished something hehe boi");
                    }
                    break;
                case CLIENT_CONNECTION:
                    if (clientConnection == null) {
                        clientConnection = connections.get((String) incomingMessage.getPayload());
                    }
                    break;
                case DISCONNECT:
                    logConsole("Node with key " + incomingMessage.getPayload() + " disconnected!");
                    connections.remove((String) incomingMessage.getPayload());

                    // Abort, if there is no one other than me
                    if (connections.size() < 2) {
                        logConsole("Im alone here");
                        nodeRunning = false;
                    }
                    break;
                case FINISHED:
                    logConsole("Stopping this node!");

                    stopNode();
                    break;
                default:
                    logConsole("Message fits no type " + incomingMessage);
                    break;
            }
        }
    }

    private void stopTimer(Timer timer) {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }

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
                    logConsole(connection.getName() + " failed to response! Therefore, the working range is reset");
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
            addOutgoingMessage(connection, createMessage(
                    connection.getName(),
                    MessageType.WORK,
                    stringRange
            ));

            connection.setShouldBeWorking(true);
            logConsole("Distributed work (" + stringRange + ") to " + connection.getName());
        } else {
            logConsole("Every package distributed");
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

                    // Save the connection
                    connections.put(
                            createConnectionKey(address, port),
                            new Connection(address, port, welcome.getSender(), clientSocket)
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
            } catch (IOException e) {
                logConsole(e, "connection to a new connection");
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
            logConsole(e, "parsing the host name");
        }
    }

    /**
     * Logs a given message to the console and prepends the node name in front of the message
     *
     * @param log The message to log to sysout
     */
    public void logConsole(String log) {
        System.out.println("[" + this.name + "]: " + log);
    }

    public void logConsole(Exception e, String errorOccurrence) {
        logConsole("Encountered an error while " + errorOccurrence + ": " + e.toString());
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
        logConsole("Connected to: " + connections);
    }

    /**
     * Creates a Message object with the given params
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

    /**
     * Creates a broadcast method with the given type and payload.
     *
     * @param messageType the type of the broadcast message
     * @param payload     the payload of the broadcast
     */
    protected synchronized void addBroadcastMessage(MessageType messageType, Object payload) {
        broadcastMessages.add(createMessage(
                "",
                messageType,
                payload
        ));
    }

    protected synchronized void addOutgoingMessage(Connection receiver, Message message) {
        ConcurrentLinkedQueue<Message> messages = outgoingMessages.get(receiver);
        if (messages == null) {
            messages = new ConcurrentLinkedQueue<>();

            outgoingMessages.put(
                    receiver, messages
            );
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
        if (state == State.LEADER) {
            addBroadcastMessage(MessageType.DISCONNECT, connectionKey);
        }

        connections.remove(connectionKey);
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
            logConsole(e, "closing the ServerSocket");
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


}
