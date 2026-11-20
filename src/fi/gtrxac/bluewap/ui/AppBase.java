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
    private static Stack screens = new Stack();

    private static void screenChanged() {
        AppCanvas canvas = AppCanvas.instance;
        if (canvas == null) return;

        canvas.updateCommands();
        canvas.updateTitle();
        repaint();
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
        screens.push(s);
        screenChanged();
    }

    /**
     * Close the screen from the top of the screen stack and go back to the previous screen. 
     */
    public static void popScreen() {
        screens.pop();
        screenChanged();
    }

    /**
     * Replace the currently shown screen with another screen.
     */
    public static void replaceScreen(Screen s) {
        screens.pop();
        pushScreen(s);
    }

    /**
     * Close all screens from the screen stack and show a new screen.
     */
    public static void replaceAllScreens(Screen s) {
        screens.setSize(0);
        pushScreen(s);
    }

    /**
     * Get the currently shown screen.
     */
    public static Screen getCurrentScreen() {
        if (screens.empty()) return null;
        return (Screen) screens.peek();
    }

    /**
     * Get the screen that is below the currently shown screen.
     */
    public static Screen getPreviousScreen() {
        if (screens.size() <= 1) return null;
        return (Screen) screens.elementAt(screens.size() - 2);
    }

    public static void recalcAllScreens() {
        for (int i = 0; i < screens.size(); i++) {
            Screen s = (Screen) screens.elementAt(i);
            s.needRecalc();
        }
    }

    public static void repaint() {
        if (AppCanvas.instance != null) AppCanvas.instance.repaint();
    }
}
