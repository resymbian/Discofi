package fi.gtrxac.discord;

import java.util.*;
import cc.nnproject.json.*;
import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;

public class ChannelListScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;
    public static final int CMD_REFRESH = 2;

    private Guild guild;

    public ChannelListScreen(Guild guild) {
        super();
        setTitle(guild.getName());

        this.guild = guild;

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
        addCommand(new Command("Refresh", Command.SCREEN, CMD_REFRESH));
        
        loadOrFetchData();
    }

    private void loadOrFetchData() {
        Account acc = Accounts.getActive();
        Vector cachedChannels = acc.channelCache.get(guild.id);

        if (cachedChannels != null) {
            populate(cachedChannels);
        }
        else {
            fetchData();
        }
    }

    private void fetchData() {
        HTTPThread h = new HTTPThread(HTTPThread.ACTION_FETCH_GUILD_CHANNELS, this);
        h.guild = guild;
        h.start();
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                Screen previous = App.getPreviousScreen();
                if (previous instanceof MainScreen) {
                    ((MainScreen) previous).updateUnreadIndicators();
                }
                App.popScreen();
                break;
            }
            case CMD_REFRESH: {
                fetchData();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
        if (i instanceof ListItem) {
            ListItem li = (ListItem) i;
            Channel channel = (Channel) li.object;
            App.pushScreen(new ChatScreen(channel));
        }
    }

    public void populate(Vector categories) {
        removeAllItems();

        for (int i = 0; i < categories.size(); i++) {
            ChannelCategory category = (ChannelCategory) categories.elementAt(i);

            if (category.name != null) {
                addItem(new ChannelCategoryItem(category.name));
                addItem(new BlankItem(0));
            }
            
            for (int j = 0; j < category.children.size(); j++) {
                Channel channel = (Channel) category.children.elementAt(j);
                UnreadStore.applyLastMessageID(channel);

                ListItem item = new ListItem(channel.name, channel);
                item.unread = UnreadStore.hasUnread(channel);
                addItem(item);
            }
        }
    }

    /** Refresh unread dots after returning from a chat. */
    public void updateUnreadIndicators() {
        for (int i = 0; i < items.size(); i++) {
            Item item = (Item) items.elementAt(i);
            if (!(item instanceof ListItem)) continue;

            ListItem li = (ListItem) item;
            if (li.object instanceof Channel) {
                li.unread = UnreadStore.hasUnread((Channel) li.object);
                li.needRecalc();
            }
        }
        App.repaint();
    }

    public void showError() {
        removeAllItems();
        addItem(new FailedToLoadItem());
    }
}