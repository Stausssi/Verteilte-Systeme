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
    private ObjectInputStream inputStream;
    private ObjectOutputStream outputStream;

    public ObjectMessageHandler(Socket socket) {
        try {
            outputStream = new ObjectOutputStream(socket.getOutputStream());
            outputStream.flush();
            inputStream = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * this method reads objects from a given socket
     *
     * @return the message object or null , in case of an
     * error
     */
    public Message read() {
        Message ret = null;
        try {
            ret = (Message) inputStream.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public void write(Message message) {
        try {
            outputStream.writeObject(message);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isMessageAvailable() throws IOException {
        return inputStream.available() != 0;
    }

}