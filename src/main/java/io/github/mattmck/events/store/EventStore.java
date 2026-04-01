package io.github.mattmck.events.store;

import io.github.mattmck.events.cursor.Cursor;
import io.github.mattmck.events.model.Event;

import java.util.List;

/**
 * Read-only store for querying deduplicated events within a date range.
 *
 * <p>Implementations are expected to return events sorted by
 * {@link Event#SORT_ORDER} (startTime ascending, then id ascending).</p>
 */
public interface EventStore {

    /**
     * Returns up to {@code maxResults} events whose {@code startTime} falls within
     * the inclusive range {@code [rangeStart, rangeEnd]}.
     *
     * <p>If {@code afterCursor} is non-null, only events positioned strictly after the
     * cursor in sort order are returned. This enables cursor-based pagination.</p>
     *
     * @param rangeStart minimum startTime (inclusive), as a Unix timestamp in seconds
     * @param rangeEnd   maximum startTime (inclusive), as a Unix timestamp in seconds
     * @param maxResults maximum number of events to return
     * @param afterCursor if non-null, skip all events at or before this cursor position
     * @return an unmodifiable list of matching events in sort order
     */
    List<Event> query(long rangeStart, long rangeEnd, int maxResults, Cursor afterCursor);
}
