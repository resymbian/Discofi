package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.ui.Screen;
import javax.microedition.lcdui.*;

/**
 * Compact emoji picker that draws directly from the bundled sprite sheets.
 *
 * No per-emoji Image objects are created and no network access is needed.
 */
public class EmojiPickerScreen extends Screen implements CommandListener {
    private static final int CMD_BACK = 1;
    private static final int CMD_SELECT = 2;

    private final EmojiPickerListener listener;
    private int columns;
    private int rows;
    private int cell;
    private int scroll;
    private int selected;

    public EmojiPickerScreen(EmojiPickerListener listener) {
        super(2);
        this.listener = listener;

        setTitle("Emoji");
        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Select", Command.OK, CMD_SELECT));
    }

    public void recalc() {
        cell = EmojiData.SIZE + 2;
        columns = Math.max(1, contentWidth / cell);
        rows = (EmojiData.getCount() + columns - 1) / columns;
        ensureVisible();
    }

    private int getMaxScroll() {
        return Math.max(0, rows * cell - getHeight());
    }

    private void ensureVisible() {
        int row = selected / columns;
        int y = row * cell;

        if (y < scroll) scroll = y;
        if (y + cell > scroll + getHeight()) scroll = y + cell - getHeight();

        if (scroll < 0) scroll = 0;
        int max = getMaxScroll();
        if (scroll > max) scroll = max;
    }

    public void draw(Graphics g) {
        g.setColor(Colors.listScreenBackground);
        g.fillRect(0, 0, getWidth(), getHeight());

        int count = EmojiData.getCount();
        if (!EmojiData.isAvailable() || count == 0) {
            g.setColor(Colors.listScreenEmptyText);
            g.setFont(Fonts.plain);
            g.drawString("No emoji data", getWidth() / 2, getHeight() / 2,
                Graphics.HCENTER | Graphics.TOP);
            return;
        }

        int firstRow = scroll / cell;
        int lastRow = Math.min(rows - 1, (scroll + getHeight()) / cell);

        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= count) break;

                int x = col * cell;
                int y = row * cell - scroll;

                if (index == selected) {
                    g.setColor(Colors.listItemBackgroundSel);
                    g.fillRect(x, y, cell, cell);
                }

                EmojiData.draw(g, index, x + 1, y + 1);
            }
        }
    }

    public void keyEvent(int keyCode, int gameAction) {
        int count = EmojiData.getCount();
        if (count == 0) return;

        switch (gameAction) {
            case Canvas.LEFT:
                if (selected > 0) selected--;
                break;
            case Canvas.RIGHT:
                if (selected < count - 1) selected++;
                break;
            case Canvas.UP:
                if (selected >= columns) selected -= columns;
                break;
            case Canvas.DOWN:
                if (selected + columns < count) selected += columns;
                break;
            case Canvas.FIRE:
                pick();
                return;
        }

        ensureVisible();
        App.repaint();
    }

    public void pointerReleased(int x, int y) {
        if (x < 0 || y < 0) return;

        int col = x / cell;
        int row = (y + scroll) / cell;
        int index = row * columns + col;
        int count = EmojiData.getCount();

        if (index >= 0 && index < count && col < columns) {
            selected = index;
            pick();
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getPriority() == CMD_BACK) {
            App.popScreen();
        }
        else if (c.getPriority() == CMD_SELECT) {
            pick();
        }
    }

    private void pick() {
        String unicode = EmojiData.getUnicode(selected);
        if (unicode == null) return;

        App.popScreen();  // Make the picker's previous screen current again.
        listener.onEmojiPicked(unicode);
    }
}
