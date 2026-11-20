package fi.gtrxac.bluewap.ui;

import fi.gtrxac.bluewap.*;

public class ClockThread extends Thread {
    public ClockThread() {}

    public void run() {
        // Update the custom titlebar clock every time the clock's minute changes
        while (AppBase.isRunning()) {
            int remaining = 60001 - (int) (System.currentTimeMillis() % 60000);

            // Sleep in short steps: CLDC 1.0 has no Thread.interrupt(), so the
            // shutdown flag is the only way to stop this thread promptly.
            while (remaining > 0 && AppBase.isRunning()) {
                int step = Math.min(remaining, 250);
                Util.sleep(step);
                remaining -= step;
            }

            if (AppBase.isRunning() && AppCanvas.instance != null) {
                AppCanvas.instance.updateClock();
            }
        }
    }
}
