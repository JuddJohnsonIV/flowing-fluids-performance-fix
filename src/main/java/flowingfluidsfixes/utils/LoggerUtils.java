package flowingfluidsfixes.utils;

import org.apache.logging.log4j.Logger;

/**
 * Utility class for logging.
 */
@SuppressWarnings("all")
public class LoggerUtils {
    /**
     * Logs a debug message.
     * 
     * @param logger  the logger to use
     * @param message the message to log
     * @param params  the parameters to use in the message
     */
    public static void debug(Logger logger, String message, Object... params) {
        logger.debug(message, params);
    }

    /**
     * Logs an info message.
     * 
     * @param logger  the logger to use
     * @param message the message to log
     * @param params  the parameters to use in the message
     */
    public static void info(Logger logger, String message, Object... params) {
        logger.info(message, params);
    }

    /**
     * Logs a warning message.
     * 
     * @param logger  the logger to use
     * @param message the message to log
     * @param params  the parameters to use in the message
     */
    public static void warn(Logger logger, String message, Object... params) {
        logger.warn(message, params);
    }

    /**
     * Logs an error message.
     * 
     * @param logger  the logger to use
     * @param message the message to log
     * @param params  the parameters to use in the message
     */
    public static void error(Logger logger, String message, Object... params) {
        logger.error(message, params);
    }

    /**
     * Logs a message.
     * 
     * @param message the message to log
     */
    public static void log(String message) {
        System.out.println(message);
    }

    /**
     * Logs an error message.
     * 
     * @param message the message to log
     * @param args    additional arguments or exceptions
     */
    public static void logError(String message, Object... args) {
        if (args.length > 0) {
            StringBuilder formattedMessage = new StringBuilder("[ERROR] " + message);
            for (Object arg : args) {
                formattedMessage.append(" ").append(arg.toString());
            }
            System.err.println(formattedMessage.toString());
        } else {
            System.err.println("[ERROR] " + message);
        }
    }

    /**
     * Logs a debug message.
     * 
     * @param message the message to log
     * @param args    the arguments to use in the message
     */
    public static void logDebug(String message, Object... args) {
        if (args.length > 0) {
            System.out.println("[DEBUG] " + String.format(message, args));
        } else {
            System.out.println("[DEBUG] " + message);
        }
    }

    /**
     * Logs an info message.
     * 
     * @param message the message to log
     * @param args    the arguments to use in the message
     */
    public static void logInfo(String message, Object... args) {
        if (args.length > 0) {
            System.out.println("[INFO] " + formatMessage(message, args));
        } else {
            System.out.println("[INFO] " + message);
        }
    }

    /**
     * Logs a debug message with mod ID prefix.
     * 
     * @param modId   the mod ID for context
     * @param message the message to log
     * @param args    the arguments to use in the message
     */
    public static void logDebug(String modId, String message, Object... args) {
        String formattedMessage = formatMessage(message, args);
        System.out.println("[DEBUG] [" + modId + "] " + formattedMessage);
    }

    /**
     * Logs an info message with mod ID prefix.
     * 
     * @param modId   the mod ID for context
     * @param message the message to log
     * @param args    the arguments to use in the message
     */
    public static void logInfo(String modId, String message, Object... args) {
        String formattedMessage = formatMessage(message, args);
        System.out.println("[INFO] [" + modId + "] " + formattedMessage);
    }

    /**
     * Logs an error message with mod ID prefix.
     * 
     * @param modId   the mod ID for context
     * @param message the message to log
     * @param args    the arguments to use in the message (can include Throwable)
     */
    public static void logError(String modId, String message, Object... args) {
        String formattedMessage = formatMessage(message, args);
        System.err.println("[ERROR] [" + modId + "] " + formattedMessage);
        for (Object arg : args) {
            if (arg instanceof Throwable) {
                ((Throwable) arg).printStackTrace(System.err);
            }
        }
    }

    /**
     * Formats a message with SLF4J-style {} placeholders.
     * 
     * @param message the message template
     * @param args    the arguments to substitute
     * @return the formatted message
     */
    private static String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int i = 0;
        while (i < message.length()) {
            if (i < message.length() - 1 && message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                if (argIndex < args.length) {
                    result.append(args[argIndex] != null ? args[argIndex].toString() : "null");
                    argIndex++;
                } else {
                    result.append("{}");
                }
                i += 2;
            } else {
                result.append(message.charAt(i));
                i++;
            }
        }
        return result.toString();
    }
}
