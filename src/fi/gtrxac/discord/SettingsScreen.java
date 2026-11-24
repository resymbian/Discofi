package fi.gtrxac.discord;

import fi.gtrxac.discord.model.*;
import fi.gtrxac.bluewap.ui.*;
import fi.gtrxac.bluewap.*;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.TextField;

public class SettingsScreen extends ListScreen implements CommandListener {
    public static final int CMD_BACK = 1;

    private RadioButtonGroup fontSizeGroup;
    private RadioButtonGroup themeGroup;
    private RadioButtonGroup avatarGroup;
    private RadioButtonGroup timeFormatGroup;
    private TextFieldItem messageCountField;
    private RadioButtonGroup sendTypingGroup;
    private RadioButtonGroup emojiGroup;
    private RadioButtonGroup notificationsGroup;
    private RadioButtonGroup notificationIntervalGroup;
    private RadioButtonGroup notifyDMsGroup;
    private RadioButtonGroup notifyMentionsGroup;
    private ButtonItem testNotificationButton;

    public SettingsScreen() {
        super();
        setTitle("Settings");

        addItem(new ChannelCategoryItem("Appearance"));

        fontSizeGroup = new RadioButtonGroup();
        addItem("Font size:");
        addItem(new RadioButtonItem(fontSizeGroup, "Small"));
        addItem(new RadioButtonItem(fontSizeGroup, "Medium"));
        addItem(new RadioButtonItem(fontSizeGroup, "Large"));

        int sizeIndex = Settings.fontSize == Font.SIZE_SMALL ? 0 :
            Settings.fontSize == Font.SIZE_MEDIUM ? 1 : 2;

        fontSizeGroup.setTickedIndex(sizeIndex);

        themeGroup = new RadioButtonGroup();
        addItem("Theme:");
        addItem(new RadioButtonItem(themeGroup, "Dark"));
        addItem(new RadioButtonItem(themeGroup, "Light"));

        themeGroup.setTickedIndex(Settings.lightTheme ? 1 : 0);

        avatarGroup = new RadioButtonGroup();
        addItem("Avatars:");
        addItem(new RadioButtonItem(avatarGroup, "Circle"));
        addItem(new RadioButtonItem(avatarGroup, "Square"));

        avatarGroup.setTickedIndex(Settings.squareAvatars ? 1 : 0);

        timeFormatGroup = new RadioButtonGroup();
        addItem("Time format:");
        addItem(new RadioButtonItem(timeFormatGroup, "12 hours"));
        addItem(new RadioButtonItem(timeFormatGroup, "24 hours"));

        timeFormatGroup.setTickedIndex(Settings.use12hTime ? 0 : 1);

        addItem(new BlankItem(0));
        addItem(new ChannelCategoryItem("Behavior"));

        messageCountField = new TextFieldItem(
            "Message count", Integer.toString(Settings.messageLoadCount), 3, TextField.NUMERIC);

        addItem("Message load count:");
        addItem(messageCountField);

        sendTypingGroup = new RadioButtonGroup();
        addItem("Send typing indicator:");
        addItem(new RadioButtonItem(sendTypingGroup, "On"));
        addItem(new RadioButtonItem(sendTypingGroup, "Off"));

        sendTypingGroup.setTickedIndex(Settings.sendTypingIndicator ? 0 : 1);

        addItem(new BlankItem(0));
        addItem(new ChannelCategoryItem("Messages"));

        emojiGroup = new RadioButtonGroup();
        addItem("Emoji images:");
        addItem(new RadioButtonItem(emojiGroup, "On"));
        addItem(new RadioButtonItem(emojiGroup, "Off"));

        emojiGroup.setTickedIndex(Settings.showEmojis ? 0 : 1);

        addItem(new BlankItem(0));
        addItem(new ChannelCategoryItem("Notifications"));

        notificationsGroup = new RadioButtonGroup();
        addItem("Background notifications:");
        addItem(new RadioButtonItem(notificationsGroup, "On"));
        addItem(new RadioButtonItem(notificationsGroup, "Off"));

        notificationsGroup.setTickedIndex(Settings.notificationsEnabled ? 0 : 1);

        notificationIntervalGroup = new RadioButtonGroup();
        addItem("Check every:");
        addItem(new RadioButtonItem(notificationIntervalGroup, "1 min"));
        addItem(new RadioButtonItem(notificationIntervalGroup, "5 min"));
        addItem(new RadioButtonItem(notificationIntervalGroup, "15 min"));

        int intervalIndex = Settings.notificationIntervalMinutes <= 1 ? 0 :
            Settings.notificationIntervalMinutes <= 5 ? 1 : 2;
        notificationIntervalGroup.setTickedIndex(intervalIndex);

        notifyDMsGroup = new RadioButtonGroup();
        addItem("Notify direct messages:");
        addItem(new RadioButtonItem(notifyDMsGroup, "On"));
        addItem(new RadioButtonItem(notifyDMsGroup, "Off"));
        notifyDMsGroup.setTickedIndex(Settings.notifyDMs ? 0 : 1);

        notifyMentionsGroup = new RadioButtonGroup();
        addItem("Notify mentions:");
        addItem(new RadioButtonItem(notifyMentionsGroup, "On"));
        addItem(new RadioButtonItem(notifyMentionsGroup, "Off"));
        notifyMentionsGroup.setTickedIndex(Settings.notifyMentions ? 0 : 1);

        testNotificationButton = new ButtonItem("Send test notification");
        addItem(testNotificationButton);

        setCommandListener(this);
        addCommand(new Command("Back", Command.BACK, CMD_BACK));
    }

