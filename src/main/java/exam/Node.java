package exam;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;


import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Node represents a working element of the decryption task.
 * It has a server socket , to which other nodes can connect and send messages to, and a client socket, which will
 * connect and send messages to other nodes.
 * One Node in the system is the coordinator, which will distribute the tasks to the other nodes (so-called workers)
 */
public class Node implements Runnable {
    private static int maxIncomingClients = 100;

    private final int port;
    protected final String name;
    private State state;

    private boolean allowNewConnections = true;
    public final ConcurrentHashMap<String, SocketClient.ClientCommunicator> connectedTo = new ConcurrentHashMap<>();

    // Create Threads for both the server and client socket
    protected final SocketServer socketServer = new SocketServer();
    private final SocketClient socketClient = new SocketClient();
    private final Raft leaderElection = new Raft(this);

    public Node(int port, String name) {
        this.port = port;
        this.name = name;
        this.state = State.FOLLOWER;
    }

    /**
     * This class will handle the incoming connections and receive messages.
     */
    protected class SocketServer implements Runnable {

        /**
         * This class will read the incoming messages and send responses.
         */
        private class ServerCommunicator implements Runnable {
            private final Socket connection;

            public ServerCommunicator(Socket connection) {
                this.connection = connection;
            }

            @Override
            public void run() {
                ObjectMessageHandler messageHandler = new ObjectMessageHandler(connection);

                // Prompt the new node to send their port
                logConsole("Asking new connection for their port...");
                Message prompt = new Message();
                prompt.setSender(name);
                prompt.setType("request");
                prompt.setPayload("port");

                messageHandler.write(prompt);

                Message incomingMessage = messageHandler.read();

                try {
                    // Check whether the response is indeed the port
                    if ("port".equals(incomingMessage.getType())) {
                        int port = (Integer) incomingMessage.getPayload();
                        logConsole("Port received: " + port);

                        // Check whether the nodes are already connected
                        if (connectedTo.containsKey(connection.getInetAddress() + ":" + port)) {
                            logConsole("Already connected to that node!");
                            connection.close();
                        } else {
                            logConsole("This is a new connection!");

                            // Connect to the new node
                            socketClient.connectTo(connection.getInetAddress(), (Integer) incomingMessage.getPayload());

                            // TODO: Send new note information to everyone
                            for (Map.Entry<String, SocketClient.ClientCommunicator> entry : connectedTo.entrySet()) {
                                SocketClient.ClientCommunicator communicator = entry.getValue();

                                // Create the message object
                                Message newConnection = new Message();
                                newConnection.setSender(name);
                                newConnection.setReceiver(communicator.connection.getName());
                                newConnection.setType("connection");
                                newConnection.setPayload(entry.getKey());

                                // Send it
                                communicator.sendMessage(newConnection);
                            }

                        }
                    } else if ("rsa".equalsIgnoreCase(incomingMessage.getType())) {
                        logConsole("The new connection is the client!");

                        String publicKey = (String) incomingMessage.getPayload();

                        logConsole("Public Key: " + publicKey);

                        // TODO: Either forward message to the coordinator or delegate tasks to every worker
                        // For now, just answer with the primes
                        Message primes = new Message();
                        primes.setType("primes");
                        primes.setPayload("17594063653378370033, 15251864654563933379");

                        messageHandler.write(primes);

                        logConsole("Primes sent!");
                        connection.close();
                    } else {
                        logConsole("Invalid response!");
                        connection.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Listen for further messages
                while (!connection.isClosed()) {
                    incomingMessage = messageHandler.read();
                    logConsole("Incoming: " + incomingMessage);

                    if ("connection".equalsIgnoreCase(incomingMessage.getType())) {
                        String[] payload = ((String) incomingMessage.getPayload()).split(":");
                        try {
                            connectTo(payload[0], Integer.parseInt(payload[1]));
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        @Override
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(
                        port,
                        maxIncomingClients,
                        InetAddress.getByName("localhost")
                );

                logConsole("Started server socket on: " + serverSocket);

                while (allowNewConnections) {
                    Socket connection = serverSocket.accept();

                    // Create a new thread to parse incoming messages
                    ServerCommunicator reader = new ServerCommunicator(connection);
                    Thread readerThread = new Thread(reader);
                    readerThread.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * This class will send messages to other nodes.
     */
    class SocketClient implements Runnable {

        /**
         * This class will read incoming messages
         */
        class ClientCommunicator implements Runnable {
            private final Connection connection;
            private ObjectMessageHandler messageHandler;
            private List<Message> sendBuffer = new ArrayList<>();

            public ClientCommunicator(Connection connection) {
                this.connection = connection;
            }

            @Override
            public void run() {
                Socket socket = connection.getSocket();
                messageHandler = new ObjectMessageHandler(socket);

                // Read the incoming message
                // Should be a port request
                Message incomingMessage = messageHandler.read();

                try {
                    // Check whether the response from the new socket was indeed a port request
                    if (MessageHelper.isPortRequest(incomingMessage)) {
                        logConsole("Server requested port");

                        // Send the port of this socket server
                        Message reply = new Message();
                        reply.setSender(name);
                        reply.setReceiver(incomingMessage.getSender());
                        reply.setType("port");
                        reply.setPayload(port);

                        messageHandler.write(reply);

                        // Also update the name of the Connection object
                        connection.setName(incomingMessage.getSender());
                    } else {
                        logConsole("Server responded with an invalid message");
                        socket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Read incoming messages
                while (!socket.isClosed()) {
                    try {
                        for (Message sendMe : sendBuffer) {
                            messageHandler.write(sendMe);
                            sendBuffer.remove(sendMe);
                        }
                    } catch (ConcurrentModificationException e) {
//                        e.printStackTrace();
                    }

                }
            }

            public void sendMessage(Message message) {
                logConsole("Writing message:\n" + message);
                sendBuffer.add(message);
            }
        }

        @Override
        public void run() {
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
                ClientCommunicator communicator = new ClientCommunicator(connectionInformation);
                connectedTo.put(
                        createConnectionKey(address, port),
                        communicator
                );

                // Create a new thread for the client socket
                Thread readerThread = new Thread(communicator);
                readerThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void run() {
        // Create Threads for both the server and client socket
        Thread serverThread = new Thread(socketServer);
        Thread clientThread = new Thread(socketClient);
        Thread raftThread = new Thread(leaderElection);

        serverThread.start();
        clientThread.start();
        raftThread.start();

        try {
            serverThread.join();
            clientThread.join();
            raftThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void logConsole(String log) {
        System.out.println("[" + this.name + "]: " + log);
    }

    public void connectTo(String name, int port) throws UnknownHostException {
        socketClient.connectTo(InetAddress.getByName(name), port);
    }

    public String createConnectionKey(InetAddress address, int port) {
        return address + ":" + port;
    }
}
