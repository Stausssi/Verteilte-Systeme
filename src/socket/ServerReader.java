package socket;

import io.InputOutput;

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
                String reply;
                while (true) {
                    reply = server_reader.readLine();
                    if (Objects.equals(reply, "last message")) {
                        String[] message = InputOutput.readFile(output).split("\n");
                        client_stream.println(message[message.length - 1]);
                    }
                    InputOutput.writeToFile(output, reply, true);
                    client_stream.println(InputOutput.readFile(output).replace("\n", "\t"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
