package exam;

import java.io.Serializable;
import java.time.Instant;

/**
 * This class is a message object containing important information. It is used for the communication of nodes through a
 * socket connection.
 */
public class Message implements Serializable {
    private String sender = "";
    private String receiver = "";
    private Object payload = "";
    private Instant time = Instant.now();
    private MessageType messageType = MessageType.INVALID;

    /**
     * Gets the sender of this message.
     *
     * @return the sender
     */
    public String getSender() {
        return sender;
    }

    /**
     * Sets the sender of this message.
     *
     * @param sender the sender
     */
    public void setSender(String sender) {
        this.sender = sender;
    }

    /**
     * Gets the receiver of this message.
     *
     * @return the receiver
     */
    public String getReceiver() {
        return receiver;
    }

    /**
     * Sets the receiver of this message.
     *
     * @param receiver the receiver
     */
    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    /**
     * Gets the payload of this message.
     *
     * @return the payload
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * Sets the payload of this message.
     *
     * @param payload the payload
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * Gets the time of this message.
     *
     * @return the time
     */
    public Instant getTime() {
        return time;
    }

    /**
     * Sets the time of this message.
     *
     * @param time the time
     */
    public void setTime(Instant time) {
        this.time = time;
    }

    /**
     * Gets the type of this message.
     *
     * @return the type
     */
    public MessageType getMessageType() {
        return messageType;
    }

    /**
     * Sets the type of this message.
     *
     * @param messageType the type
     */
    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    @Override
    public String toString() {
        return "Message:\n" +
                "\tsender='" + sender + "'\n" +
                "\treceiver='" + receiver + "'\n" +
                "\tpayload='" + payload + "'\n" +
                "\ttime=" + time;
    }
}