package fi.gtrxac.discord;

import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.client.*;
import fi.gtrxac.discord.model.*;
import javax.microedition.lcdui.Graphics;

public class AttachmentItem extends SingleLineLinkItem {
    public final Attachment attachment;

    public AttachmentItem(Attachment att) {
        super(att.filename);
        attachment = att;
    }

    public void draw(Graphics g, ListScreen screen, int width, boolean highlighted) {
        g.translate(MessageItem.contentX, 0);
        super.draw(g, screen, MessageItem.contentWidth, highlighted);
        g.translate(-MessageItem.contentX, 0);
    }

    public void recalc(int width) {
        super.recalc(MessageItem.contentWidth);
    }

    public void itemSelected() {
        // A video's proxy image is only its first frame. Route flagged videos
        // to the local-file player before the preview branch can show it.
        if (attachment.isVideo) {
            VideoLauncher.open(attachment);
        }
        else if (attachment.previewable) {
            App.pushScreen(new AttachmentScreen(attachment));
        }
        else {
            try {
                Account acc = Accounts.getActive();
                String url = attachment.getDownloadUrl(acc.getCdnUrl());
                if (App.instance.platformRequest(url)) {
                    App.instance.destroyApp(false);
                }
            }
            catch (Exception e) {
                new DiscordException(e).show();
            }
        }
    }   
}