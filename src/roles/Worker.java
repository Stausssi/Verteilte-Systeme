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
import java.util.stream.Collectors;

public class Worker extends Client implements Serializable {
    private final Role role;
    private final List<Message> messages = new ArrayList<>();
    private static final List<Worker> nodes = new ArrayList<>();

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
            boolean messagesRetrieved = false;

            while (!clientSocket.isClosed()) {
                Message reply = new Message();
                reply.setSender(this.name);
                reply.setTime(Instant.now());

                if (this.role == Role.UNKNOWN && !messagesRetrieved) {
                    reply.setType("request");
                    reply.setReceiver(getCoordinator());
//                    messageHandler.write(reply);
                    messagesRetrieved = true;
                }

                incomingMessage = messageHandler.read();

                if (this.role == Role.COORDINATOR) {
                    messages.add(incomingMessage);
                }


                if ("node".equals(incomingMessage.getType()) &&
                        !this.name.equals(incomingMessage.getSender()) &&
                        this.role == Role.COORDINATOR) {
//                    nodes.add(new Worker(incomingMessage.getSender(), (Role) incomingMessage.getPayload()));
                    reply.setReceiver(incomingMessage.getSender());
                    reply.setType("welcome");
                    reply.setPayload("Greetings " + incomingMessage.getSender());
                } else if (this.name.equals(incomingMessage.getReceiver())) {
                    logConsole("Received: " + incomingMessage);
                    reply.setReceiver(incomingMessage.getSender());

                    if ("request".equals(incomingMessage.getType())) {
                        if (this.role == Role.COORDINATOR) {
                            reply.setType("messages");
                            reply.setPayload(
                                    messages.size() > 10 ?
                                            new ArrayList<>(messages.subList(messages.size() - 10, messages.size())) :
                                            messages
                            );
                        } else {
                            reply.setType("request");
                            reply.setReceiver(getCoordinator());
                            forwardTo = incomingMessage.getSender();
                        }

                    } else if ("messages".equals(incomingMessage.getType())) {
                        if (!forwardTo.equals("")) {
                            reply.setReceiver(forwardTo);
                            reply.setPayload(incomingMessage.getPayload());

                            forwardTo = "";
                        }
                    }
                }

                if (reply.getType() != null) {
                    logConsole("Sent: " + reply);
                    messageHandler.write(reply);
                }

                if (this.role == Role.COORDINATOR) {
                    messages.add(reply);
//                    logConsole("Messages:\n " + messages);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logConsole(String log) {
        System.out.println("[" + this.name + "]: " + log);
    }

    private String getCoordinator() {
        Worker coordinator = nodes.stream()
                .filter(worker -> worker.role == Role.COORDINATOR)
                .findFirst()
                .orElse(null);

        return coordinator != null ? coordinator.name : null;
    }
}
