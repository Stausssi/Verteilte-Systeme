package exam;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Utility {
    private static final Level logLevel = Level.INFO;

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

    public static String getLoggerName(String nodeName) {
        return "exam.logging." + nodeName;
    }

    public static Logger initializeLogger(String name) {
        Logger logger = Logger.getLogger(getLoggerName(name));
        try {
            String fileName = "logging/" + name + ".log";
            File logFile = new File(fileName);
            Files.createDirectories(Paths.get("logging"));
            if (logFile.createNewFile() || logFile.exists()) {
                logger.addHandler(new FileHandler(fileName));
                logger.fine("Node initialised!");
            }
        } catch (IOException e) {
            logger.warning("The FileHandler couldn't be attached to the logger: " + e);
        }

        logger.setLevel(logLevel);

        return logger;
    }
}
