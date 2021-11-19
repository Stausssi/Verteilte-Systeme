package exam;

import eu.boxwork.dhbw.examhelpers.rsa.RSAHelper;
import tasks.messages.Message;
import tasks.messages.ObjectMessageHandler;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;

/**
 * This class represents the client, which will connect to the cluster of Nodes and send the RSA request.
 */
public class Client {
    private static final String encrypted = "2d80afa14a65a7bf26636f97c89b43d5";
    private static final String publicKey = "268342277565109549360836262560222031507";

    private void work() {
        // Connect to any socket in the system
        try {
            Socket cluster = new Socket("localhost", 4444);
            ObjectMessageHandler messageHandler = new ObjectMessageHandler(cluster);

            // Send the RSA information
            Message rsaInfo = new Message();
            rsaInfo.setSender("Client");
            rsaInfo.setMessageType(MessageType.RSA);
            rsaInfo.setPayload(publicKey);
            messageHandler.write(rsaInfo);

            // Now wait for the cluster to solve the key
            // TODO: If socket is gone, check on any other of the given ports
            while (!cluster.isClosed()) {
                Message incomingMessage = messageHandler.read();
                System.out.println("Received: " + incomingMessage);

                if (incomingMessage.getMessageType() == MessageType.PRIMES) {
                    String[] primes = ((String) incomingMessage.getPayload()).replace(" ", "").split(",");
                    System.out.println("Received the primes " + Arrays.toString(primes) + " from the cluster!");

                    String p = primes[0];
                    String q = primes[1];

                    // Create an RSA Helper
                    RSAHelper helper = new RSAHelper();
                    if (helper.isValid(p, q, publicKey)) {
                        System.out.println("Primes are valid!");
                        System.out.println("Decrypted text is: " + helper.decrypt(p, q, encrypted));
                    } else {
                        System.out.println("Primes dont fit!");
                    }

                    cluster.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        client.work();
    }
}
