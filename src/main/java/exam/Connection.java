package exam;

import tasks.messages.ObjectMessageHandler;

import java.net.InetAddress;
import java.net.Socket;
import java.util.Timer;

/**
 * This class contains every information of a Node connection.
 */
public class Connection {
    // Information about the socket connection
    private final InetAddress address;
    private final int port;
    private final Socket socket;

    // Information about the node
    private String name;
    private final ObjectMessageHandler messageHandler;
    private State state = State.FOLLOWER;
    private int workingIndex;
    protected Timer nodeTimeout;

    /**
     * Creates a new Connection object.
     *
     * @param address the internet address of the socket connection
     * @param port the port of the socket server
     * @param name the name of the Node
     * @param socket the socket instance
     */
    public Connection(InetAddress address, int port, String name, Socket socket) {
        this.address = address;
        this.port = port;
        this.name = name;
        this.socket = socket;
        this.messageHandler = new ObjectMessageHandler(socket);
    }

    /**
     * Gets the port of the connection.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the address of the connection.
     *
     * @return the address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Sets the name of the connection.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the name of the connection.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the socket instance of the connection.
     *
     * @return the socket instance
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Gets the ObjectMessageHandler of the connection.
     *
     * @return the ObjectMessageHandler
     */
    public ObjectMessageHandler getMessageHandler() {
        return messageHandler;
    }

    /**
     * Sets the state of the node of the connection.
     */
    public void setState(State state) {
        this.state = state;
    }

    /**
     * Gets the state of the node.
     *
     * @return the state
     */
    public State getState() {
        return state;
    }

    /**
     * Gets the timeout timer of the connection.
     *
     * @return the timeout timer
     */
    public Timer getNodeTimeout() {
        return nodeTimeout;
    }

    /**
     * Sets the timeout timer of the connection.
     */
    public void setNodeTimeout(Timer nodeTimeout) {
        this.nodeTimeout = nodeTimeout;
    }

    /**
     * @return returns current index of calculation
     */
    public int getWorkingIndex() {
        return workingIndex;
    }

    /**
     * @param workingIndex set the current index of calculation
     */
    public void setWorkingIndex(int workingIndex) {
        this.workingIndex = workingIndex;
    }
}
