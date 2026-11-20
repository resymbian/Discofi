package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import java.util.*;
import javax.microedition.lcdui.Image;

/**
 * Small asynchronous cache for custom guild emoji images (16x16).
 *
 * Custom emoji are only fetched when they actually appear in a message.
 * The cache is deliberately tiny (24 images) so this cannot meaningfully
 * affect the heap budget on a Nokia E51.
 */
public class EmojiManager {
    private static final int CACHE_SIZE = 24;

    private static final Hashtable cache = new Hashtable();
    private static final Hashtable failed = new Hashtable();
    private static final Vector requests = new Vector();

    public static Image get(String id, MessageItem callback) {
        Image image = (Image) cache.get(id);
        if (image != null) return image;
        if (failed.containsKey(id)) return null;

        synchronized (requests) {
            EmojiRequest existing = null;

            for (int i = 0; i < requests.size(); i++) {
                EmojiRequest req = (EmojiRequest) requests.elementAt(i);
                if (req.id.equals(id)) {
                    existing = req;
                    break;
                }
            }

            if (existing == null) {
                existing = new EmojiRequest(id);
                requests.addElement(existing);
            }

            if (callback != null && existing.callbacks.indexOf(callback) == -1) {
                existing.callbacks.addElement(callback);
            }
        }

        return null;
    }

    public static void fetchDone(EmojiRequest req, Image image) {
        if (image != null) {
            if (cache.size() >= CACHE_SIZE) cache.clear();
            cache.put(req.id, image);
        }
        else {
            // Do not hammer the network from the paint loop if an emoji is
            // permanently unavailable (e.g. deleted custom emoji).
            failed.put(req.id, req.id);
        }

        synchronized (requests) {
            requests.removeElement(req);
        }

        for (int i = 0; i < req.callbacks.size(); i++) {
            MessageItem item = (MessageItem) req.callbacks.elementAt(i);
            item.emojiLoaded();
        }
    }

    private EmojiManager() {}

    static {
        Thread worker = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    EmojiRequest req = null;

                    synchronized (requests) {
                        for (int i = 0; i < requests.size(); i++) {
                            EmojiRequest check = (EmojiRequest) requests.elementAt(i);
                            if (!check.started) {
                                req = check;
                                break;
                            }
                        }
                    }

                    if (req == null) {
                        try {
                            Thread.sleep(250);
                        }
                        catch (InterruptedException e) {}
                        continue;
                    }

                    req.started = true;

                    HTTPThread h = new HTTPThread(HTTPThread.ACTION_FETCH_EMOJI);
                    h.emojiRequest = req;
                    h.start();
                }
            }
        });
        worker.start();
    }
}
