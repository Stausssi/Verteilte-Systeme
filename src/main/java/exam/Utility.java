package exam;

import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
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

    /**
     * Create the connection key of a given connection
     *
     * @param connection the connection to create the key of
     * @return "address:port"
     */
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

    /**
     * Creates the string representation of a range
     *
     * @param range the range.
     * @return "lower,upper"
     */
    public static String createStringFromRange(int[] range) {
        return range[0] + "," + range[1];
    }

    /**
     * Creates the string representation of a range
     *
     * @param lower the lower border of the range.
     * @param upper the upper border of the range.
     * @return "lower,upper"
     */
    public static String createStringFromRange(int lower, int upper) {
        return createStringFromRange(new int[]{lower, upper});
    }

    /**
     * Converts a given String into a range array.
     *
     * @param range the string representation of the range
     * @return an integer array containing the range
     */
    public static int[] createRangeFromString(String range) {
        return Arrays.stream(range.trim().split(",")).mapToInt(Integer::parseInt).toArray();
    }

    /**
     * Creates a string containing information about the state of a range.
     *
     * @param range the range to set the state of
     * @param state the new state of the range
     * @return "lower,upper:state"
     */
    public static String createPrimeStateString(int[] range, PrimeState state) {
        return createStringFromRange(range) + ":" + state;
    }

    /**
     * Creates a string containing information about the state of a range.
     *
     * @param lower the lower border of the range to set the state of
     * @param upper the upper border of the range to set the state of
     * @param state the new state of the range
     * @return "lower,upper:state"
     */
    public static String createPrimeStateString(int lower, int upper, PrimeState state) {
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

    /**
     * Delays the execution of the given task by the given delay.
     *
     * @param task the task to execute delayed
     * @param delay the delay in ms
     */
    public static void delayExecution(TimerTask task, int delay) {
        Timer delayedTimer = new Timer();
        delayedTimer.schedule(task, delay);
    }

    // -------------------- [Logging] -------------------- //

    /**
     * Get the unique logger name of the given node name.
     *
     * @param nodeName the name of the node to get the logger of
     * @return "exam.logging.nodeName"
     */
    public static String getLoggerName(String nodeName) {
        return "exam.logging." + nodeName;
    }

    /**
     * Initializes the Logger of the given node and attaches a file handler to it.
     *
     * @param name the name of the node to init the logger of
     * @return the initialized logger object
     */
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

    // -------------------- [Argument Parsing] -------------------- //

    /**
     * Parses the command line with the given options.
     * @param options valid options
     * @param args the command line argument
     * @return the parsed options with their values
     */
    public static CommandLine parseArguments(Options options, String[] args) {
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cl = null;

        try {
            cl = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("Invalid argument list", options);
            System.exit(0);
        }

        return cl;
    }

    /**
     * Checks whether the given prime count is valid
     *
     * @param primeCount the prime count
     * @return true, if it is valid
     * @throws IllegalArgumentException if the prime count is invalid
     */
    public static boolean isValidPrimeCount(int primeCount) throws IllegalArgumentException {
        if (primeCount == 100 || primeCount == 1_000 || primeCount == 10_000 || primeCount == 100_000) {
            return true;
        } else {
            throw new IllegalArgumentException("Invalid Prime Count!");
        }
    }
}
