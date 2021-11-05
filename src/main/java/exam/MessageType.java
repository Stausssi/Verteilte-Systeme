package exam;

public enum MessageType {
    // A Message of the type CLUSTER contains a list of IP:Port combinations
    CLUSTER("cluster"),

    // A Message of the type REQUEST contains a request. The type of the request is specified in the payload.
    REQUEST("request"),

    // A Message of the type PORT contains the port of the connection
    PORT("port"),

    // A Message of the type RSA contains the public key information from the Client
    RSA("rsa"),

    // A Message of the type PRIMES contains p and q needed for the RSA decryption
    PRIMES("primes");

    private final String stringType;

    MessageType(String type) {
        this.stringType = type;
    }

    public String toString() {
        return this.stringType;
    }
}
