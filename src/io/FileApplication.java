package io;

public class FileApplication {
    public static void main(String[] args) {
        FileCounter runnableObject = new FileCounter();

        Thread runner = new Thread(runnableObject);
        runner.setName("Prozess 1");
        runner.start();

        Thread runner_2 = new Thread(runnableObject);
        runner_2.setName("Prozess 2");
        runner_2.start();
    }
}
