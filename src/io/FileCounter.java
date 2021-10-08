package io;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.Random;

public class FileCounter implements Runnable {
    @Override
    public void run() {
        File testFile = new File("fileCounter.txt");
        try {
            if (testFile.createNewFile() || testFile.exists()) {
                String[] numList;
                Random randomGen = new Random();
                do {
                    FileChannel inputChannel = new RandomAccessFile(testFile, "rws").getChannel();
                    FileLock lock;
                    do {
                        lock = retrieveLock(inputChannel);
                        Thread.sleep(randomGen.nextInt(100));
                    } while (lock == null);
                    System.out.println("lock is here");

                    numList = InputOutput.readFile(testFile).split("\n");
                    System.out.println("file is read");
                    lock.release();
                    InputOutput.writeToFile(testFile, Integer.toString(numList.length + 1), true);

                    inputChannel.close();

                    System.out.println("Going back to sleep!");
                    Thread.sleep(randomGen.nextInt(100));
                } while (numList.length < 100);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private FileLock retrieveLock(FileChannel channel) {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException | IOException e) {
            return null;
        }
    }
}
