package exam;

import eu.boxwork.dhbw.examhelpers.rsa.RSAHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * These callbacks represent events which can be fired by the worker and should be handled in the node class
 */
interface WorkerCallback {
    /**
     * This method is called once the primes of the private key were found.
     *
     * @param p one prime of the private key
     * @param q the other prime of the private key
     */
    void resultFound(String p, String q);

    /**
     * This method is called once the worker finished working on the given range.
     *
     * @param range the range which was worked on and is now closed.
     */
    void workerFinished(String range);

    /**
     * This method is called if the worker catches an exception
     *
     * @param e the exception which was caught
     */
    void onError(Exception e);
}

/**
 * Combines primes and checks whether the make the private key.
 */
public class PrimeWorker {
    private final String publicKey;
    private final ArrayList<String> primes;
    private final Logger logger;
    private WorkerCallback callbacks;
    private final RSAHelper rsaHelper = new RSAHelper();

    private String range;
    private boolean isRunning = false;
    private Thread workerThread;
    private WorkerThread worker;

    /**
     * Creates a prime worker with a public key, list of primes, callbacks and a logger.
     *
     * @param publicKey the public key to find the private key of
     * @param primes the list of primes to combine
     * @param callbacks the callbacks to fire
     * @param logger the logger to log onto
     */
    PrimeWorker(String publicKey, ArrayList<String> primes, WorkerCallback callbacks, Logger logger) {
        this.logger = logger;
        this.publicKey = publicKey;
        this.primes = primes;
        this.callbacks = callbacks;
    }

    /**
     * Asynchronously combines the primes in a given range
     */
    private class WorkerThread implements Runnable {
        protected boolean stopWorking = false;

        @Override
        public void run() {
            // Convert string range to array
            int[] rangeArray = Arrays.stream(range.trim().split(",")).mapToInt(Integer::parseInt).toArray();

            // Loop over every prime in the array and combine it with every prime greater than that
            try {
                for (int i = rangeArray[0]; i <= rangeArray[1]; ++i) {
                    String p = primes.get(i);
                    for (int j = i + 1; j < primes.size(); j++) {
                        String q = primes.get(j);
                        logger.finest("Combining" + p + "with" + q);
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
            } catch (Exception e) {
                callbacks.onError(e);
            }
        }
    }

    /**
     * Tries to start working on a given range
     *
     * @param range the range the worker should work on
     * @return true, if the worker started working on it. False, if the worker is already working
     */
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

    /**
     * Stops the worker.
     */
    public void stopWorking() {
        if (worker != null) {
            worker.stopWorking = true;
            logger.info("Stopped the prime worker!");
        }
    }

    /**
     * Procedure to follow if the asynchronous combination is finished.
     *
     * @param fireCallback whether the node class should be notified of the range being finished
     */
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

    /**
     * Updates the callbacks of the worker
     *
     * @param callbacks the new callbacks to call
     */
    public void updateCallbacks(WorkerCallback callbacks) {
        this.callbacks = callbacks;
    }
}
