package fi.gtrxac.discord;

import cc.nnproject.json.JSONObject;
import fi.gtrxac.bluewap.ui.AppBase;
import fi.gtrxac.bluewap.ui.AppCanvas;
import fi.gtrxac.bluewap.ui.Dialog;
import fi.gtrxac.bluewap.ui.ListScreen;
import fi.gtrxac.bluewap.ui.Screen;
import fi.gtrxac.discord.model.Attachment;
import fi.gtrxac.discord.model.Channel;
import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/**
 * Headless smoke test for the Resymbian features.
 *
 * Runs inside MicroEmulator as a real MIDlet (see sdk/test.sh). The debug JAR
 * is used as the application class path so the DEBUG-only DemoScreen is part
 * of the tested code.
 */
public class DiscofiSelfTest extends App {
    private int failures;
    private boolean destroyed;

    private void check(String name, boolean ok) {
        System.out.println((ok ? "PASS: " : "FAIL: ") + name);
        if (!ok) failures++;
    }

    public void startApp() {
        super.startApp();

        try {
            runTests();
        }
        catch (Throwable t) {
            System.out.println("FAIL: unexpected exception: " + t);
            failures++;
        }

        if (failures == 0) {
            System.out.println("TESTS PASSED");
        }
        else {
            System.out.println("TESTS FAILED: " + failures);
        }

        // destroyApp() must stop the workers and notify the JVM. MicroEmulator
        // exits as soon as notifyDestroyed() is called, so this is the last
        // thing the test does.
        destroyApp(false);
    }

    public void destroyApp(boolean unconditional) {
        if (destroyed) return;
        destroyed = true;
        super.destroyApp(unconditional);
    }

    private void runTests() {
        check("startup creates canvas", AppCanvas.instance != null);
        check("startup shows a screen", AppBase.getCurrentScreen() != null);
        check("app property from descriptor",
            AppBase.instance.getAppProperty("MIDlet-Name") != null);

        // A loading screen can have an error dialog pushed on top of it. The
        // loader must remove only its own screen, never the dialog.
        Screen original = AppBase.getCurrentScreen();
        ListScreen loader = new ListScreen();
        App.pushScreen(loader);
        Dialog errorDialog = new Dialog("test error");
        App.pushScreen(errorDialog);
        App.popScreen(loader);
        check("popScreen(Screen) keeps dialog on top",
            AppBase.getCurrentScreen() == errorDialog);
        App.popScreen();
        check("dialog closes back to original screen",
            AppBase.getCurrentScreen() == original);

        // Video attachments must take the player path, not the JPEG preview:
        // the proxy image is only the first frame. Download names are
        // sanitised before they become file:// URLs for platformRequest().
        JSONObject videoData = new JSONObject();
        videoData.put("content_type", "video/mp4");
        videoData.put("filename", "../../clip.mp4");
        videoData.put("url", "http://example.invalid/clip.mp4");
        videoData.put("proxy_url",
            "https://media.discordapp.net/attachments/1/2/3/clip.mp4");
        videoData.put("width", 640);
        videoData.put("height", 360);
        Attachment video = new Attachment(videoData);
        check("video attachment detected", video.isVideo);
        check("video download name sanitized",
            "clip.mp4".equals(VideoLauncher.safeFileName(video)));
        check("video launcher starts idle", !VideoLauncher.isLaunching());
        // The account menu needs the cleanup entry point to be public; calling
        // it here verifies that other classes can reach it.
        VideoLauncher.cleanupStale();
        check("video cleanup leaves launcher idle", !VideoLauncher.isLaunching());

        String lowVideo = video.getVideoUrl("http://media.example.net", 320, 240);
        check("video uses the small media proxy URL",
            lowVideo != null && lowVideo.startsWith("http://media.example.net/attachments")
                && lowVideo.indexOf("format=mp4") > 0
                && lowVideo.indexOf("width=320") > 0
                && lowVideo.indexOf("height=180") > 0);

        check("emoji data available", EmojiData.isAvailable());
        check("emoji table not empty", EmojiData.getCount() > 0);

        int grin = EmojiData.indexOf("grinning");
        check("shortcode lookup", grin >= 0);
        check("unicode lookup", EmojiData.indexOf("\uD83D\uDE00") == grin);

        int unicodeMatch = EmojiData.matchUnicode("\uD83D\uDE00X", 0);
        check("unicode inline match",
            unicodeMatch >= 0 && (unicodeMatch >> 8) == grin && (unicodeMatch & 0xFF) == 2);

        int shortcodeMatch = EmojiData.matchShortcode(":grinning:", 0);
        check("shortcode inline match",
            shortcodeMatch >= 0 && (shortcodeMatch >> 8) == grin &&
            (shortcodeMatch & 0xFF) == ":grinning:".length());

        check("custom tag fallback",
            ":party:".equals(EmojiData.stripCustomTags("<:party:123456789012345678>")));

        check("reaction encoding",
            "%F0%9F%94%A5".equals(DiscordUtil.encodeReaction("\uD83D\uDD25", null)) &&
            "grinning:123".equals(DiscordUtil.encodeReaction("grinning", "123")));

        Image image = Image.createImage(64, 32);
        Graphics g = image.getGraphics();
        check("emoji sprite drawn", grin >= 0 && EmojiData.draw(g, grin, 0, 0));

        // The demo screen contains Unicode emoji, shortcodes, custom emoji,
        // reactions and an unread marker in one message list.
        DemoScreen demo = new DemoScreen();
        demo.recalc();
        demo.draw(g);
        check("demo messages with emoji/reactions render", true);

        // Picker -> listener is the same path used when reacting to a message
        // and when adding an emoji to a message draft.
        final Vector picked = new Vector();
        Screen previous = AppBase.getCurrentScreen();
        EmojiPickerScreen picker = new EmojiPickerScreen(new EmojiPickerListener() {
            public void onEmojiPicked(String unicode) {
                picked.addElement(unicode);
            }
        });
        App.pushScreen(picker);
        picker.recalc();
        picker.draw(g);
        picker.keyEvent(0, Canvas.FIRE);
        check("picker selects an emoji", picked.size() == 1 &&
            picked.elementAt(0) != null && ((String) picked.elementAt(0)).length() > 0);
        check("picker returns to previous screen", AppBase.getCurrentScreen() == previous);

        // Compose flow: emoji must end up in the draft that MessageBox sends.
        Channel channel = new Channel();
        channel.id = "123456789012345678";
        channel.name = "#test";
        channel.type = 0; // Guild text; constants are inlined by ProGuard

        ChatScreen chat = new ChatScreen(channel) {
            public void fetchMessages(String type, String anchor, int page, boolean loading) {}
        };
        App.pushScreen(chat);
        chat.commandAction(new Command("Emoji", Command.SCREEN, 8), null); // CMD_EMOJI
        Screen composePicker = AppBase.getCurrentScreen();
        check("chat opens emoji picker", composePicker instanceof EmojiPickerScreen);

        if (composePicker instanceof EmojiPickerScreen) {
            composePicker.keyEvent(0, Canvas.FIRE);
            check("emoji added to outgoing draft", chat.messageDraft.length() > 0);
        }

    }
}
