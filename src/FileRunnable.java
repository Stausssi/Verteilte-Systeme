import java.io.File;
import java.io.IOException;
import java.lang.Thread;
import java.lang.Runnable;
import java.util.Random;

public class FileRunnable implements Runnable {

    @Override
    public void run() {
        File testfile = new File("inputOutput.txt");
        Random randomgen = new Random();
        try {

            if (testfile.createNewFile() || testfile.exists()) {

                for (int i = 0; i < 50; i++) {

                    InputOutput.writeToFile(testfile,"Testtext von " + (Thread.currentThread().getName()), true);
                    System.out.println(InputOutput.readFile(testfile));
                    Thread.sleep(randomgen.nextInt(250));
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}