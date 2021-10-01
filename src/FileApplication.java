

public class FileApplication {
    public static void main(String [] args) {

            FileRunnable runnableObject = new FileRunnable();

            Thread runner = new Thread(runnableObject);
            Thread runner_2 = new Thread(runnableObject);
            runner.setName("Prozess 1");
            runner_2.setName("Prozess 2");
            runner.start();
            runner_2.start();



    }
}
