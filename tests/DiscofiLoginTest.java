package fi.gtrxac.discord;

import com.gtrxac.discord.HTTPQueue;
import fi.gtrxac.bluewap.Util;
import fi.gtrxac.bluewap.ui.AppBase;
import fi.gtrxac.bluewap.ui.Dialog;
import fi.gtrxac.bluewap.ui.ListScreen;
import fi.gtrxac.bluewap.ui.Screen;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;

/**
 * End-to-end reproduction of the reported login freeze.
 *
 * Drives the real password-login HTTPThread against tests/fake_discord.py.
 * In normal mode it checks that login finishes and the account list is still
 * usable. With FAKE_DISCORD_MODE=hang_login the login response stalls; the
 * error dialog must appear and be dismissable instead of the app hanging.
 *
 * Run with ./sdk/logintest.sh.
 */
public class DiscofiLoginTest extends App {
    private boolean destroyed;
    private boolean expectHang;
    private int failures;

    private void check(String name, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + name);
        if (!ok) failures++;
    }

    private Screen waitFor(String what, int screenType, int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < end) {
            Screen current = AppBase.getCurrentScreen();

            if (screenType == 0 && current instanceof AccountListScreen) return current;
            if (screenType == 1 && current instanceof MainScreen) return current;
            if (screenType == 2 && current instanceof Dialog) return current;

            Util.sleep(100);
        }

        System.out.println("FAIL: timed out waiting for " + what +
            " (current screen: " + AppBase.getCurrentScreen() + ")");
        failures++;
        return null;
    }

    public void startApp() {
        super.startApp();

        HTTPQueue.setTimeoutMs(1500);
        expectHang = "hang_login".equals(AppBase.instance.getAppProperty("FakeDiscordMode"));

        String apiUrl = AppBase.instance.getAppProperty("FakeApiUrl");
        Account acc = new Account();
        acc.connectionMode = 2; // CONNECTION_MODE_PROXY (inlined by ProGuard)
        acc.apiUrl = apiUrl;
        acc.email = "tester@example.com";
        acc.password = "secret";

        Accounts.list.removeAllElements();
        Accounts.setActive(-1);
        App.pendingAccount = acc;
        App.pushScreen(new LoginScreen());

        HTTPThread login = new HTTPThread(0); // ACTION_PASSWORD_LOGIN (inlined)
        login.email = acc.email;
        login.password = acc.password;
        login.start();

        if (expectHang) {
            Screen dialog = waitFor("login error dialog", 2, 10000);
            check("error dialog replaces loading screen", dialog instanceof Dialog);

            if (dialog != null) {
                // The softkeys must work again: dismissing the dialog has to
                // reveal the login screen underneath.
                dialog.commandAction(new Command("OK", Command.BACK, 0), null);
                check("dialog can be dismissed",
                    AppBase.getCurrentScreen() instanceof LoginScreen);
            }
        }
        else {
            Screen accounts = waitFor("account list after login", 0, 10000);
            check("login completed", accounts instanceof AccountListScreen);
            check("account saved", Accounts.list.size() == 1 &&
                acc.token != null && acc.token.length() > 0);

            if (accounts instanceof ListScreen) {
                ((ListScreen) accounts).setHighlightedIndex(1);
                accounts.keyEvent(0, Canvas.FIRE);
                check("account list responds after login",
                    waitFor("main screen", 1, 10000) instanceof MainScreen);
            }
        }

        System.out.println(failures == 0 ? "LOGIN TEST PASSED" : "LOGIN TEST FAILED: " + failures);
        destroyApp(false);
    }

    public void destroyApp(boolean unconditional) {
        if (destroyed) return;
        destroyed = true;
        super.destroyApp(unconditional);
    }
}
