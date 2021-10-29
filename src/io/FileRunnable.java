package io;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class FileRunnable implements Runnable {

    @Override
    public void run() {
        File testFile = new File("inputOutput.txt");
        Random randomGen = new Random();
        try {
            if (testFile.createNewFile() || testFile.exists()) {
                for (int i = 0; i < 50; i++) {
                    InputOutput.writeToFile(testFile, "Testtext von " + (Thread.currentThread().getName()), true);
                    System.out.println(InputOutput.readFile(testFile));
                    Thread.sleep(randomGen.nextInt(250));
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}