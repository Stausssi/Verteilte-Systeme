package exam;

import java.net.InetAddress;
import java.net.Socket;

public final class Connection {
    private final InetAddress address;
    private final int port;
    private String name;
    private final Socket socket;

    public Connection(InetAddress address, int port, String name, Socket socket) {
        this.address = address;
        this.port = port;
        this.name = name;
        this.socket = socket;
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
}