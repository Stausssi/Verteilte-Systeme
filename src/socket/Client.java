package socket;

import messages.Message;

import java.io.*;
import java.net.Socket;
import java.sql.SQLOutput;
import java.util.Objects;

/**
 * This class is used as an example for a Client Socket
 * this could be a own thread
 */
public class Client implements Runnable {
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
        Socket clientSocket = new Socket(dns, port);
        // no need for an additional bind , but could be done here
        return clientSocket;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(1000);
            Socket clientSocket = this.initialise("localhost", 4444);

            OutputStream client_stream = clientSocket.getOutputStream();
            PrintStream client_printstream = new PrintStream(client_stream, true);
            int counter = 0;
            InputStream server_stream = clientSocket.getInputStream();
            BufferedReader client_reader = new BufferedReader(new InputStreamReader(server_stream));
            String reply;
            do {
                Message message = new Message();
                if(counter % 20 ==0) {
                    message.setSender("last message");
                    System.out.println("Client received last message: " + client_reader.readLine());
                }
                else {
                    message.setSender("Hallo Server: Nachricht Nr" + counter);
                }
                reply = client_reader.readLine().replace("\t", "\n");
                Thread.sleep(100);
                counter++;
            }while(true);


        } catch (IOException | InterruptedException ioException) {
            ioException.printStackTrace();
        }
    }
}