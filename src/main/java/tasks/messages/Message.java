package tasks.messages;

import exam.MessageType;

import java.io.Serializable;
import java.time.Instant;

/**
 * This class is a sendable message object containing important information
 */
public class Message implements Serializable {
    private String sender;
    private String receiver;
    private Object payload;
    private Instant time = Instant.now();
    private String type; // may be an enum
    private int sequenceNo = -1;

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }


    public int getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(int sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    @Override
    public String toString() {
        return "Message:\n" +
                "\tsender='" + sender + "'\n" +
                "\treceiver='" + receiver + "'\n" +
                "\tpayload='" + payload + "'\n" +
                "\ttime=" + time + '\n' +
                "\ttype='" + type + "'\n" +
                "\tsequenceNo=" + sequenceNo;
    }
}