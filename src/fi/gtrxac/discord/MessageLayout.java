package fi.gtrxac.discord;

import java.util.*;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import fi.gtrxac.bluewap.ui.Fonts;

/**
 * Lightweight inline layout for message text that contains Unicode emoji,
 * :shortcodes: or custom emoji tags.
 *
 * Messages without emoji never instantiate this class; they keep using the
 * existing Util.wordWrap path. Layout is done once per message and cached in
 * MessageItem, so scrolling does not re-parse or re-measure anything.
 */
public class MessageLayout {
    private static final int CUSTOM_SIZE = 16;

    private static class Run {
        String text;        // plain text run, or null for an emoji
        int emoji;          // Unicode emoji index, or -1
        String customID;    // custom emoji ID, or null
        String customName;  // custom emoji name (for placeholder)
        int width;
    }

    private static class Line {
        Vector runs = new Vector(2);
        int width;
    }

    private final Vector lines = new Vector();
    private final int lineHeight;
    private int height;

    public MessageLayout(String source, int maxWidth) {
        // If custom emoji rendering fails, fall back to readable text.
        String text = source;
        if (text == null) text = "";

        lineHeight = Math.max(Fonts.height, EmojiData.SIZE);

        Vector lineVec = lines;
        Line line = new Line();
        int x = 0;
        int i = 0;
        int length = text.length();

        while (i < length) {
            char c = text.charAt(i);

            if (c == '\n') {
                endLine(lineVec, line);
                line = new Line();
                x = 0;
                i++;
                continue;
            }

            if (c == ' ' || c == '\t') {
                int start = i;
                while (i < length && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;

                String spaces = text.substring(start, i);
                int width = Fonts.plain.stringWidth(spaces);

                if (x == 0) continue;  // never start a line with whitespace

                if (x + width <= maxWidth) {
                    addText(line, spaces, width);
                    x += width;
                }
                else {
                    endLine(lineVec, line);
                    line = new Line();
                    x = 0;
                }
                continue;
            }

            int emoji = parseCustomEmoji(text, i, line, x, maxWidth);
            if (emoji != 0) {
                if (x + EmojiData.SIZE > maxWidth && x > 0) {
                    endLine(lineVec, line);
                    line = new Line();
                    x = 0;
                }

                Run run = new Run();
                run.emoji = -1;
                run.customID = customID;
                run.customName = customName;
                run.width = CUSTOM_SIZE;
                line.runs.addElement(run);
                line.width += CUSTOM_SIZE;
                x += CUSTOM_SIZE;
                i = customEnd;
                continue;
            }

            int match = EmojiData.matchUnicode(text, i);
            int matchLength = 0;
            if (match == -1) {
                match = EmojiData.matchShortcode(text, i);
            }
            if (match != -1) {
                int index = match >> 8;
                matchLength = match & 0xFF;

                if (x + EmojiData.SIZE > maxWidth && x > 0) {
                    endLine(lineVec, line);
                    line = new Line();
                    x = 0;
                }

                Run run = new Run();
                run.text = null;
                run.emoji = index;
                run.customID = null;
                run.customName = null;
                run.width = EmojiData.SIZE;
                line.runs.addElement(run);
                line.width += EmojiData.SIZE;
                x += EmojiData.SIZE;
                i += matchLength;
                continue;
            }

            // Plain text word: stop at whitespace, emoji, or custom emoji tag.
            int start = i;
            while (i < length) {
                char wc = text.charAt(i);
                if (wc == ' ' || wc == '\t' || wc == '\n') break;
                if (wc == '<' && i + 1 < length && text.charAt(i + 1) == ':') break;
                if (EmojiData.matchUnicode(text, i) != -1) break;
                if (wc == ':' && EmojiData.matchShortcode(text, i) != -1) break;
                i++;
            }

            if (i == start) {
                // Unknown tag or lone character; draw it as text.
                String ch = text.substring(i, i + 1);
                int width = Fonts.plain.stringWidth(ch);
                if (x + width > maxWidth && x > 0) {
                    endLine(lineVec, line);
                    line = new Line();
                    x = 0;
                }
                addText(line, ch, width);
                x += width;
                i++;
                continue;
            }

            String word = text.substring(start, i);
            int wordWidth = Fonts.plain.stringWidth(word);

            if (x + wordWidth <= maxWidth) {
                addText(line, word, wordWidth);
                x += wordWidth;
                continue;
            }

            if (x > 0) {
                endLine(lineVec, line);
                line = new Line();
                x = 0;
                i = start;  // retry the same word on a fresh line
                continue;
            }

            // Word is wider than a whole line: split it by characters without
            // breaking surrogate pairs.
            int k = start;
            while (k < i) {
                int charCount = 1;
                char kc = text.charAt(k);
                if (kc >= 0xD800 && kc <= 0xDBFF && k + 1 < i) charCount = 2;

                String piece = text.substring(k, k + charCount);
                int pieceWidth = Fonts.plain.stringWidth(piece);

                if (x + pieceWidth > maxWidth && x > 0) {
                    endLine(lineVec, line);
                    line = new Line();
                    x = 0;
                }

                addText(line, piece, pieceWidth);
                x += pieceWidth;
                k += charCount;
            }
        }

        if (line.runs.size() > 0) {
            endLine(lineVec, line);
        }

        // Preserve a trailing newline as an empty line.
        if (length > 0 && text.charAt(length - 1) == '\n') {
            endLine(lineVec, new Line());
        }

        height = lines.size() * lineHeight;
    }

    // Scratch values for parseCustomEmoji, to avoid allocating a match object.
    private String customID;
    private String customName;
    private int customEnd;

    /**
     * @return 0 = no match, 1 = matched (customID/customName/customEnd set)
     */
    private int parseCustomEmoji(String text, int pos, Line line, int x, int maxWidth) {
        if (pos + 3 >= text.length()) return 0;
        if (text.charAt(pos) != '<' || text.charAt(pos + 1) != ':') return 0;

        int end = text.indexOf('>', pos + 2);
        if (end == -1) return 0;

        String tag = text.substring(pos + 2, end);
        if (tag.startsWith("a:")) tag = tag.substring(2);

        int colon = tag.indexOf(':');
        if (colon <= 0 || colon >= tag.length() - 1) return 0;

        String name = tag.substring(0, colon);
        String id = tag.substring(colon + 1);

        if (!isValidID(id)) return 0;

        customID = id;
        customName = name;
        customEnd = end + 1;
        return 1;
    }

    private static boolean isValidID(String id) {
        if (id.length() < 15 || id.length() > 22) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static void addText(Line line, String text, int width) {
        if (text.length() == 0) return;

        if (line.runs.size() > 0) {
            Run last = (Run) line.runs.lastElement();
            if (last.text != null) {
                last.text += text;
                last.width += width;
                line.width += width;
                return;
            }
        }

        Run run = new Run();
        run.text = text;
        run.emoji = -1;
        run.width = width;
        line.runs.addElement(run);
        line.width += width;
    }

    private static void endLine(Vector lines, Line line) {
        lines.addElement(line);
    }

    public int getHeight() {
        return height;
    }

    public void draw(Graphics g, int startX, int startY, MessageItem item) {
        int y = startY;

        for (int li = 0; li < lines.size(); li++) {
            Line line = (Line) lines.elementAt(li);
            int x = startX;

            for (int ri = 0; ri < line.runs.size(); ri++) {
                Run run = (Run) line.runs.elementAt(ri);

                if (run.text != null) {
                    g.drawString(run.text, x, y + (lineHeight - Fonts.height) / 2, 0);
                    x += run.width;
                }
                else if (run.customID != null) {
                    Image image = EmojiManager.get(run.customID, item);

                    if (image != null) {
                        g.drawImage(image, x, y + (lineHeight - CUSTOM_SIZE) / 2, 0);
                    }
                    else {
                        // Placeholder while the custom emoji is downloading.
                        g.drawRect(x, y + (lineHeight - CUSTOM_SIZE) / 2, CUSTOM_SIZE - 1, CUSTOM_SIZE - 1);
                    }
                    x += CUSTOM_SIZE;
                }
                else {
                    if (!EmojiData.draw(g, run.emoji, x, y + (lineHeight - EmojiData.SIZE) / 2)) {
                        g.drawRect(x, y + (lineHeight - EmojiData.SIZE) / 2, EmojiData.SIZE - 1, EmojiData.SIZE - 1);
                    }
                    x += EmojiData.SIZE;
                }
            }

            y += lineHeight;
        }
    }
}
