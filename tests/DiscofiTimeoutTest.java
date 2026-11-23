package fi.gtrxac.discord;

import com.gtrxac.discord.HTTPQueue;
import fi.gtrxac.bluewap.http.HTTP;
import fi.gtrxac.bluewap.ui.AppBase;

/**
 * Regression test for the login hang: a stalled HTTP response must be closed
 * by HTTPQueue and reported to the caller instead of blocking forever.
 *
 * Run with ./sdk/timeouttest.sh against tests/fake_discord.py.
 */
public class DiscofiTimeoutTest extends App {
    private boolean destroyed;

    public void startApp() {
        super.startApp();

        boolean failed = false;
        HTTPQueue.setTimeoutMs(1500);

        String url = AppBase.instance.getAppProperty("FakeApiUrl") + "/hang";
        long start = System.currentTimeMillis();

        try {
            HTTP h = HTTP.createRequest(url);
            h.getResponseCode();
            h.getResponseString();
            System.out.println("FAIL: stalled request returned normally");
            failed = true;
        }
        catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("stalled request interrupted after " + elapsed + " ms: " + e);

            if (elapsed > 8000) {
                System.out.println("FAIL: timeout took too long (" + elapsed + " ms)");
                failed = true;
            }
            else {
                System.out.println("PASS: stalled request timed out");
            }
        }

        if (HTTPQueue.size() == 0) {
            System.out.println("PASS: queue slot released");
        }
        else {
            System.out.println("FAIL: queue slot leaked");
            failed = true;
        }

        System.out.println(failed ? "TIMEOUT TEST FAILED" : "TIMEOUT TEST PASSED");
        destroyApp(false);
    }

    public void destroyApp(boolean unconditional) {
        if (destroyed) return;
        destroyed = true;
        super.destroyApp(unconditional);
    }
}
