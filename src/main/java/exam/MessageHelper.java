package exam;

import tasks.messages.Message;

/**
 * This class contains helper functions to deal with Messages.
 */
public abstract class MessageHelper {

    /**
     * This method checks if the given message is a request.
     *
     * @param msg The message to check
     * @return true, if the message is a request.
     */
    public static boolean isRequest(Message msg) {
        return "request".equalsIgnoreCase(msg.getType());
    }

    /**
     * This method checks if the given message is a port request.
     *
     * @param msg The message to check
     * @return true, if the message is a port request.
     */
    public static boolean isPortRequest(Message msg) {
        return msg.getPayload() instanceof String &&
                MessageHelper.isRequest(msg) &&
                "port".equalsIgnoreCase((String) msg.getPayload());
    }
}
