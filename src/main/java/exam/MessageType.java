package exam;

public enum MessageType {
    // A message of the type HELLO contains the name (sender) and port of a connection. It is the message that initiates
    // the communication between nodes
    HELLO("hello"),

    // A message of the type WELCOME contains a list of IP:Port combinations and is sent to a new client
    WELCOME("welcome"),

    // The following message types are needed for the raft communication
    // A message of the type RAFT_HEARTBEAT is sent every X ns to notify every node that this node is still alive
    RAFT_HEARTBEAT("heartbeat"),

    // A message of the type RAFT_ELECTION is sent if a node decides to become the leader
    RAFT_ELECTION("election"),

    // A message of the type RAFT_APPROVE is send to the node which started the election, if the node approves
    RAFT_APPROVE("approve_election"),

    // A message of the type REQUEST contains a request. The type of the request is specified in the payload.
    REQUEST("request"),

    // A message of the type RSA contains the public key information from the Client
    RSA("rsa"),

    // A message of the type PRIMES contains p and q needed for the RSA decryption
    PRIMES("primes");

    private final String typeAsString;

    MessageType(String type) {
        this.typeAsString = type;
    }

    public String toString() {
        return this.typeAsString;
    }
}
