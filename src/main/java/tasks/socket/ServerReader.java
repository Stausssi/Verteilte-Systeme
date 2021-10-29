package tasks.socket;

import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.File;
import java.io.IOException;
import java.net.Socket;

public class ServerReader implements Runnable {
    private final Socket serverSocket;

    public ServerReader(Socket serverSocket) {
        this.serverSocket = serverSocket;
    }

    @Override
    public void run() {
        File output = new File("socketoutput.txt");
        try {
            if (output.createNewFile() || output.exists()) {
                System.out.println("New connection");

                Message incomingMessage;
                ObjectMessageHandler messageHandler = new ObjectMessageHandler(serverSocket);

                while (!serverSocket.isClosed()) {
                    // Read client message
                    incomingMessage = messageHandler.read();

                    Message reply = new Message();
                    reply.setSender(incomingMessage.getSender());
                    reply.setReceiver(incomingMessage.getReceiver());
                    reply.setPayload(incomingMessage.getPayload());
                    reply.setType(incomingMessage.getType());
                    reply.setTime(incomingMessage.getTime());
                    reply.setSequenceNo(incomingMessage.getSequenceNo());

                    // Broadcast message to every client
                    for (Socket s : Server.socketConnections) {
                        ObjectMessageHandler messageSender = new ObjectMessageHandler(s);
                        messageSender.write(reply);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
