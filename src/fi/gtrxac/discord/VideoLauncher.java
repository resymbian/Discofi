package fi.gtrxac.discord;

import fi.gtrxac.discord.model.Attachment;
//#ifndef NO_FILECONNECTION
import java.io.*;
import java.util.Enumeration;
import javax.microedition.io.*;
import javax.microedition.io.file.FileConnection;
import fi.gtrxac.bluewap.Platform;
import fi.gtrxac.bluewap.http.HTTP;
//#endif

/**
 * Hands a video attachment to the phone's own player.
 *
 * Showing the media-proxy JPEG was only a first frame: the user saw a still
 * image no matter how long they waited. The file is downloaded through the
 * patched Discofi HTTP stack into a local file with a real video extension, and
 * platformRequest() then opens that file. Play is a separate command, so the
 * handoff to another application is never a surprise.
 *
 * Videos are first requested as a small MP4 from Discord's media proxy. If the
 * proxy cannot resize them, or the resized file is still over MAX_VIDEO_BYTES,
 * the original URL is streamed by the phone instead of being stored. Nothing
 * is kept: the dedicated video folder is emptied every time Discofi returns to
 * the foreground, because MIDP cannot tell when the external player closes.
 *
 * JSR-75 is optional. On builds/devices without it, open() falls back to the
 * remote-URL handoff; on the E51 the local path is what makes the player work
 * without relying on the browser's TLS stack.
 */
public final class VideoLauncher {
    /** Above this, a local copy costs more than the streaming handoff. */
    private static final int MAX_VIDEO_BYTES = 12 * 1024 * 1024;

    /** Small enough for a 2011 handset decoder and for the phone's storage. */
    private static final int LOW_WIDTH = 320;
    private static final int LOW_HEIGHT = 240;

    private static volatile boolean launching;
    private static volatile boolean playing;

    private VideoLauncher() { }

    /** True while a video is downloading or a viewer handoff is in progress. */
    public static boolean isLaunching() {
        return launching || playing;
    }

    public static void open(final Attachment attachment) {
        if (attachment == null) return;

        if (isLaunching()) {
            new DiscordException("A video is already opening. Wait for it to finish.").show();
            return;
        }

        final String original = downloadUrl(attachment);
        final String low = lowQualityUrl(attachment);
        if (original == null && low == null) {
            new DiscordException("This video has no download URL.").show();
            return;
        }

//#ifndef NO_FILECONNECTION
        if (Platform.supportsFileConnection) {
            final AttachmentScreen screen = new AttachmentScreen(attachment, true);
            launching = true;
            App.pushScreen(screen);

            Thread worker = new Thread(new Runnable() {
                public void run() {
                    String saved = null;
                    try {
                        String first = (low != null) ? low : original;
                        try {
                            saved = download(first, attachment, true);
                        }
                        catch (Throwable primaryError) {
                            boolean tooLarge = primaryError instanceof TooLargeException;
                            if (tooLarge) {
                                screen.videoStatus("Too large to save; streaming...");
                            }
                            if (original == null || original.equals(first)) {
                                if (tooLarge) {
                                    stream(original != null ? original : first, screen);
                                    return;
                                }
                                throw primaryError;
                            }
                            try {
                                saved = download(original, attachment, false);
                            }
                            catch (TooLargeException stillTooLarge) {
                                screen.videoStatus("Too large to save; streaming...");
                                stream(original, screen);
                                return;
                            }
                            catch (Throwable fallbackError) {
                                if (tooLarge) {
                                    stream(original, screen);
                                    return;
                                }
                                throw fallbackError;
                            }
                        }
                        screen.videoReady(saved);
                    }
                    catch (Throwable t) {
                        t.printStackTrace();
                        screen.videoFailed(shortMessage(t));
                    }
                    finally {
                        // A ready video keeps launching set until Play or Back:
                        // the saved file is still the active video handoff.
                        if (saved == null) launching = false;
                    }
                }
            });
            worker.start();
            return;
        }
//#endif

        stream((low != null) ? low : original, null);
    }

    /** Called when the MIDlet resumes: a previous handoff is over. */
    public static void resumed() {
        playing = false;
        launching = false;
        cleanupStale();
    }

    /** Called when the video screen is closed without playing the file. */
    static void cancel() {
        if (!playing) launching = false;
    }

    /**
     * Ask the phone to open the file or URL. A true return means the platform
     * took over, so this MIDlet must stop; MIDP leaves that to the application.
     */
    public static boolean play(String url) {
        if (url == null || url.length() == 0 || playing) return false;

        playing = true;
        boolean handled = false;
        try {
            handled = App.instance.platformRequest(url);
            if (handled) {
                App.instance.destroyApp(false);
                return true;
            }
            new DiscordException("The phone did not open the video. Saved as: " + url).show();
            return false;
        }
        catch (Throwable t) {
            t.printStackTrace();
            new DiscordException("The phone could not open the video. Saved as: "
                + url + " (" + shortMessage(t) + ")").show();
            return false;
        }
        finally {
            if (!handled) playing = false;
        }
    }

