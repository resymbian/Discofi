package fi.gtrxac.discord.model;

/**
 * One reaction summary attached to a message.
 *
 * Discord returns custom emoji as { id, name } and Unicode emoji as
 * { id: null, name: "😀" }.
 */
public class Reaction {
    public String id;
    public String name;
    public int count;
    public boolean me;

    public Reaction(String id, String name, int count, boolean me) {
        this.id = id;
        this.name = name;
        this.count = count;
        this.me = me;
    }

    public boolean isCustom() {
        return id != null && id.length() > 0;
    }

    /** value used in the reaction API path */
    public String getApiValue() {
        if (isCustom()) return name + ":" + id;
        return name;
    }

    /** readable fallback when no image is available */
    public String getFallbackText() {
        return ":" + name + ":";
    }
}
