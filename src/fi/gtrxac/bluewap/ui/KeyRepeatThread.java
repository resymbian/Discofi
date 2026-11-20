package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.*;

public class KeyRepeatThread extends Thread {
    public static volatile int activeKey;
    public static final KeyRepeatThread instance = new KeyRepeatThread();

    public static void shutdown() {
        activeKey = 0;
        synchronized (instance) {
            instance.notifyAll();
        }
    }

    public void run() {
        while (AppBase.isRunning()) {
            try {
                synchronized (this) {
                    wait(0);
                }
            }
            catch (InterruptedException e) {}

            if (!AppBase.isRunning()) break;

            while (AppBase.isRunning()) {
                long curr = System.currentTimeMillis();
                if (curr >= AppCanvas.beginRepeatTime) break;

                try {
                    Thread.sleep(AppCanvas.beginRepeatTime - curr);
                }
                catch (InterruptedException e) {
                    break;
                }
            }

            while (AppBase.isRunning() && activeKey != 0 &&
                   AppCanvas.instance != null && AppCanvas.instance.isShown()) {
                try {
                    AppCanvas.instance.keyEvent(activeKey);
                    AppBase.repaint();
                    Thread.sleep(50);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}