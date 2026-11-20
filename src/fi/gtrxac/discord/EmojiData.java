package fi.gtrxac.discord;

import java.io.*;
import java.util.*;
import javax.microedition.lcdui.*;
import javax.microedition.lcdui.game.Sprite;
import fi.gtrxac.bluewap.Util;

/**
 * Bundled Unicode emoji data.
 *
 * The emoji set is stored as a plain text resource (one entry per line:
 * "unicode<TAB>shortcode1,shortcode2,..."). Emoji images are 16x16 cells in
 * 45-cell sprite sheets, exactly like the ones used by Discord J2ME.
 *
 * Memory note: only one sprite sheet is decoded at a time. Individual emoji
 * images are never allocated; they are drawn straight from the sheet with
 * Graphics.drawRegion().
 */
public class EmojiData {
    public static final int SIZE = 16;
    public static final int SHEET_COLUMNS = 45;

    private static boolean initialized;
    private static boolean available;

    private static int count;
    private static String[] unicodeByIndex;
    private static Hashtable nameToIndex;
    private static Hashtable unicodeToIndex;

    private static int loadedSheet = -1;
    private static Image sheetImage;

    /** Cached result of Settings.showEmojis && resources available. */
    public static boolean isEnabled() {
        return Settings.showEmojis && isAvailable();
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    public static int getCount() {
        init();
        return count;
    }

    public static String getUnicode(int index) {
        init();
        if (index < 0 || index >= count) return null;
        return unicodeByIndex[index];
    }

    /** Resolve a shortcode name or a Unicode emoji string to its sheet index. */
    public static int indexOf(String name) {
        init();
        if (!available || name == null) return -1;

        Integer index = (Integer) nameToIndex.get(name);
        if (index == null) index = (Integer) unicodeToIndex.get(name);
        return (index != null) ? index.intValue() : -1;
    }

    private static void init() {
        if (initialized) return;
        initialized = true;

        try {
            String data = readResource("/emoji/emoji.dat");
            if (data == null) {
                available = false;
                return;
            }

            Vector unicode = new Vector();
            nameToIndex = new Hashtable();
            unicodeToIndex = new Hashtable();

            int pos = 0;
            int length = data.length();

            while (pos < length) {
                int tab = data.indexOf('\t', pos);
                if (tab == -1) break;

                int end = data.indexOf('\n', tab + 1);
                if (end == -1) end = length;

                String emoji = data.substring(pos, tab);
                int index = unicode.size();
                unicode.addElement(emoji);

                unicodeToIndex.put(emoji, new Integer(index));

                // Names are separated by commas; map every alias to this entry.
                int nameStart = tab + 1;
                while (nameStart < end) {
                    int comma = data.indexOf(',', nameStart);
                    if (comma == -1 || comma > end) comma = end;

                    String name = data.substring(nameStart, comma);
                    if (name.length() > 0) {
                        nameToIndex.put(name, new Integer(index));
                    }
                    nameStart = comma + 1;
                }

                pos = end + 1;
            }

            count = unicode.size();
            unicodeByIndex = new String[count];
            unicode.copyInto(unicodeByIndex);

            available = (count > 0);
        }
        catch (Throwable e) {
            e.printStackTrace();
            available = false;
        }
    }

    /**
     * Read a resource completely. Kept small and allocation-conscious: the
     * emoji data file is only about 14 kB.
     */
    private static String readResource(String name) throws IOException {
        InputStream in = new Object().getClass().getResourceAsStream(name);
        if (in == null) return null;

        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        byte[] buffer = new byte[1024];

        try {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        finally {
            in.close();
        }

        return Util.bytesToString(out.toByteArray(), "UTF-8");
    }

    /**
     * Check whether a string contains anything that the emoji layout needs to
     * handle: a custom emoji tag, a possible :shortcode: or a surrogate pair.
     * This is intentionally cheap; messages without emoji keep using the old
     * word-wrap path with zero additional work.
     */
    public static boolean hasEmoji(String text) {
        if (text == null) return false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '<' && i + 2 < text.length() && text.charAt(i + 1) == ':') {
                return true;
            }
            if (c == ':' && text.indexOf(':', i + 1) != -1) {
                return true;
            }
            if (c >= 0xD800 && c <= 0xDBFF) {
                return true;
            }
        }
        return false;
    }

