package exam;

import java.util.Timer;
import java.util.TimerTask;

public final class Utility {
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
}
