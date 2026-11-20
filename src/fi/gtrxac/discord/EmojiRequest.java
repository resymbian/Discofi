package fi.gtrxac.discord;

import java.util.Vector;

/** A pending custom emoji download, shared by all messages that use it. */
public class EmojiRequest {
    public final String id;
    public final Vector callbacks = new Vector();
    public boolean started;

    public EmojiRequest(String id) {
        this.id = id;
    }
}
