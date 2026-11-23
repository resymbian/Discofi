package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.client.*;
import com.gtrxac.discord.HTTPQueue;
import fi.gtrxac.tinytls.TLSTest;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import java.util.*;

public class App extends AppBase {
    // Account that is currently being created (or edited)
    public static Account pendingAccount;

    // State for the password login flow (fingerprint -> ticket/login instance ID -> TOTP)
    public static String pendingTicket;
    public static String pendingFingerprint;
    public static String pendingLoginInstanceID;

    public void init() {
        // TLSTest.test();
        Settings.load();
        UnreadStore.init();
        NotificationPoller.settingsChanged();

        Account acc = Accounts.getActive();

        if (acc == null) {
            pushScreen(new AccountListScreen());
        }
//#ifndef NO_BLUETOOTH
        else if (acc.needsTetherDevice()) {
            // Add an accountlist screen to the background so if the user
            // cancels BT device selection, they can go back to change accounts/settings
            pushScreen(new AccountListScreen());
            pushScreen(new BluetoothDeviceScreen());
        }
//#endif
        else {
            pushScreen(new MainScreen());
        }
    }

    public void pauseApp() {
        UnreadStore.saveIfDirty();
        Settings.save();
    }

    public void destroyApp(boolean unconditional) {
        // Save before the framework stops the UI threads and calls
        // notifyDestroyed(). Never let a save failure block the exit.
        try {
            NotificationPoller.stop();
        }
        catch (Throwable t) {}

        try {
            UnreadStore.saveIfDirty();
        }
        catch (Throwable t) {}

        try {
            Settings.save();
        }
        catch (Throwable t) {}

        try {
            EmojiManager.shutdown();
        }
        catch (Throwable t) {}

        try {
            IconManager.shutdown();
        }
        catch (Throwable t) {}

        try {
            HTTPQueue.shutdown();
        }
        catch (Throwable t) {}

        super.destroyApp(unconditional);
    }

    public static void loginCompleted(String token) {
        pendingAccount.token = token;

        // Add account if it doesn't already exist.
        // It will already exist if we were editing a previously created account.
        if (!Accounts.list.contains(pendingAccount)) {
            Accounts.list.addElement(pendingAccount);
        }

        Settings.save();
        pendingAccount = null;
        NotificationPoller.settingsChanged();
        replaceAllScreens(new AccountListScreen());
    }
}