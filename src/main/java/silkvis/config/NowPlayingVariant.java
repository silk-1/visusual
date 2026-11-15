package silkvis.config;

/** How the Now Playing HUD widget is drawn (MEDIA-*): chosen by the user, not guessed. */
public enum NowPlayingVariant {
    /** One line: track and artist. */
    LINE,
    /** Two lines plus a progress bar and time. */
    PROGRESS,
    /** Album art, title, artist, progress and control buttons. */
    CARD
}
