package exam;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;


import java.io.IOException;
import java.net.*;
import java.util.HashMap;

/**
 * A Node represents a working element of the decryption task.
 * It has a server socket , to which other nodes can connect and send messages to, and a client socket, which will
 * connect and send messages to other nodes.
 * One Node in the system is the coordinator, which will distribute the tasks to the other nodes (so-called workers)
 */
public class Node implements Runnable {
    private static int maxIncomingClients = 100;

    private final int port;
    private final String name;
    private State state;

    private boolean allowNewConnections = true;
    public final HashMap<InetAddress, Integer> connectedTo = new HashMap<>();

    // Create Threads for both the server and client socket
    private final SocketServer socketServer = new SocketServer();
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
    private class SocketServer implements Runnable {

        /**
         * This class will read the incoming messages and send responses.
         */
        private class IncomingReader implements Runnable {
            private final Socket connection;

            public IncomingReader(Socket connection) {
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
                        if (connectedTo.containsValue(port)) {
                            logConsole("Already connected to that node!");
                            connection.close();
                        } else {
                            logConsole("This is a new connection!");

                            // Connect to the new node
                            socketClient.connectTo(connection.getInetAddress(), (Integer) incomingMessage.getPayload());
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
                    IncomingReader reader = new IncomingReader(connection);
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
    private class SocketClient implements Runnable {

        /**
         * This class will read incoming messages
         */
        private class OutgoingReader implements Runnable {
            private final Socket connection;

            public OutgoingReader(Socket connection) {
                this.connection = connection;
            }

            @Override
            public void run() {
                ObjectMessageHandler messageHandler = new ObjectMessageHandler(connection);

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
                    } else {
                        logConsole("Server responded with an invalid message");
                        connection.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Read incoming messages
//                while (!connection.isClosed()) {
//                    incomingMessage = messageHandler.read();
//                }
            }
        }

        @Override
        public void run() {
        }

        public void connectTo(InetAddress address, int port) {
            try {
                // Try connecting to the given socket
                Socket clientSocket = new Socket(address, port);

                logConsole("Connected to: " + clientSocket);

                // Save that connection
                connectedTo.put(address, port);

                // Create a new thread for the client socket
                Thread readerThread = new Thread(new OutgoingReader(clientSocket));
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
}
