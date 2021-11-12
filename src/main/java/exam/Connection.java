package exam;

import tasks.messages.ObjectMessageHandler;

import java.net.InetAddress;
import java.net.Socket;

public final class Connection {
    private final InetAddress address;
    private final int port;
    private String name;
    private final Socket socket;
    private final ObjectMessageHandler messageHandler;
    private State state = State.FOLLOWER;

    public Connection(InetAddress address, int port, String name, Socket socket) {
        this.address = address;
        this.port = port;
        this.name = name;
        this.socket = socket;
        this.messageHandler = new ObjectMessageHandler(socket);
    }

    public Connection(InetAddress address, int port, String name, Socket socket, State state) {
        this.address = address;
        this.port = port;
        this.name = name;
        this.socket = socket;
        this.messageHandler = new ObjectMessageHandler(socket);;
        this.state = state;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getAddress() {
        return address;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Socket getSocket() {
        return socket;
    }

    public ObjectMessageHandler getMessageHandler() {
        return messageHandler;
    }

    public void setState(State state) {
        this.state = state;
    }

    public State getState() {
        return state;
    }
}
