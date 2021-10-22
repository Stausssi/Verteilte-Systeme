package roles;

import messages.Message;
import messages.ObjectMessageHandler;
import socket.Client;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Worker extends Client implements Serializable {
    private final Role role;
    private final List<Message> messages = new ArrayList<>();
    private final List<Worker> nodes = new ArrayList<>();

    public Worker(String name, Role role) {
        super(name);

        this.role = role;
        nodes.add(this);
    }

    @Override
    public void run() {
        try {
            Socket clientSocket = this.initialise("localhost", 4444);

            Message incomingMessage;
            ObjectMessageHandler messageHandler = new ObjectMessageHandler(clientSocket);
            String forwardTo = "";

            Message helloMessage = new Message();
            helloMessage.setType("node");
            helloMessage.setSender(this.name);
            helloMessage.setPayload(this.role);
            messageHandler.write(helloMessage);

            while (!clientSocket.isClosed()) {
                Message reply = new Message();
                reply.setSender(this.name);
                reply.setTime(Instant.now());

                if (this.role == Role.UNKNOWN) {
                    reply.setType("request");
                }

                incomingMessage = messageHandler.read();

                if (this.role == Role.COORDINATOR) {
                    messages.add(incomingMessage);
                }

                logConsole("Received: " + incomingMessage.toString());

                if (incomingMessage.getType().equals("node")) {
                    nodes.add(new Worker(incomingMessage.getSender(), (Role) incomingMessage.getPayload()));
                    System.out.println("New node added!");
                    System.out.println(nodes + " Ich: " + this);
                } else if (incomingMessage.getReceiver().equals(this.name)) {
                    reply.setReceiver(incomingMessage.getSender());

                    if (incomingMessage.getType().equals("request")) {
                        if (this.role == Role.COORDINATOR) {
                            reply.setType("messages");
                            reply.setPayload(
                                    messages.size() > 10 ?
                                            messages.subList(messages.size() - 10, messages.size()) :
                                            messages
                            );
                        } else {
                            Worker coordinator = nodes.stream()
                                    .filter(worker -> worker.role == Role.COORDINATOR)
                                    .findFirst()
                                    .orElse(null);

                            if (coordinator != null) {
                                reply.setType("request");
                                reply.setReceiver(coordinator.name);
                                forwardTo = incomingMessage.getSender();
                            }

                        }

                    } else if (incomingMessage.getType().equals("messages")) {
                        if (!forwardTo.equals("")) {
                            reply.setReceiver(forwardTo);
                            reply.setPayload(incomingMessage.getPayload());

                            forwardTo = "";
                        }
                    }

                    logConsole("Sent: " + reply);

                    messageHandler.write(reply);

                    if (this.role == Role.COORDINATOR) {
                        messages.add(reply);
                    }
                }

                if (this.role == Role.COORDINATOR) {
                    logConsole("Messages:\n " + messages);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logConsole(String log) {
        System.out.println("[" + this.name + "]: " + log);
    }
}
