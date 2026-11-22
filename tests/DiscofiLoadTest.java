package fi.gtrxac.discord;

import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;
import fi.gtrxac.bluewap.ui.AppBase;
import fi.gtrxac.discord.model.Channel;
import fi.gtrxac.discord.model.DMChannel;
import fi.gtrxac.discord.model.Guild;
import java.util.Vector;

/**
 * Reproduces the load that follows a successful login, against the fake server
 * in tests/fake_discord.py.
 *
 * It makes the same calls MainScreen makes when it is opened with no cached
 * data (profile, guilds, DMs, a guild's channels) and reports which one throws.
 * HTTPThread only catches DiscordException, so anything printed as FAIL here is
 * an exception that would silently kill the loader thread on the phone and
 * leave the "Loading..." item on screen forever.
 */
public class DiscofiLoadTest extends App {
    private int failures;
    private Account acc;

    private void pass(String name) {
        System.out.println("PASS: " + name);
    }

    private void fail(String name, Throwable t) {
        System.out.println("FAIL: " + name + ": " + t);
        t.printStackTrace();
        failures++;
    }

    public void startApp() {
        super.startApp();

        String apiUrl = AppBase.instance.getAppProperty("FakeApiUrl");
        System.out.println("fake api url: " + apiUrl);

        try {
            acc = new Account();
            acc.token = "MTAwMDAwMDAwMDAwMDAwMDAx.GGGGGG.abcdef";
            acc.connectionMode = 2;    // CONNECTION_MODE_PROXY (inlined by ProGuard in the JAR)
            acc.apiUrl = apiUrl;
            acc.username = null;

            Accounts.list.removeAllElements();
            Accounts.list.addElement(acc);
            Accounts.setActive(0);
            pass("account set up");
        }
        catch (Throwable t) {
            fail("account set up", t);
        }

        try {
            acc.fetchUserProfile();
            pass("fetch /users/@me");
        }
        catch (Throwable t) {
            fail("fetch /users/@me", t);
        }

        Vector guilds = null;
        try {
            guilds = acc.getGuilds(true);
            pass("fetch /users/@me/guilds + settings (" + guilds.size() + " guilds)");
        }
        catch (Throwable t) {
            fail("fetch /users/@me/guilds + settings", t);
        }

        Vector dms = null;
        try {
            dms = acc.getDMChannels(true);
            pass("fetch /users/@me/channels (" + dms.size() + " DMs)");
        }
        catch (Throwable t) {
            fail("fetch /users/@me/channels", t);
        }

        try {
            if (dms != null && dms.size() > 0) {
                DMChannel dm = (DMChannel) dms.elementAt(0);
                System.out.println("  DM name: " + dm.getName());
            }
            if (dms != null && dms.size() > 1) {
                DMChannel group = (DMChannel) dms.elementAt(1);
                System.out.println("  group DM name: " + group.getName());
            }
            pass("DM names built");
        }
        catch (Throwable t) {
            fail("DM names built", t);
        }

        try {
            Guild g = (Guild) guilds.elementAt(0);
            JSONArray channelsJson = (JSONArray) acc.fetchJSON("/guilds/" + g.id + "/channels");
            int channels = 0;
            for (int i = 0; i < channelsJson.size(); i++) {
                Channel c = new Channel(channelsJson.getObject(i));
                UnreadStore.init();
                c.lastMessageID = 0;
                UnreadStore.applyLastMessageID(c);
                UnreadStore.hasUnread(c);
                if (c.type != 4) channels++;    // 4 = TYPE_GUILD_CATEGORY
            }
            pass("fetch guild channels (" + channels + " channels)");
        }
        catch (Throwable t) {
            fail("fetch guild channels", t);
        }

        System.out.println(failures == 0 ? "LOAD TEST PASSED" : "LOAD TEST FAILED: " + failures);

        destroyApp(false);
    }
}
