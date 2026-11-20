//#ifdef DEBUG
package fi.gtrxac.discord;

import cc.nnproject.json.*;
import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

/**
 * DEBUG-only screen with deterministic fake messages. It lets us verify
 * emoji layout, reactions and unread markers in an emulator without using a
 * real Discord account.
 */
public class DemoScreen extends ListScreen implements CommandListener {
    private static final int CMD_BACK = 1;
    private static final int CMD_EMOJI = 2;

    public DemoScreen() {
        super();
        setTitle("Demo");

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Emoji", Command.SCREEN, CMD_EMOJI));

        addItem(new ChannelCategoryItem("Unread + emoji + reactions"));

        long now = (System.currentTimeMillis() - DiscordUtil.DISCORD_EPOCH) << 22;

        addItem(new UnreadItem());
        addItem(new MessageItem(createMessage(
            Long.toString(now - 5000),
            "Buffy",
            "Hola \uD83D\uDE00 :smile: <:party:123456789012345678> texto normal sin emoji",
            2, false)));

        addItem(new MessageItem(createMessage(
            Long.toString(now - 4000),
            "Resymbian",
            "Un mensaje largo con emoji \uD83D\uDD25 y un enlace normal: https://example.com/a:b para comprobar el word wrap.",
            4, true)));

        addItem(new MessageItem(createMessage(
            Long.toString(now - 3000),
            "Nokia E51",
            "Mensaje sin emoji para comprobar que el camino r\u00e1pido no cambia.",
            0, false)));
    }

    private Message createMessage(String id, String authorName, String content,
        int reactionCount, boolean me) {

        JSONObject author = new JSONObject();
        author.put("id", "123456789012345678");
        author.put("username", authorName.toLowerCase());
        author.put("global_name", authorName);

        JSONObject message = new JSONObject();
        message.put("id", id);
        message.put("author", author);
        message.put("content", content);
        message.put("channel_id", "123456789012345678");

        if (reactionCount > 0) {
            JSONArray reactions = new JSONArray();

            JSONObject emoji = new JSONObject();
            emoji.put("id", (String) null);
            emoji.put("name", "\uD83D\uDD25");

            JSONObject reaction = new JSONObject();
            reaction.put("emoji", emoji);
            reaction.put("count", reactionCount);
            reaction.put("me", me);

            reactions.add(reaction);
            message.put("reactions", reactions);
        }

        Message result = new Message(message);
        result.showAuthor = true;
        return result;
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getPriority() == CMD_BACK) {
            App.popScreen();
        }
        else if (c.getPriority() == CMD_EMOJI) {
            App.pushScreen(new EmojiPickerScreen(new EmojiPickerListener() {
                public void onEmojiPicked(String unicode) {
                    // Picker is visual-only in the demo.
                }
            }));
        }
    }
}
//#endif
