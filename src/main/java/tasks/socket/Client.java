package tasks.socket;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;

/**
 * This class is used as an example for a Client Socket
 * this could be a own thread
 */
public class Client implements Runnable {
    public final String name;
    private static int count;

    public Client(String name) {
        this.name = name;
    }

    /**
     * this method initialises the client
     *
     * @param dns  distination like " localhost "; can also be
     *             an IP
     * @param port port to connect to
     * @return the created socket after connection is
     * established
     */
    public Socket initialise(String dns, int port) throws IOException {
        // no need for an additional bind , but could be done here
        return new Socket(dns, port);
    }

    @Override
    public void run() {
        try {
            Thread.sleep(1000);
            Socket clientSocket = this.initialise("localhost", 4444);
            Random random = new Random();

            Message incomingMessage;
            ObjectMessageHandler messageHandler = new ObjectMessageHandler(clientSocket);

            do {
                Message message = new Message();
                message.setSender(this.name);
                message.setSequenceNo(count());
                message.setReceiver("Server Socket");
                message.setType("String");

                if (message.getSequenceNo() % 20 == 0) {
                    message.setPayload("last message");
                    messageHandler.write(message);
                    System.out.println("Client received last message:\n" + messageHandler.read());
                } else {
                    message.setPayload("Dies ist eine Nachricht an den Server");
                    messageHandler.write(message);
                }

                incomingMessage = messageHandler.read();
                String fileContent = incomingMessage.getPayload().toString().replace("\t", "\n");

                Thread.sleep(random.nextInt(200));
            } while (true);
        } catch (IOException | InterruptedException ioException) {
            ioException.printStackTrace();
        }
    }

    private synchronized int count() {
        return count++;
    }
}