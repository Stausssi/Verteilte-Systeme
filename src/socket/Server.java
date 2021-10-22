package socket;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * This class is used as an example for a Server Socket
 * this could be a own thread
 */
public class Server implements Runnable {
    // this max client definition is not available in "old "
    // implementations
    public static final int maxIncomingClients = 100;

    /**
     * this method initialises the server
     *
     * @param dns  name like " localhost "
     * @param port port to use
     */
    public void initialise(String dns, int port) throws IOException {
        ServerSocket serverSocket = new ServerSocket(
                port,
                maxIncomingClients,
                InetAddress.getByName(dns)
        );

        while (true) {
            Socket connection = serverSocket.accept();

            ServerReader reader = new ServerReader(connection);
            Thread reader_thread = new Thread(reader);
            reader_thread.start();
        }
        // no need for an additional bind , but could be done here

    }

    @Override
    public void run() {
        try {
            this.initialise("localhost", 4444);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}