    /** Stream from the source URL: no local copy, so nothing to delete later. */
    private static void stream(String url, AttachmentScreen screen) {
        if (url == null || url.length() == 0) {
            if (screen != null) screen.videoFailed("no URL to stream");
            return;
        }
        if (screen != null) screen.videoStatus("Streaming video...");
        if (!play(url) && screen != null) {
            screen.videoFailed("the phone refused the stream");
        }
    }

    private static String downloadUrl(Attachment attachment) {
        String url = attachment.url;
        try {
            Account acc = Accounts.getActive();
            if (acc != null) {
                String routed = attachment.getDownloadUrl(acc.getCdnUrl());
                if (routed != null && routed.length() > 0) url = routed;
            }
        }
        catch (Throwable ignored) {}
        return (url == null || url.length() == 0) ? null : url;
    }

    /** Discord's media proxy can resize a video; keep the request tiny. */
    private static String lowQualityUrl(Attachment attachment) {
        try {
            Account acc = Accounts.getActive();
            String proxy = (acc == null) ? null : acc.getMediaProxyUrl();
            String url = attachment.getVideoUrl(proxy, LOW_WIDTH, LOW_HEIGHT);
            return (url == null || url.length() == 0) ? null : url;
        }
        catch (Throwable ignored) { return null; }
    }

    private static String shortMessage(Throwable t) {
        String text = (t == null) ? "unknown error" : t.toString();
        if (text == null || text.length() == 0) return "unknown error";
        return (text.length() > 100) ? text.substring(0, 100) : text;
    }

//#ifndef NO_FILECONNECTION
    /**
     * Download to a .part file first. A failed or interrupted transfer can
     * never be mistaken for a playable video, and a stale file from a previous
     * attempt is removed before the rename.
     */
    private static String download(String remote, Attachment attachment,
                                   boolean requireVideo) throws Exception {
        String dir = directoryUrl();
        ensureDirectory(dir);

        String name = safeFileName(attachment);
        String partName = ".discofi-" + name + ".part";
        String finalUrl = dir + name;
        String partUrl = dir + partName;

        FileConnection part = null;
        FileConnection existing = null;
        OutputStream out = null;
        HTTP request = null;
        boolean renamed = false;

        try {
            part = (FileConnection) Connector.open(partUrl, Connector.READ_WRITE);
            if (part.exists()) part.delete();
            part.create();
            out = part.openOutputStream();

            request = HTTP.createRequest("GET", remote);
            int code = request.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + " while downloading video");
            }
            if (requireVideo && !looksLikeVideo(request)) {
                throw new Exception("media proxy returned a still image");
            }
            checkDeclaredLength(request);

            InputStream in = request.getResponseStream();
            byte[] buffer = new byte[4096];
            int count;
            int written = 0;
            while ((count = in.read(buffer)) != -1) {
                if (count > 0) {
                    written += count;
                    if (written > MAX_VIDEO_BYTES) {
                        throw new TooLargeException();
                    }
                    out.write(buffer, 0, count);
                }
            }
            out.flush();
            out.close();
            out = null;

            request.close();
            request = null;

            part.close();
            part = null;

            // rename() cannot replace an existing file on S60.
            existing = (FileConnection) Connector.open(finalUrl, Connector.READ_WRITE);
            if (existing.exists()) existing.delete();
            existing.close();
            existing = null;

