package exam;

/**
 * Contains information about every supported type a Message object can be of
 */
public enum MessageType {
    // A message of the type HELLO contains the name (sender) and port of a connection. It is the message that initiates
    // the communication between nodes
    HELLO("hello"),

    // A message of the type WELCOME contains a list of IP:Port combinations and is sent to a new client
    WELCOME("welcome"),

    // A message of the type STATE contains the current state of the Node
    STATE("state"),

    // A message of the type CLIENT_CONNECTION notifies every node to which node the client is connected to
    CLIENT_CONNECTION("client"),

    // A message of the type DISCONNECT notifies every node of a disconnect
    DISCONNECT("disconnect"),

    // The following message types are needed for the raft communication
    // A message of the type RAFT_HEARTBEAT is sent every X ns to notify every node that this node is still alive
    RAFT_HEARTBEAT("heartbeat"),

    // A message of the type RAFT_ELECTION is sent if a node decides to become the leader
    RAFT_ELECTION("election"),

    // A message of the type RAFT_VOTE is send to the node which started the election and contains whether he has our vote.
    RAFT_VOTE("vote"),

    // A message of the type REQUEST contains a request. The type of the request is specified in the payload.
    REQUEST("request"),

    // A message of the type RSA contains the public key information from the Client
    RSA("rsa"),

    // A message of the type WORK contains an index range for the node to work on
    WORK("work"),

    // A message of the type WORK_STATE notifies every node of the state change of the given index range
    WORK_STATE("work_state"),

    // A message of the type PRIMES contains p and q needed for the RSA decryption
    PRIMES("primes"),

    // A message of the type FINISHED notifies everyone that the task was finished
    FINISHED("finished"),

    // A message of the type INVALID indicates that the message is invalid (default)
    INVALID("invalid");

    private final String typeAsString;

    MessageType(String type) {
        this.typeAsString = type;
    }

    public String toString() {
        return this.typeAsString;
    }
}
