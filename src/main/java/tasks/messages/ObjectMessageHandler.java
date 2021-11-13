package tasks.messages;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 * This class is used as an example for reading and writing Message-Objects
 * from/to a socket
 */
public class ObjectMessageHandler {
    private final Socket socket;

    public ObjectMessageHandler(Socket socket) {
        this.socket = socket;
    }

    /**
     * Reads objects from the socket
     *
     * @return the message object or null , in case of an
     * error
     */
    public Message read() {
        Message ret = null;
        try {
            // Get the input stream and try reading on it
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
            ret = (Message) inputStream.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    /**
     * Writes Message objects to the socket.

     * @param message the message to write to the socket
     */
    public void write(Message message) {
        try {
            // Get the output stream and try writing on it
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.writeObject(message);
//            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns whether there is any data available on the input stream.
     *
     * @return true, if there is a Message object on the input stream.
     *
     * @throws IOException if something went wrong.
     */
    public boolean isMessageAvailable() throws IOException {
        return socket.getInputStream().available() != 0;
    }
}