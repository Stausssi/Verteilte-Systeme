package exam;

public enum MessageType {
    // A Message of the type HELLO contains the name (sender) and port of a connection. It is the message that initiates
    // the communication between nodes
    HELLO("hello"),

    // A Message of the type WELCOME contains a list of IP:Port combinations and is sent to a new client
    WELCOME("welcome"),

    RAFT_ELECTION("election"),

    // A Message of the type REQUEST contains a request. The type of the request is specified in the payload.
    REQUEST("request"),

    // A Message of the type RSA contains the public key information from the Client
    RSA("rsa"),

    // A Message of the type PRIMES contains p and q needed for the RSA decryption
    PRIMES("primes");

    private final String typeAsString;

    MessageType(String type) {
        this.typeAsString = type;
    }

    public String toString() {
        return this.typeAsString;
    }
}