    /**
     * Try to match a Unicode emoji at the given position.
     *
     * @return packed (index << 8) | length, or -1 if nothing matched
     */
    public static int matchUnicode(String text, int pos) {
        if (!isEnabled()) return -1;

        char first = text.charAt(pos);
        // Most emoji are surrogate pairs or BMP symbols above U+2000.
        if (first < 0x2000 && !(first >= 0xD800 && first <= 0xDBFF)) {
            return -1;
        }

        int max = Math.min(8, text.length() - pos);

        // Longest match first: flags, ZWJ sequences and skin tones are stored
        // as complete sequences in the data file.
        for (int len = max; len >= 1; len--) {
            String key = text.substring(pos, pos + len);
            Integer index = (Integer) unicodeToIndex.get(key);
            if (index != null) {
                return (index.intValue() << 8) | len;
            }
        }
        return -1;
    }

    /**
     * Try to match a :shortcode: at the given position.
     *
     * @return packed (index << 8) | length, or -1 if nothing matched
     */
    public static int matchShortcode(String text, int pos) {
        if (!isEnabled() || text.charAt(pos) != ':') return -1;

        int end = text.indexOf(':', pos + 1);
        if (end == -1 || end - pos > 40) return -1;

        String name = text.substring(pos + 1, end);
        if (name.length() == 0) return -1;

        Integer index = (Integer) nameToIndex.get(name);
        if (index == null) return -1;

        return (index.intValue() << 8) | (end - pos + 1);
    }

    /**
     * Strip custom emoji tags into readable :name: text. Used when emoji
     * rendering is disabled and in short previews.
     */
    public static String stripCustomTags(String text) {
        if (text == null) return "";

        int start = text.indexOf("<:");
        if (start == -1) return text;

        StringBuffer out = new StringBuffer();
        int pos = 0;

        while (start != -1) {
            out.append(text.substring(pos, start));

            int end = text.indexOf('>', start + 2);
            if (end == -1) break;

            String tag = text.substring(start + 2, end);
            if (tag.startsWith("a:")) tag = tag.substring(2);

            int colon = tag.indexOf(':');
            if (colon != -1) {
                out.append(':').append(tag.substring(0, colon)).append(':');
            }
            else {
                out.append(text.substring(start, end + 1));
            }

            pos = end + 1;
            start = text.indexOf("<:", pos);
        }

        out.append(text.substring(pos));
        return out.toString();
    }

    /**
     * Draw one emoji from the currently loaded sprite sheet.
     * Returns false if the sheet could not be loaded.
     */
    public static boolean draw(Graphics g, int index, int x, int y) {
        init();
        if (!available || index < 0 || index >= count) return false;

        int sheet = index / SHEET_COLUMNS;
        if (sheet != loadedSheet) {
            try {
                sheetImage = Image.createImage("/emoji/emoji" + sheet + ".png");
                loadedSheet = sheet;
            }
            catch (Throwable e) {
                sheetImage = null;
                loadedSheet = -1;
                return false;
            }
        }

        int cell = index % SHEET_COLUMNS;
        try {
            g.drawRegion(
                sheetImage,
                cell * SIZE, 0, SIZE, SIZE,
                Sprite.TRANS_NONE,
                x, y,
                Graphics.TOP | Graphics.LEFT);
            return true;
        }
        catch (Throwable e) {
            return false;
        }
    }

    /** Free the decoded sheet, e.g. when emojis are disabled in settings. */
    public static void unloadSheet() {
        sheetImage = null;
        loadedSheet = -1;
    }
}
