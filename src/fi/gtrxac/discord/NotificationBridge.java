package fi.gtrxac.discord;

import java.io.*;
import javax.microedition.io.*;

/**
 * Sends notification text to the optional native Symbian companion
 * (resymbian/NotiHelper). The E51's Java runtime has neither Nokia UI soft
 * notifications nor Pigler, so a small SISX agent is needed to show a system
 * note. The agent listens on loopback and answers with one byte (1 = shown).
 *
 * No native code is required for the app to work; when the agent is not
 * installed, post() simply returns false and the in-app unread dots keep
 * working as before.
 */
public class NotificationBridge {
    private static final String PRIMARY_URL = "socket://127.0.0.1:4711";

    /**
     * Some S60 stacks route a literal 127.0.0.1 through the selected IAP.
     * NotiHelper binds all local interfaces and accepts the phone's own IAP
     * address, so "localhost" is a safe second attempt for that route.
     */
    private static final String[] URLS = {
        PRIMARY_URL, "socket://localhost:4711"
    };

    // NotiHelper accepts 2048 bytes per modified-UTF-8 field. Leave room for
    // the ellipsis and keep the two fields comfortably below the wire cap.
    private static final int MAX_TITLE_BYTES = 256;
    private static final int MAX_TEXT_BYTES = 1536;

    /** Set on failure so Settings can say why, not just "no agent". */
    public static String lastError;

    private static void closeQuietly(Object stream) {
        if (stream == null) return;

        try {
            if (stream instanceof InputStream) ((InputStream) stream).close();
            else if (stream instanceof OutputStream) ((OutputStream) stream).close();
        }
        catch (Exception ignored) {}
    }

    /** @return true when the native agent accepted the notification. */
    public static synchronized boolean post(String title, String text) {
        if (title == null) title = "Discofi";
        if (text == null) text = "";
        lastError = null;

        String reason = null;
        for (int i = 0; i < URLS.length; i++) {
            boolean ok = postTo(URLS[i], title, text);
            if (ok) return true;
            // A permission failure repeats on every alias; a note refused by the
            // bridge means the connection succeeded, so do not try localhost.
            if (lastError != null && (lastError.indexOf("SecurityException") >= 0
                    || lastError.indexOf("refused") >= 0)) {
                break;
            }
            reason = lastError;
        }
        if (lastError == null) {
            lastError = reason == null ? "the bridge refused the note" : reason;
        }
        return false;
    }

    private static boolean postTo(String url, String title, String text) {
        SocketConnection connection = null;
        DataInputStream in = null;
        DataOutputStream out = null;
        lastError = null;

        try {
            connection = (SocketConnection) Connector.open(url);
            out = new DataOutputStream(connection.openOutputStream());
            out.writeUTF(trimUtf(title, MAX_TITLE_BYTES));
            out.writeUTF(trimUtf(text, MAX_TEXT_BYTES));
            out.flush();

            in = new DataInputStream(connection.openInputStream());
            if (in.read() != 1) {
                lastError = "the bridge refused the note";
                return false;
            }
            return true;
        }
        catch (Throwable t) {
            // A SecurityException here means the JAD/manifest did not declare
            // Connector.socket, not that NotiHelper is missing. Keep the exact
            // reason for the Settings dialog and for bug reports.
            lastError = t.toString();
            return false;
        }
        finally {
            closeQuietly(in);
            closeQuietly(out);

            if (connection != null) {
                try {
                    connection.close();
                }
                catch (Exception ignored) {}
            }
        }
    }

    private static int utfBytes(char c) {
        return (c >= 1 && c < 0x80) ? 1 : (c < 0x800 ? 2 : 3);
    }

    /** Trim by encoded bytes: writeUTF counts bytes, not characters. */
    private static String trimUtf(String value, int maxBytes) {
        if (value == null) return "";
        String single = value.replace('\n', ' ').replace('\r', ' ');
        int budget = maxBytes - 3;
        int used = 0;
        for (int i = 0; i < single.length(); i++) {
            used += utfBytes(single.charAt(i));
            if (used > budget) {
                return single.substring(0, i) + "...";
            }
        }
        return single;
    }
}
