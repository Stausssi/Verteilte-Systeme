package socket;

import io.InputOutput;
import messages.Message;
import messages.ObjectMessageHandler;

import java.io.*;
import java.net.Socket;
import java.util.Objects;

public class ServerReader implements Runnable {
    private final Socket serverSocket;
    public ServerReader(Socket serverSocket){
        this.serverSocket = serverSocket;
    }
    @Override
    public void run() {
        File output = new File("socketoutput.txt");
        try {
            if (output.createNewFile() || output.exists()) {
                Message incomingMessage;
                ObjectMessageHandler messageHandler = new ObjectMessageHandler();

                while (true) {
                    Message outgoingMessage = new Message();
                    outgoingMessage.setSender("Server Socket");

                    // Read client message
                    incomingMessage = messageHandler.read(serverSocket);
                    outgoingMessage.setReceiver(incomingMessage.getSender());

                    if (Objects.equals(incomingMessage.getPayload(), "last message")) {
                        String[] message = InputOutput.readFile(output).split("\n\n");

                        outgoingMessage.setPayload(message[message.length - 1]);
                        messageHandler.write(serverSocket, outgoingMessage);
                    }

                    // Reply to client
                    InputOutput.writeToFile(output, incomingMessage.toString() + '\n', true, false);
                    outgoingMessage.setPayload(InputOutput.readFile(output).replace("\n", "\t"));
                    messageHandler.write(serverSocket, outgoingMessage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
