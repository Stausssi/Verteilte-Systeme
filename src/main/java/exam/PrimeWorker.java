package exam;

import eu.boxwork.dhbw.examhelpers.rsa.RSAHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

interface WorkerCallback {
    void resultFound(String p, String q);

    void workerFinished(String range);
}

public class PrimeWorker {
    private final String publicKey;
    private final ArrayList<String> primes;
    private final Logger logger;
    private final WorkerCallback callbacks;
    private final RSAHelper rsaHelper = new RSAHelper();

    private String range;
    private boolean isRunning = false;
    private Thread workerThread;
    private WorkerThread worker;

    PrimeWorker(String publicKey, ArrayList<String> primes, WorkerCallback callbacks, Logger logger) {
        this.logger = logger;
        this.publicKey = publicKey;
        this.primes = primes;
        this.callbacks = callbacks;
    }

    private class WorkerThread implements Runnable {
        protected boolean stopWorking = false;

        @Override
        public void run() {
            // Convert string range to array
            int[] rangeArray = Arrays.stream(range.trim().split(",")).mapToInt(Integer::parseInt).toArray();

            // Loop over every prime in the array and combine it with every prime greater than that
            for (int i = rangeArray[0]; i <= rangeArray[1]; ++i) {
                String p = primes.get(i);
                for (String q : primes) {
                    logger.finest("Combining " + p + " with " + q);
                    boolean valid = rsaHelper.isValid(p, q, publicKey);

                    if (valid) {
                        logger.info("PrimeWorker found the result!");
                        callbacks.resultFound(p, q);
                    }

                    if (valid || stopWorking) {
                        i = rangeArray[1];
                        break;
                    }
                }
            }

            workerFinished(!stopWorking);
        }
    }

    public boolean startWorking(String range) {
        if (!isRunning) {
            this.range = range;

            worker = new WorkerThread();
            workerThread = new Thread(worker);
            workerThread.start();
            isRunning = true;

            logger.info("PrimeWorker started working on range " + range);
            return true;
        } else {
            logger.info("PrimeWorker is already working!");
            return false;
        }
    }

    public void stopWorking() {
        if (worker != null) {
            worker.stopWorking = true;
            logger.info("Stopped the prime worker!");
        }
    }

    private void workerFinished(boolean fireCallback) {
        isRunning = false;

        if (fireCallback) {
            logger.info("PrimeWorker finished working!");
            callbacks.workerFinished(range);
        }

        try {
            workerThread.join();
        } catch (InterruptedException e) {
            logger.warning("Exception while joining workerThread: " + e);
        }
    }
}
