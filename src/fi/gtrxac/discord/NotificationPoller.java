package fi.gtrxac.discord;

import cc.nnproject.json.*;
import fi.gtrxac.bluewap.Util;
import fi.gtrxac.bluewap.ui.AppBase;
import fi.gtrxac.bluewap.ui.Screen;
import fi.gtrxac.discord.model.*;
import java.io.*;
import java.util.*;
import javax.microedition.rms.*;

/**
 * Background REST poller for system notifications.
 *
 * Discofi has no Discord gateway, so this polls the same REST routes the Inbox
 * tab uses. It keeps running while the MIDlet is in the background (S60 Java
 * MIDlets keep their threads), and sends new DMs/mentions to NotificationBridge.
 *
 * The first observation of a channel only records its newest message ID, so
 * enabling notifications never produces a burst of old messages.
 */
public class NotificationPoller implements Runnable {
    private static final String RMS_NAME = "notify";

    private static NotificationPoller instance;

    private Thread thread;
    private volatile boolean running;
    private volatile boolean dirty;
    private final Hashtable lastSeen = new Hashtable();

    /** Start/restart after login or a settings change. */
    public static synchronized void settingsChanged() {
        stop();

        if (Settings.notificationsEnabled) {
            instance = new NotificationPoller();
            instance.running = true;
            instance.thread = new Thread(instance);
            instance.thread.start();
        }
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.running = false;
        instance = null;
    }

    private void load() {
        try {
            RecordStore rms = RecordStore.openRecordStore(RMS_NAME, true);

            if (rms.getNumRecords() >= 1) {
                byte[] data = rms.getRecord(1);
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                int count = in.readInt();

                for (int i = 0; i < count; i++) {
                    String channelID = Long.toString(in.readLong());
                    lastSeen.put(channelID, new Long(in.readLong()));
                }
            }

            rms.closeRecordStore();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void save() {
        if (!dirty) return;

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(lastSeen.size() * 16);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(lastSeen.size());

            for (Enumeration e = lastSeen.keys(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                Long value = (Long) lastSeen.get(key);
                out.writeLong(Long.parseLong(key));
                out.writeLong(value.longValue());
            }

            byte[] data = bytes.toByteArray();
            RecordStore rms = RecordStore.openRecordStore(RMS_NAME, true);

            if (rms.getNumRecords() >= 1) {
                rms.setRecord(1, data, 0, data.length);
            }
            else {
                rms.addRecord(data, 0, data.length);
            }

            rms.closeRecordStore();
            dirty = false;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private long getLastSeen(String channelID) {
        if (channelID == null) return 0;

        Long value = (Long) lastSeen.get(channelID);
        return (value != null) ? value.longValue() : 0;
    }

    private void setLastSeen(String channelID, long messageID) {
        if (channelID == null || messageID <= 0) return;

        Long current = (Long) lastSeen.get(channelID);
        if (current == null || messageID > current.longValue()) {
            lastSeen.put(channelID, new Long(messageID));
            dirty = true;
        }
    }

    private static long parseID(String id) {
        try {
            return Long.parseLong(id);
        }
        catch (Exception e) {
            return 0;
        }
    }

    private static String authorName(JSONObject message) {
        try {
            return new User(message.getObject("author")).name;
        }
        catch (Exception e) {
            return "New message";
        }
    }

    private static String snippet(JSONObject message) {
        String content;

        try {
            content = message.getString("content", "");
        }
        catch (Exception e) {
            content = "";
        }

        content = content.replace('\n', ' ');

        if (content.length() == 0) {
            try {
                if (message.getArray("attachments").size() > 0) content = "(attachment)";
            }
            catch (Exception e) {}
        }

        if (content.length() > 80) content = content.substring(0, 80) + "...";
        return content;
    }

    private static boolean isChannelOpen(String channelID) {
        if (channelID == null) return false;

        Screen current = AppBase.getCurrentScreen();
        if (!(current instanceof ChatScreen)) return false;

        Channel channel = ((ChatScreen) current).channel;
        return channel != null && channelID.equals(channel.id);
    }

    private void checkMentions(Account acc) throws DiscordException {
        if (!Settings.notificationsEnabled || !Settings.notifyMentions) return;

        JSONArray mentions = (JSONArray) acc.fetchJSON(
            "/users/@me/mentions?limit=25&roles=true&everyone=true");

        for (int i = 0; i < mentions.size(); i++) {
            JSONObject message = mentions.getObject(i);
            String channelID = message.getString("channel_id", null);
            long messageID = parseID(message.getString("id", null));
            long previous = getLastSeen(channelID);

            if (messageID > 0) setLastSeen(channelID, messageID);

            if (previous == 0 || messageID <= previous || isChannelOpen(channelID)) continue;

            String location = "Mention";

            try {
                String guildID = message.getString("guild_id", null);
                if (guildID == null) {
                    location = "DM";
                }
                else {
                    for (int g = 0; g < acc.getGuilds(false).size(); g++) {
                        Guild guild = (Guild) acc.getGuilds(false).elementAt(g);
                        if (guild.id.equals(guildID)) {
                            location = guild.getName();
                            break;
                        }
                    }
                }
            }
            catch (Exception e) {}

            NotificationBridge.post(location, authorName(message) + ": " + snippet(message));
        }
    }

    private void checkDMs(Account acc) throws DiscordException {
        if (!Settings.notificationsEnabled || !Settings.notifyDMs) return;

        JSONArray channels = (JSONArray) acc.fetchJSON("/users/@me/channels");

        for (int i = 0; i < channels.size(); i++) {
            DMChannel channel;

            try {
                channel = new DMChannel(channels.getObject(i));
            }
            catch (Exception e) {
                continue;
            }

            long previous = getLastSeen(channel.id);

            if (channel.lastMessageID > 0) setLastSeen(channel.id, channel.lastMessageID);

            if (previous == 0 || channel.lastMessageID <= previous ||
                isChannelOpen(channel.id)) continue;

            String text = "New message";

            try {
                JSONArray messages = (JSONArray) acc.fetchJSON(
                    "/channels/" + channel.id + "/messages?limit=1");

                if (messages.size() > 0) {
                    JSONObject message = messages.getObject(0);
                    text = authorName(message) + ": " + snippet(message);
                }
            }
            catch (Exception e) {}

            NotificationBridge.post(channel.getName(), text);
        }
    }

    public void run() {
        load();

        long interval = Math.max(1, Settings.notificationIntervalMinutes) * 60000L;
        long nextCheck = System.currentTimeMillis() + 5000;

        while (running && AppBase.isRunning()) {
            if (System.currentTimeMillis() >= nextCheck) {
                try {
                    for (int i = 0; i < Accounts.list.size(); i++) {
                        Account acc = (Account) Accounts.list.elementAt(i);

                        if (acc == null || acc.token == null || acc.token.length() == 0) continue;

                        checkMentions(acc);
                        checkDMs(acc);
                    }

                    save();
                }
                catch (Throwable t) {
                    // A failed poll must never kill the background thread.
                    t.printStackTrace();
                }

                interval = Math.max(1, Settings.notificationIntervalMinutes) * 60000L;
                nextCheck = System.currentTimeMillis() + interval;
            }

            Util.sleep(500);
        }

        save();
    }
}
