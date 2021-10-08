package socket;

import io.InputOutput;
import messages.Message;
import messages.ObjectMessageReader;

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
                BufferedReader server_reader = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
                PrintStream client_stream = new PrintStream(serverSocket.getOutputStream());
                Message reply;
                ObjectMessageReader message_reader = new ObjectMessageReader();
                while (true) {
                    reply = message_reader.read(serverSocket);
                    System.out.println(reply.getSender());
                    if (Objects.equals(reply.getSender(), "last message")) {
                        String[] message = InputOutput.readFile(output).split("\n");
                        client_stream.println(message[message.length - 1]);
                    }
                    InputOutput.writeToFile(output, reply.getSender(), true);
                    client_stream.println(InputOutput.readFile(output).replace("\n", "\t"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
