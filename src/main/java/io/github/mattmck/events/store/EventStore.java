package io.github.mattmck.events.store;

import io.github.mattmck.events.cursor.Cursor;
import io.github.mattmck.events.model.Event;

import java.util.Comparator;
import java.util.List;

/**
 * Read-only store for querying deduplicated events within a date range.
 *
 * <p>Implementations return events in the order specified by the caller. When no
 * explicit ordering is given, the default is {@link Event#START_TIME_ASC}.</p>
 *
 * <p><strong>Supported sort orders:</strong> Only {@link Event#START_TIME_ASC} and
 * {@link Event#START_TIME_DESC} are supported. Passing a different comparator
 * produces undefined results because implementations may rely on pre-sorted lists
 * that match these specific orderings.</p>
 */
public interface EventStore {

    /**
     * Returns up to {@code maxResults} events whose {@code startTime} falls within
     * the inclusive range {@code [rangeStart, rangeEnd]}, using the default sort order
     * ({@link Event#START_TIME_ASC}).
     *
     * @param rangeStart  minimum startTime (inclusive), as a Unix timestamp in seconds
     * @param rangeEnd    maximum startTime (inclusive), as a Unix timestamp in seconds
     * @param maxResults  maximum number of events to return
     * @param afterCursor if non-null, skip all events at or before this cursor position
     * @return an unmodifiable list of matching events in sort order
     */
    default List<Event> query(long rangeStart, long rangeEnd, int maxResults, Cursor afterCursor) {
        return query(rangeStart, rangeEnd, maxResults, afterCursor, Event.START_TIME_ASC);
    }

    /**
     * Returns up to {@code maxResults} events whose {@code startTime} falls within
     * the inclusive range {@code [rangeStart, rangeEnd]}, in the order defined by
     * {@code sortOrder}.
     *
     * <p>If {@code afterCursor} is non-null, only events positioned strictly after the
     * cursor in the given sort order are returned. The cursor must have been derived
     * from the same sort order — mixing cursors across different orderings produces
     * undefined results.</p>
     *
     * @param rangeStart  minimum startTime (inclusive), as a Unix timestamp in seconds
     * @param rangeEnd    maximum startTime (inclusive), as a Unix timestamp in seconds
     * @param maxResults  maximum number of events to return
     * @param afterCursor if non-null, skip all events at or before this cursor position
     * @param sortOrder   comparator defining the result ordering
     * @return an unmodifiable list of matching events in the specified order
     */
    default List<Event> query(long rangeStart, long rangeEnd, int maxResults, Cursor afterCursor,
                      Comparator<Event> sortOrder) {
        return query(rangeStart, rangeEnd, maxResults, afterCursor, sortOrder, null);
    }

    /**
     * Returns up to {@code maxResults} events whose {@code startTime} falls within
     * the inclusive range {@code [rangeStart, rangeEnd]}, in the order defined by
     * {@code sortOrder}, optionally filtered by a payload substring.
     *
     * <p>If {@code payloadContains} is non-null and non-blank, only events whose payload
     * contains the substring (case-insensitive) are included. The filter is applied before
     * pagination, so paginated totals remain consistent with non-paginated totals when
     * the same filter is used.</p>
     *
     * @param rangeStart      minimum startTime (inclusive), as a Unix timestamp in seconds
     * @param rangeEnd        maximum startTime (inclusive), as a Unix timestamp in seconds
     * @param maxResults      maximum number of events to return
     * @param afterCursor     if non-null, skip all events at or before this cursor position
     * @param sortOrder       comparator defining the result ordering
     * @param payloadContains if non-null, filter events to those whose payload contains
     *                        this substring (case-insensitive)
     * @return an unmodifiable list of matching events in the specified order
     */
    List<Event> query(long rangeStart, long rangeEnd, int maxResults, Cursor afterCursor,
                      Comparator<Event> sortOrder, String payloadContains);
}
