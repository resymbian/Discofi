package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

/** Lists the reactions on a message so the user can toggle their own. */
public class ReactionsScreen extends ListScreen implements CommandListener {
    private static final int CMD_BACK = 1;

    private final ChatScreen chat;
    private final Message message;

    public ReactionsScreen(ChatScreen chat, Message message) {
        super();
        this.chat = chat;
        this.message = message;

        setTitle("Reactions");
        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));

        addItem(new ChannelCategoryItem("Tap to add or remove"));

        for (int i = 0; i < message.reactions.size(); i++) {
            Reaction r = (Reaction) message.reactions.elementAt(i);
            String text = r.getFallbackText() + "  x" + r.count;
            if (r.me) text += "  (you)";

            addItem(new ListItem(text, r));
        }
    }

    public void itemSelected(Item item) {
        if (!(item instanceof ListItem)) return;
        Reaction r = (Reaction) ((ListItem) item).object;

        chat.toggleReaction(message, r);
        App.popScreen();
    }

    public void commandAction(Command c, Displayable d) {
        if (c.getPriority() == CMD_BACK) App.popScreen();
    }
}
