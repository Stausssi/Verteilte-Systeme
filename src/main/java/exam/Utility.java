package exam;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Utility {
    private static final Level logLevel = Level.INFO;
    private static final boolean logToFile = false;

    // -------------------- [Message helpers] -------------------- //

    /**
     * Creates a Message object with the given params
     *
     * @param receiver the name of the receiver of the message
     * @param type     the type of the message
     * @param payload  the payload of the message
     * @return the Message object
     */
    public static Message createMessage(String receiver, MessageType type, Object payload) {
        Message msg = new Message();
        msg.setReceiver(receiver);
        msg.setMessageType(type);
        msg.setPayload(payload);

        return msg;
    }

    // -------------------- [Connection helpers] -------------------- //

    /**
     * Creates a connection key consisting of IP and port
     *
     * @param address The address of the connection
     * @param port    The port of the connection
     * @return "address:port"
     */
    public static String createConnectionKey(InetAddress address, int port) {
        return removeHostFromAddress(address) + ":" + port;
    }

    public static String createConnectionKey(Connection connection) {
        return createConnectionKey(connection.getAddress(), connection.getPort());
    }

    /**
     * Removes the host name from the string representation of an InetAddress.
     *
     * @param address The address
     * @return A String containing the IP-Address of the InetAddress.
     */
    public static String removeHostFromAddress(InetAddress address) {
        return address.toString().split("/")[1];
    }

    // -------------------- [Primes helpers] -------------------- //

    public static String createRangeString(int[] range) {
        return range[0] + "," + range[1];
    }

    public static String createRangeString(int lower, int upper) {
        return createRangeString(new int[]{lower, upper});
    }

    public static String createPrimeStateString(int[] range, Index state) {
        return createRangeString(range) + ":" + state;
    }

    public static String createPrimeStateString(int lower, int upper, Index state) {
        return createPrimeStateString(new int[]{lower, upper}, state);
    }

    // -------------------- [Timer related] -------------------- //

    /**
     * Stops a given timer.
     *
     * @param timer the timer to stop
     */
    public static void stopTimer(Timer timer) {
        if (timer != null) {
            timer.cancel();
            timer.purge();
        }
    }

    /**
     * Restarts a given timer with the given task and returns the timer.
     *
     * @param timer The timer to restart
     * @param task  The task to run
     * @param delay The delay before running the task
     * @return the new timer
     */
    public static Timer restartTimer(Timer timer, TimerTask task, int delay) {
        stopTimer(timer);

        timer = new Timer();
        timer.schedule(task, delay);

        return timer;
    }

    /**
     * Restarts a given timer with the given task and returns the timer.
     *
     * @param timer  The timer to restart
     * @param task   The task to run
     * @param delay  The delay before running the task
     * @param period The period to repeat the task with
     * @return the new timer
     */
    public static Timer restartTimer(Timer timer, TimerTask task, int delay, int period) {
        stopTimer(timer);

        timer = new Timer();
        timer.schedule(task, delay, period);

        return timer;
    }

    // -------------------- [Logging] -------------------- //

    public static String getLoggerName(String nodeName) {
        return "exam.logging." + nodeName;
    }

    public static Logger initializeLogger(String name) {
        Logger logger = Logger.getLogger(getLoggerName(name));
        logger.setLevel(logLevel);

        if (logToFile) {
            try {
                String fileName = "logging/" + name + ".log";

                File logFile = new File(fileName);
                Files.createDirectories(Paths.get("logging"));

                if (logFile.createNewFile() || logFile.exists()) {
                    logger.addHandler(new FileHandler(fileName));
                    logger.fine("FileHandler initialised!");
                }
            } catch (IOException e) {
                logger.warning("The FileHandler couldn't be attached to the logger: " + e);
            }
        }

        return logger;
    }
}
