package messages;

import java.io.*;
import java.net.Socket;

/**
 * This class is used as an example for reading and writing Message-Objects
 * from/to a socket
 */
public class ObjectMessageHandler {
    /**
     * this method reads objects from a given socket
     *
     * @param socket socket to read an object from
     * @return the message object or null , in case of an
     * error
     */
    public Message read(Socket socket) {
        Message ret = null;
        try {
            InputStream is = socket.getInputStream();
            ObjectInputStream ois = new ObjectInputStream(is);
            ret = (Message) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public void write(Socket socket, Message message) {
        try {
            OutputStream os = socket.getOutputStream();
            ObjectOutputStream ois = new ObjectOutputStream(os);
            ois.writeObject(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}