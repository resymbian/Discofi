package com.gtrxac.discord;

import fi.gtrxac.bluewap.*;
import fi.gtrxac.bluewap.ui.AppBase;
import java.io.*;
import java.util.*;
import javax.microedition.io.*;

/**
 * Keeps track of open HTTP connections and closes them when a request takes
 * too long. Closing the connection is the only portable way to interrupt a
 * blocking Connector.open()/getResponseCode()/read() in MIDP.
 */
public class HTTPQueue implements Runnable {
    // Time before a connection will be closed for timeout.
    private static final int DEFAULT_TIMEOUT_MS = 60000;

    // Max amount of possible HTTP connections
    // Nokia S40 has a limit of 8 HTTP connections, if they run out, you get "No response entries available"
    public static int maxSlots = 4;

    private static final Vector queueItems = new Vector(maxSlots);
    private static volatile boolean running = true;
    private static volatile int timeoutMs = DEFAULT_TIMEOUT_MS;

    private final long timestamp;
    private volatile boolean expired;

    public volatile InputStream is;
    public volatile OutputStream os;
    public volatile HttpConnection hc;

    static {
        new Thread(new HTTPQueue()).start();
    }

    private HTTPQueue() {
        timestamp = System.currentTimeMillis();
    }

    /** Overridable by tests; the default is one minute. */
    public static void setTimeoutMs(int ms) {
        if (ms > 0) timeoutMs = ms;
    }

    public static int getTimeoutMs() {
        return timeoutMs;
    }

    /** Number of requests currently being tracked (used by the tests). */
    public static int size() {
        synchronized (queueItems) {
            return queueItems.size();
        }
    }

    public static synchronized HTTPQueue newQueueItem() {
        // wait for available slot
        while (queueItems.size() >= maxSlots) {
            Util.sleep(100);
        }

        HTTPQueue result = new HTTPQueue();
        synchronized (queueItems) {
            queueItems.addElement(result);
        }
        return result;
    }

    public void run() {
        while (running && AppBase.isRunning()) {
            long now = System.currentTimeMillis();
            int limit = timeoutMs;

            synchronized (queueItems) {
                for (int i = 0; i < queueItems.size(); i++) {
                    HTTPQueue item = (HTTPQueue) queueItems.elementAt(i);

                    // Count from when the request started, not from now: the
                    // old comparison against now + TIMEOUT could never be true.
                    if (!item.expired && now - item.timestamp >= limit) {
                        item.expire();
                    }
                }
            }

            Util.sleep(1000);
        }
    }

    private void expire() {
        expired = true;
        // A request can still be inside Connector.open() at this point. The
        // setters below close the transport as soon as it becomes available.
        close();
    }

    public boolean isExpired() {
        return expired;
    }

    public void setConnection(HttpConnection connection) {
        hc = connection;
        if (expired) closeQuietly(connection);
    }

    public void setInputStream(InputStream stream) {
        is = stream;
        if (expired) closeQuietly(stream);
    }

    public void setOutputStream(OutputStream stream) {
        os = stream;
        if (expired) closeQuietly(stream);
    }

    public static void shutdown() {
        running = false;
    }

    private static void closeQuietly(Object closeable) {
        try {
            if (closeable instanceof InputStream) ((InputStream) closeable).close();
            else if (closeable instanceof OutputStream) ((OutputStream) closeable).close();
            else if (closeable instanceof HttpConnection) ((HttpConnection) closeable).close();
        }
        catch (Exception e) {}
    }

    private void close() {
        closeQuietly(is);
        closeQuietly(os);
        closeQuietly(hc);
    }

    /** Request has unblocked: release its slot even if it timed out. */
    public void finished() {
        close();
        synchronized (queueItems) {
            queueItems.removeElement(this);
        }
    }
}
