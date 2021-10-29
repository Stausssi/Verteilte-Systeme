package tasks.socket;

public class ServerApplication {
    public static void main(String[] args) throws InterruptedException {
        Thread serverThread = new Thread(new Server());
        serverThread.start();
        serverThread.join();
    }
}
