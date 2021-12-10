package exam;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * This class is used as an example for reading and writing Message-Objects
 * from/to a socket
 */
public class ObjectMessageHandler {
    private final Socket socket;
    private final String nodeName;
    private final Logger logger;

    public ObjectMessageHandler(Socket socket, String nodeName) {
        this.socket = socket;
        this.nodeName = nodeName;
        logger = Utility.initializeLogger(nodeName);
    }

    /**
     * Reads objects from the socket
     *
     * @return the message object or an empty message on error
     * @throws IOException if an IOException occurred
     */
    public Message read() throws IOException {
        Message ret = new Message();
        try {
            // Get the input stream and try reading on it
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
            ret = (Message) inputStream.readObject();

            logger.finest("Incoming " + ret);
        } catch (ClassNotFoundException e) {
            logger.warning("The following error occurred: " + e);
        }
        return ret;
    }

    /**
     * Writes Message objects to the socket.
     *
     * @param message the message to write to the socket
     */
    public void write(Message message) throws IOException {
        message.setSender(nodeName);
        logger.finest("Outgoing " + message);

        // Get the output stream and try writing on it
        ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
        outputStream.writeObject(message);


//        outputStream.flush();
    }

    /**
     * Returns whether there is any data available on the input stream.
     *
     * @return true, if there is a Message object on the input stream.
     * @throws IOException if something went wrong.
     */
    public boolean isMessageAvailable() throws IOException {
        return socket.getInputStream().available() != 0;
    }
}