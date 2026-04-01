package io.github.mattmck.events.cursor;

/**
 * Decoded pagination cursor representing a position in the event sort order.
 *
 * <p>A cursor encodes the {@code startTime} and {@code id} of the last event on the
 * previous page. The next page returns events strictly after this position in the
 * canonical sort order ({@code startTime} ascending, then {@code id} ascending).</p>
 *
 * @param startTime the startTime of the last event on the previous page
 * @param id        the id of the last event on the previous page
 */
public record Cursor(long startTime, String id) {
}
