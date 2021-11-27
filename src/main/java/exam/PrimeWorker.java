package exam;

import eu.boxwork.dhbw.examhelpers.rsa.RSAHelper;

import java.util.ArrayList;
import java.util.Arrays;

interface WorkerCallback {
    void resultFound(String p, String q);
    void workerFinished(String range);
}


public class PrimeWorker implements Runnable {
    private final String range;
    private final String publicKey;
    private final String[] primes;
    WorkerCallback callbacks;
    private final RSAHelper rsaHelper = new RSAHelper();

    PrimeWorker(String range, String publicKey, ArrayList<String> primes, WorkerCallback callbacks) {
//        System.out.println("Initialised Prime Worker with range " + range + " and PublicKey " + publicKey);
        this.range = range;
        this.publicKey = publicKey;
        this.primes = primes.toArray(new String[0]);
        this.callbacks = callbacks;
    }

    @Override
    public void run() {
        int[] rangeArray = Arrays.stream(range.trim().split(",")).mapToInt(Integer::parseInt).toArray();
        for (int i = rangeArray[0]; i < rangeArray[1]; ++i) {
            String p = primes[i];
            for (String q : primes) {
//                System.out.println("Combining " + p + " with " + q);
                if (rsaHelper.isValid(p, q, publicKey)) {
                    callbacks.resultFound(p, q);
                    i = rangeArray[1];
                    break;
                }
            }
        }

        callbacks.workerFinished(range);
    }
}
