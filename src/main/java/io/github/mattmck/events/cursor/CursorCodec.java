package io.github.mattmck.events.cursor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes and decodes opaque pagination cursors.
 *
 * <p>The cursor format is a URL-safe Base64 encoding of {@code "startTime|id"}, where
 * {@code startTime} is a Unix timestamp in seconds and {@code id} is the event identifier.
 * This produces compact, opaque tokens suitable for use as query parameters.</p>
 *
 * <p>Example: the event at timestamp {@code 1708646400} with id {@code "evt-c3"}
 * encodes to {@code "MTcwODY0NjQwMHxldnQtYzM"}.</p>
 */
public final class CursorCodec {

    private static final String SEPARATOR = "|";

    private CursorCodec() {
        // utility class
    }

    /**
     * Encodes a cursor from the given event position.
     *
     * @param startTime the startTime of the last event on the current page
     * @param id        the id of the last event on the current page
     * @return an opaque, URL-safe Base64 cursor string
     */
    public static String encode(long startTime, String id) {
        var raw = startTime + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque cursor string into its component parts.
     *
     * @param cursor the Base64-encoded cursor from the client
     * @return the decoded {@link Cursor} with startTime and id
     * @throws IllegalArgumentException if the cursor is null, empty, malformed Base64,
     *         missing the separator, or contains a non-numeric timestamp
     */
    public static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalArgumentException("Cursor must not be null or blank");
        }

        String decoded;
        try {
            decoded = new String(
                    Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cursor is not valid Base64: " + cursor, e);
        }

        var separatorIndex = decoded.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Cursor missing separator: " + decoded);
        }

        var timePart = decoded.substring(0, separatorIndex);
        var idPart = decoded.substring(separatorIndex + 1);

        long startTime;
        try {
            startTime = Long.parseLong(timePart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Cursor contains non-numeric timestamp: " + timePart, e);
        }

        return new Cursor(startTime, idPart);
    }
}
