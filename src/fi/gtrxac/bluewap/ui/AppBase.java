package fi.gtrxac.bluewap.ui;

import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.util.*;

public abstract class AppBase extends MIDlet {
    // _________________________________________________________________________
    //
    //  MIDlet lifecycle
    // _________________________________________________________________________
    //
	public static Display disp;
    public static AppBase instance;

    // Worker threads check this flag so destroyApp can actually stop them.
    // A MIDlet that forgets to call notifyDestroyed() is left running in the
    // background by S60 until the phone is rebooted.
    private static volatile boolean running = true;

    private boolean started = false;

    public AppBase() {
        instance = this;
    }

    public static boolean isRunning() {
        return running;
    }

    public void startApp() {
        if (started) return;
        started = true;

        try {
            disp = Display.getDisplay(this);

            // Assign the canvas before anything else can use it. The old
            // static initializer ran while the class was still being set up,
            // and optimized builds could see a null instance.
            AppCanvas.create();

            Fonts.loadFonts(Font.SIZE_SMALL);
            Colors.loadDefaults();

            init();

            disp.setCurrent(AppCanvas.instance);

            // Some JVMs do not schedule an initial paint for setCurrent().
            disp.callSerially(new Runnable() {
                public void run() {
                    try {
                        if (AppCanvas.instance != null) AppCanvas.instance.repaint();
                    }
                    catch (Throwable t) {
                        showFatalError(t);
                    }
                }
            });
        }
        catch (Throwable t) {
            // Never let the JVM show its generic "unknown error" with no way
            // to quit. Show the real cause and an Exit command instead.
            showFatalError(t);
        }
    }

    private void showFatalError(Throwable t) {
        String detail = (t == null) ? "Unknown error" : t.toString();

        try {
            Form form = new Form("Discofi could not start");
            form.append(new javax.microedition.lcdui.StringItem("Error", detail));
            form.addCommand(new Command("Exit", Command.EXIT, 1));
            form.setCommandListener(new CommandListener() {
                public void commandAction(Command c, Displayable d) {
                    AppBase.instance.destroyApp(true);
                }
            });
            disp.setCurrent(form);
        }
        catch (Throwable ignored) {
            // Last resort: at least let the user leave the MIDlet.
            notifyDestroyed();
        }
    }

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {
        running = false;

        try {
            AppCanvas.shutdown();
        }
        catch (Throwable ignored) {}

        try {
            KeyRepeatThread.shutdown();
        }
        catch (Throwable ignored) {}

        // Required by MIDP: without this the suite stays in the background
        // and on S60 can only be removed by rebooting the phone.
        notifyDestroyed();
    }

    // _________________________________________________________________________
    //
    //  Screen management
    // _________________________________________________________________________
    //
    private static final Stack screens = new Stack();

    // Screen stack mutations happen on both the event thread and the network
    // worker threads. The stack itself is guarded here, and LCDUI operations
    // (commands, title) are posted to the event thread instead of being made
    // from whichever thread happened to push a dialog or a loading screen.
    private static final Object UI_LOCK = new Object();
    private static boolean uiUpdatePending;

    private static final Runnable uiUpdateRunnable = new Runnable() {
        public void run() {
            AppCanvas canvas;

            synchronized (UI_LOCK) {
                uiUpdatePending = false;
                canvas = AppCanvas.instance;

                if (canvas != null) {
                    canvas.updateCommands();
                    canvas.updateTitle();
                }
            }

            repaint();
        }
    };

    /** Schedule command/title refresh on the MIDP event thread. */
    static void requestUiUpdate() {
        synchronized (UI_LOCK) {
            if (disp == null || AppCanvas.instance == null || uiUpdatePending) return;
            uiUpdatePending = true;
            try {
                disp.callSerially(uiUpdateRunnable);
            }
            catch (RuntimeException e) {
                uiUpdatePending = false;
            }
        }
    }

    private static void screenChanged() {
        requestUiUpdate();
    }

    // _________________________________________________________________________
    //
    //  Public API
    // _________________________________________________________________________
    //

    /**
     * Implemented by the app to show the first screen of the app.
     */
    public abstract void init();

    /**
     * Add a new screen to the top of the screen stack and show it.
     */
    public static void pushScreen(Screen s) {
        if (s == null) return;

        synchronized (UI_LOCK) {
            screens.push(s);
        }
        screenChanged();
    }

    /**
     * Close the screen from the top of the screen stack and go back to the previous screen.
     */
    public static void popScreen() {
        synchronized (UI_LOCK) {
            if (!screens.empty()) screens.pop();
        }
        screenChanged();
    }

    /**
     * Close a specific screen, even if another screen (for example an error
     * dialog) has been pushed on top of it in the meantime. Loading screens
     * use this so their finally block cannot remove the wrong screen.
     */
    public static void popScreen(Screen s) {
        if (s == null) return;

        boolean removed = false;
        synchronized (UI_LOCK) {
            for (int i = screens.size() - 1; i >= 0; i--) {
                if (screens.elementAt(i) == s) {
                    screens.removeElementAt(i);
                    removed = true;
                    break;
                }
            }
        }

        if (removed) screenChanged();
    }

    /**
     * Replace the currently shown screen with another screen.
     */
    public static void replaceScreen(Screen s) {
        if (s == null) return;

        synchronized (UI_LOCK) {
            if (!screens.empty()) screens.pop();
            screens.push(s);
        }
        screenChanged();
    }

    /**
     * Close all screens from the screen stack and show a new screen.
     */
    public static void replaceAllScreens(Screen s) {
        if (s == null) return;

        synchronized (UI_LOCK) {
            screens.setSize(0);
            screens.push(s);
        }
        screenChanged();
    }

    public static Screen getCurrentScreen() {
        synchronized (UI_LOCK) {
            if (screens.empty()) return null;
            return (Screen) screens.peek();
        }
    }

    public static Screen getPreviousScreen() {
        synchronized (UI_LOCK) {
            if (screens.size() <= 1) return null;
            return (Screen) screens.elementAt(screens.size() - 2);
        }
    }

    public static void recalcAllScreens() {
        Screen[] all;

        synchronized (UI_LOCK) {
            all = new Screen[screens.size()];
            for (int i = 0; i < all.length; i++) {
                all[i] = (Screen) screens.elementAt(i);
            }
        }

        for (int i = 0; i < all.length; i++) {
            all[i].needRecalc();
        }
    }

    public static void repaint() {
        if (AppCanvas.instance != null) AppCanvas.instance.repaint();
    }
}