    private void writeSettings() {
        int[] fontSizes = { Font.SIZE_SMALL, Font.SIZE_MEDIUM, Font.SIZE_LARGE };
        Settings.fontSize = fontSizes[fontSizeGroup.getTickedIndex()];

        Settings.lightTheme = (themeGroup.getTickedIndex() == 1);
        Settings.squareAvatars = (avatarGroup.getTickedIndex() == 1);
        Settings.use12hTime = (timeFormatGroup.getTickedIndex() == 0);
        Settings.messageLoadCount = parseMessageLoadCount();
        Settings.sendTypingIndicator = (sendTypingGroup.getTickedIndex() == 0);
        Settings.showEmojis = (emojiGroup.getTickedIndex() == 0);

        boolean enabled = notificationsGroup.getTickedIndex() == 0;
        int[] intervals = { 1, 5, 15 };
        int interval = intervals[notificationIntervalGroup.getTickedIndex()];
        boolean notifyDMs = notifyDMsGroup.getTickedIndex() == 0;
        boolean notifyMentions = notifyMentionsGroup.getTickedIndex() == 0;

        boolean restartPoller = (enabled != Settings.notificationsEnabled) ||
            (interval != Settings.notificationIntervalMinutes) ||
            (notifyDMs != Settings.notifyDMs) ||
            (notifyMentions != Settings.notifyMentions);

        Settings.notificationsEnabled = enabled;
        Settings.notificationIntervalMinutes = interval;
        Settings.notifyDMs = notifyDMs;
        Settings.notifyMentions = notifyMentions;

        if (restartPoller) NotificationPoller.settingsChanged();

        // HTTPQueue.maxSlots = Integer.parseInt(connLimitField.getValue());
        // if (HTTPQueue.maxSlots < 1) HTTPQueue.maxSlots = 1;
    }

    private int parseMessageLoadCount() {
        try {
            int result = Integer.parseInt(messageCountField.getValue());

            if (result < 1) {
                return 1;
            }
            if (result > 100) {
                return 100;
            }
            return result;
        }
        catch (NumberFormatException e) {
            return 20;
        }
    }

    public void commandAction(Command c, Displayable d) {
        switch (c.getPriority()) {
            case CMD_BACK: {
                writeSettings();
                Settings.save();
                App.popScreen();
                break;
            }
        }
    }

    public void itemSelected(Item i) {
        if (i == testNotificationButton) {
            boolean ok = NotificationBridge.post(
                "Discofi", "Test notification from Discofi");

            String message;
            if (ok) {
                message = "Notification sent. The native agent is working.";
            }
            else {
                message = "No native agent answered. Install NotiHelper.sisx and "
                    + "start it once. (" + NotificationBridge.lastError + ")";
            }
            App.pushScreen(new Dialog(message));
            return;
        }

        writeSettings();
        Settings.refresh();
    }
}