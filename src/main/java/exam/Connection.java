package exam;

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
    private boolean shouldBeWorking = false;
    protected Timer nodeTimeout;
    protected Timer workTimeout;
    private int[] workRange;

    /**
     * Creates a new Connection object.
     *
     * @param address        the internet address of the socket connection
     * @param port           the port of the socket server
     * @param name           the name of the Node
     * @param socket         the socket instance
     * @param messageHandler the message handler
     */
    public Connection(InetAddress address, int port, String name, Socket socket, ObjectMessageHandler messageHandler) {
        this.address = address;
        this.port = port;
        this.name = name;
        this.socket = socket;
        this.messageHandler = messageHandler;
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
     * @return returns whether the node is currently working
     */
    public boolean isWorking() {
        return workRange != null;
    }

    /**
     * Get the range this connection is working on
     *
     * @return the range
     */
    public int[] getWorkRange() {
        return workRange;
    }

    /**
     * Set the range this connection is working on
     *
     * @param range the new range
     */
    public void setWorkRange(int[] range) {
        this.workRange = range;
    }

    /**
     * Whether this Connection was given a range to work on
     *
     * @return true, if the node should be working
     */
    public boolean shouldBeWorking() {
        return shouldBeWorking;
    }

    /**
     * Set whether the connection was given a working range
     *
     * @param shouldBeWorking true, if a range was distributed to this connection
     */
    public void setShouldBeWorking(boolean shouldBeWorking) {
        this.shouldBeWorking = shouldBeWorking;
    }

    /**
     * Get the Timer which handles the timeout for work confirmation
     *
     * @return a Timer object
     */
    public Timer getWorkTimeout() {
        return workTimeout;
    }

    /**
     * Set the Timer of the work timeout
     *
     * @param workTimeout the timer to set
     */
    public void setWorkTimeout(Timer workTimeout) {
        this.workTimeout = workTimeout;
    }

    /**
     * Convert this object to a readable string
     *
     * @return a String representing this object
     */
    @Override
    public String toString() {
        return "Connection{" +
                "address=" + address +
                ", port=" + port +
                ", name='" + name + '\'' +
                ", state=" + state +
                '}';
    }
}
