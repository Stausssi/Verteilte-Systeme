package socket;

import roles.Role;
import roles.Worker;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class WorkerApplication {
    public static void main(String[] args) throws InterruptedException {
        Role[] roles = {Role.COORDINATOR, Role.WORKER, Role.UNKNOWN};
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            Thread workerThread = new Thread(new Worker("Worker" + (i + 1), roles[i]));
            workerThread.start();
            Thread.sleep(1000);
            workers.add(workerThread);
        }

        workers.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
    }
}
