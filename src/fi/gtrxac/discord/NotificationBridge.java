package fi.gtrxac.discord;

import java.io.*;
import javax.microedition.io.*;

/**
 * Sends notification text to the optional native Symbian companion
 * (native/e51/DiscofiNotify). The E51's Java runtime has neither Nokia UI soft
 * notifications nor Pigler, so a small SISX agent is needed to show a system
 * note. The agent listens on loopback and answers with one byte (1 = shown).
 *
 * No native code is required for the app to work; when the agent is not
 * installed, post() simply returns false and the in-app unread dots keep
 * working as before.
 */
public class NotificationBridge {
    private static final String URL = "socket://127.0.0.1:4711";

    private static void closeQuietly(Object stream) {
        if (stream == null) return;

        try {
            if (stream instanceof InputStream) ((InputStream) stream).close();
            else if (stream instanceof OutputStream) ((OutputStream) stream).close();
        }
        catch (Exception ignored) {}
    }

    /** @return true when the native agent accepted the notification. */
    public static boolean post(String title, String text) {
        if (title == null) title = "Discofi";
        if (text == null) text = "";

        SocketConnection connection = null;
        DataInputStream in = null;
        DataOutputStream out = null;

        try {
            connection = (SocketConnection) Connector.open(URL);
            out = new DataOutputStream(connection.openOutputStream());
            out.writeUTF(title);
            out.writeUTF(text);
            out.flush();

            in = new DataInputStream(connection.openInputStream());
            return in.read() == 1;
        }
        catch (Exception e) {
            e.printStackTrace();
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
}
