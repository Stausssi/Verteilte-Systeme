package exam;

import java.util.TimerTask;

/**
 * This abstract class enables a Connection object to be passed to a TimerTask
 */
public abstract class NodeTimeoutTask extends TimerTask {
    protected final Connection node;

    public NodeTimeoutTask(Connection c) {
        node = c;
    }
}
