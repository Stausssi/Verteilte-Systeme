package exam;

import java.util.TimerTask;

public abstract class NodeTimeoutTask extends TimerTask {
    protected final Connection node;

    public NodeTimeoutTask(Connection c) {
        node = c;
    }
}
