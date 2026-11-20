package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Graphics;

/** Red "NEW" divider inserted into the chat before the first unread message. */
public class UnreadItem extends Item {
    public UnreadItem() {
        super(false);
    }

    public void recalc(int width, ListScreen screen, Item aboveItem) {
        height = Fonts.height;
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        int y = Fonts.height / 2;

        g.setColor(Theme.unreadLine);
        g.drawLine(0, y, width - 1, y);

        String text = "NEW";
        int textWidth = Fonts.bold.stringWidth(text);
        int x = width - textWidth - 4;

        g.setColor(Theme.unreadBadge);
        g.fillRect(x - 4, 0, textWidth + 8, Fonts.height);

        g.setColor(Theme.unreadBadgeText);
        g.setFont(Fonts.bold);
        g.drawString(text, x, 1, 0);
    }
}
