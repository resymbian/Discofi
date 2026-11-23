package fi.gtrxac.discord;

import fi.gtrxac.bluewap.Util;
import java.io.*;
import javax.microedition.io.*;

/**
 * Checks that NotificationBridge speaks the protocol expected by the native
 * S60 agent: two Java modified-UTF strings followed by a one-byte answer.
 *
 * Run with ./sdk/notificationtest.sh.
 */
public class DiscofiNotificationTest extends App {
    private boolean destroyed;
    private String receivedTitle;
    private String receivedText;

    class FakeAgent implements Runnable {
        public void run() {
            ServerSocketConnection server = null;
            SocketConnection connection = null;

            try {
                server = (ServerSocketConnection) Connector.open("socket://:4711");
                connection = (SocketConnection) server.acceptAndOpen();

                DataInputStream in = new DataInputStream(connection.openInputStream());
                receivedTitle = in.readUTF();
                receivedText = in.readUTF();

                DataOutputStream out = new DataOutputStream(connection.openOutputStream());
                out.writeByte(1);
                out.flush();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                try { if (connection != null) connection.close(); } catch (Exception e) {}
                try { if (server != null) server.close(); } catch (Exception e) {}
            }
        }
    }

    public void startApp() {
        super.startApp();

        boolean failed = false;
        new Thread(new FakeAgent()).start();
        Util.sleep(300);

        boolean accepted = NotificationBridge.post("Discofi", "Mensaje nuevo: ñ");
        System.out.println((accepted ? "PASS: " : "FAIL: ") + "agent accepted notification");
        if (!accepted) failed = true;

        Util.sleep(300);

        boolean textOK = "Discofi".equals(receivedTitle) &&
            "Mensaje nuevo: ñ".equals(receivedText);

        System.out.println((textOK ? "PASS: " : "FAIL: ") +
            "UTF strings decoded (title=" + receivedTitle + ", text=" + receivedText + ")");
        if (!textOK) failed = true;

        System.out.println(failed ? "NOTIFICATION TEST FAILED" : "NOTIFICATION TEST PASSED");
        destroyApp(false);
    }

    public void destroyApp(boolean unconditional) {
        if (destroyed) return;
        destroyed = true;
        super.destroyApp(unconditional);
    }
}
