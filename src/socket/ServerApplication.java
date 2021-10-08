package socket;

public class ServerApplication {
    public static void main(String[] args) throws InterruptedException {
        Thread ServerThread = new Thread(new Server());
        Thread ClientThread = new Thread(new Client());
        Thread ClientThread2 = new Thread(new Client());
        ServerThread.start();
        ClientThread.start();
        ClientThread2.start();

        ServerThread.join();
        ClientThread.join();
        ClientThread2.join();
    }

}