            part = (FileConnection) Connector.open(partUrl, Connector.READ_WRITE);
            part.rename(name);
            renamed = true;
            return finalUrl;
        }
        finally {
            if (request != null) {
                try { request.close(); } catch (Throwable ignored) {}
            }
            if (out != null) {
                try { out.close(); } catch (Throwable ignored) {}
            }
            if (existing != null) {
                try { existing.close(); } catch (Throwable ignored) {}
            }
            if (part != null) {
                if (!renamed) {
                    try {
                        if (part.exists()) part.delete();
                    }
                    catch (Throwable ignored) {}
                }
                try { part.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void checkDeclaredLength(HTTP request) throws Exception {
        String value = request.getResponseHeader("Content-Length");
        if (value == null) value = request.getResponseHeader("content-length");
        if (value == null || value.length() == 0) return;
        try {
            long declared = Long.parseLong(value.trim());
            if (declared > MAX_VIDEO_BYTES) throw new TooLargeException();
        }
        catch (NumberFormatException ignored) {}
    }

    /** The media proxy may answer a video request with a JPEG still. */
    private static boolean looksLikeVideo(HTTP request) throws Exception {
        String value = request.getResponseHeader("Content-Type");
        if (value == null) value = request.getResponseHeader("content-type");
        if (value == null || value.length() == 0) return true;
        String type = value.toLowerCase();
        return type.startsWith("video/") || type.startsWith("application/octet-stream");
    }

//#endif

    /**
     * MIDP cannot observe the external player closing, so the local copy is
     * removed when Discofi is activated again (or on the next launch, when the
     * platform destroyed the MIDlet after platformRequest()).
     */
    private static void cleanupStale() {
//#ifndef NO_FILECONNECTION
        if (!Platform.supportsFileConnection) return;

        deleteFiles(directoryUrl(), false);

        // Leftovers from the old build, which saved into the system folder.
        String[] keys = { "fileconn.dir.videos", "fileconn.dir.music",
            "fileconn.dir.photos" };
        for (int i = 0; i < keys.length; i++) {
            String legacy = System.getProperty(keys[i]);
            if (legacy == null || legacy.length() == 0) continue;
            if (legacy.indexOf("file:") != 0) {
                if (legacy.startsWith("/")) legacy = legacy.substring(1);
                legacy = "file:///" + legacy;
            }
            if (!legacy.endsWith("/")) legacy += "/";
            deleteFiles(legacy, true);
        }
//#endif
    }

//#ifndef NO_FILECONNECTION
    /** all=false removes everything in our private folder; true only .part files. */
    private static void deleteFiles(String dirUrl, boolean onlyParts) {
        FileConnection dir = null;
        try {
            dir = (FileConnection) Connector.open(dirUrl, Connector.READ_WRITE);
            if (!dir.exists()) return;
            Enumeration names = dir.list();
            while (names != null && names.hasMoreElements()) {
                String name = (String) names.nextElement();
                if (name == null || name.length() == 0) continue;
                if (onlyParts && !(name.startsWith(".discofi-")
                        && name.endsWith(".part"))) continue;
                FileConnection file = null;
                try {
                    file = (FileConnection) Connector.open(
                        dirUrl.endsWith("/") ? dirUrl + name : dirUrl + "/" + name,
                        Connector.READ_WRITE);
                    if (file.exists() && !file.isDirectory()) file.delete();
                }
                catch (Throwable ignored) {}
                finally {
                    if (file != null) {
                        try { file.close(); } catch (Throwable ignored) {}
                    }
                }
            }
        }
        catch (Throwable ignored) {}
        finally {
            if (dir != null) {
                try { dir.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** A private folder: cleanup can never remove somebody else's video. */
    private static String directoryUrl() {
        String[] keys = { "fileconn.dir.others", "fileconn.dir.memorycard" };
        for (int i = 0; i < keys.length; i++) {
            String value = System.getProperty(keys[i]);
            if (value == null || value.length() == 0) continue;
            if (value.indexOf("file:") != 0) {
                if (value.startsWith("/")) value = value.substring(1);
                value = "file:///" + value;
            }
            if (!value.endsWith("/")) value += "/";
            return value + "Discofi/Videos/";
        }
        return "file:///C:/Data/Discofi/Videos/";
    }

    private static void ensureDirectory(String url) throws IOException {
        FileConnection dir = null;
        try {
            dir = (FileConnection) Connector.open(url, Connector.READ_WRITE);
            if (!dir.exists()) dir.mkdir();
        }
        finally {
            if (dir != null) {
                try { dir.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** Package-visible so the debug smoke test can check the sanitising. */
    static String safeFileName(Attachment attachment) {
        String name = attachment.filename;
        if (name == null || name.length() == 0) name = "video";

        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < name.length()) name = name.substring(slash + 1);

        String lower = name.toLowerCase();
        String extension = defaultExtension(attachment);
        int dot = lower.lastIndexOf('.');
        if (dot > 0 && dot < lower.length() - 1) {
            String candidate = lower.substring(dot);
            if (isVideoExtension(candidate)) extension = candidate;
            name = name.substring(0, dot);
        }

        StringBuffer safe = new StringBuffer();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') || c == '-' || c == '_') {
                safe.append(c);
            }
            else {
                safe.append('_');
            }
        }
        while (safe.length() > 0 && safe.charAt(safe.length() - 1) == '_') {
            safe.setLength(safe.length() - 1);
        }
        if (safe.length() == 0) safe.append("video");
        if (safe.length() > 40) safe.setLength(40);
        return safe.toString() + extension;
    }

    private static String defaultExtension(Attachment attachment) {
        String type = attachment.contentType;
        if (type != null) {
            type = type.toLowerCase();
            if (type.startsWith("video/webm")) return ".webm";
            if (type.startsWith("video/quicktime")) return ".mov";
            if (type.startsWith("video/3gpp")) return ".3gp";
            if (type.startsWith("video/x-m4v")) return ".m4v";
            if (type.startsWith("video/mp4")) return ".mp4";
            if (type.startsWith("video/x-matroska")) return ".mkv";
            if (type.startsWith("video/x-msvideo")) return ".avi";
        }
        return ".mp4";
    }

    private static boolean isVideoExtension(String value) {
        return value.equals(".mp4") || value.equals(".m4v") || value.equals(".3gp") ||
            value.equals(".webm") || value.equals(".mov") || value.equals(".mkv") ||
            value.equals(".avi");
    }

    /** The download crossed the storage bound: stream instead of saving. */
    private static final class TooLargeException extends Exception {
        TooLargeException() { super("video exceeds the local storage bound"); }
    }
//#endif
}
