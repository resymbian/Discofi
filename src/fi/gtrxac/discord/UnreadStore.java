package fi.gtrxac.discord;

import java.io.*;
import java.util.*;
import javax.microedition.rms.*;
import fi.gtrxac.discord.model.*;

/**
 * Local read-state store for unread indicators.
 *
 * Discord has no REST endpoint that lists read states (they are delivered
 * through the gateway), so the fork keeps the last-read message ID locally and
 * compares it with each channel's last_message_id. This costs no extra HTTP
 * requests and works with a 1 MB heap.
 *
 * The tradeoff is the same one Discord J2ME documented: reads performed on
 * another device are not reflected until this client acknowledges the channel.
 */
public class UnreadStore {
    private static final String RMS_NAME = "u";

    private static Hashtable states;
    private static Hashtable lastMessages;
    private static Hashtable acked;
    private static boolean dirty;

    public static void init() {
        states = new Hashtable();
        lastMessages = new Hashtable();
        acked = new Hashtable();
        dirty = false;

        try {
            RecordStore rms = RecordStore.openRecordStore(RMS_NAME, true);

            if (rms.getNumRecords() >= 1) {
                byte[] data = rms.getRecord(1);
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    String channelID = Long.toString(in.readLong());
                    long lastRead = in.readLong();
                    states.put(channelID, new Long(lastRead));
                }
            }

            rms.closeRecordStore();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        loadLongMap("l", lastMessages);

        // On a fresh install, consider messages older than the app install as read.
        if (!states.containsKey("0")) {
            states.put("0", new Long(installSnowflake()));
            dirty = true;
        }
    }

    private static long installSnowflake() {
        long time = System.currentTimeMillis() - DiscordUtil.DISCORD_EPOCH;
        if (time < 0) time = 0;
        return time << 22;
    }

    private static void loadLongMap(String rmsName, Hashtable target) {
        try {
            RecordStore rms = RecordStore.openRecordStore(rmsName, true);

            if (rms.getNumRecords() >= 1) {
                byte[] data = rms.getRecord(1);
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    String key = Long.toString(in.readLong());
                    target.put(key, new Long(in.readLong()));
                }
            }

            rms.closeRecordStore();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void setLastMessageID(Channel channel, long messageID) {
        if (channel == null || messageID <= 0) return;

        Long current = (Long) lastMessages.get(channel.id);
        if (current == null || messageID > current.longValue()) {
            lastMessages.put(channel.id, new Long(messageID));
            dirty = true;
        }
    }

    /** Restore a channel's last_message_id from RMS (cached channel lists). */
    public static void applyLastMessageID(Channel channel) {
        if (channel == null) return;

        Long value = (Long) lastMessages.get(channel.id);
        if (value != null && value.longValue() > channel.lastMessageID) {
            channel.lastMessageID = value.longValue();
        }
    }

    public static long getLastRead(String channelID) {
        Long value = (Long) states.get(channelID);
        if (value == null) value = (Long) states.get("0");
        return (value != null) ? value.longValue() : 0;
    }

    public static boolean hasUnread(Channel channel) {
        if (channel == null || channel.lastMessageID <= 0) return false;
        return getLastRead(channel.id) < channel.lastMessageID;
    }

    public static boolean guildHasUnread(Guild guild) {
        Account acc = Accounts.getActive();
        if (acc == null || guild == null) return false;

        Vector categories = acc.channelCache.get(guild.id);
        if (categories == null) return false;

        for (int i = 0; i < categories.size(); i++) {
            ChannelCategory category = (ChannelCategory) categories.elementAt(i);

            for (int j = 0; j < category.children.size(); j++) {
                Channel channel = (Channel) category.children.elementAt(j);
                applyLastMessageID(channel);
                if (hasUnread(channel)) return true;
            }
        }
        return false;
    }

    /**
     * Mark up to the given message as read. Sends a best-effort ack if the
     * server has not already been told about this message.
     */
    public static void markRead(Channel channel, long messageID) {
        if (channel == null || messageID <= 0) return;

        long current = getLastRead(channel.id);
        if (messageID > current) {
            states.put(channel.id, new Long(messageID));
            dirty = true;
        }

        Long lastAck = (Long) acked.get(channel.id);
        if (lastAck == null || messageID > lastAck.longValue()) {
            acked.put(channel.id, new Long(messageID));

            HTTPThread h = new HTTPThread(HTTPThread.ACTION_MARK_READ);
            h.channel = channel;
            h.markReadMessageID = messageID;
            h.start();
        }
    }

    public static void saveIfDirty() {
        if (!dirty || states == null) return;

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(states.size() * 16);
            DataOutputStream out = new DataOutputStream(bytes);

            out.writeInt(states.size());

            for (Enumeration e = states.keys(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                Long value = (Long) states.get(key);

                out.writeLong(Long.parseLong(key));
                out.writeLong(value.longValue());
            }

            byte[] data = bytes.toByteArray();
            writeRecord(RMS_NAME, data);
            writeLongMap("l", lastMessages);

            dirty = false;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeLongMap(String rmsName, Hashtable map) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(map.size() * 16);
        DataOutputStream out = new DataOutputStream(bytes);

        out.writeInt(map.size());

        for (Enumeration e = map.keys(); e.hasMoreElements(); ) {
            String key = (String) e.nextElement();
            Long value = (Long) map.get(key);

            out.writeLong(Long.parseLong(key));
            out.writeLong(value.longValue());
        }

        writeRecord(rmsName, bytes.toByteArray());
    }

    private static void writeRecord(String rmsName, byte[] data) throws Exception {
        RecordStore rms = RecordStore.openRecordStore(rmsName, true);

        if (rms.getNumRecords() >= 1) {
            rms.setRecord(1, data, 0, data.length);
        }
        else {
            rms.addRecord(data, 0, data.length);
        }

        rms.closeRecordStore();
    }
}
