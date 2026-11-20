package fi.gtrxac.discord;

/** Receives the Unicode character chosen in EmojiPickerScreen. */
public interface EmojiPickerListener {
    void onEmojiPicked(String unicode);
}
