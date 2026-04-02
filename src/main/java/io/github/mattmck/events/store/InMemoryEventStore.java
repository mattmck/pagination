package io.github.mattmck.events.store;

import io.github.mattmck.events.cursor.Cursor;
import io.github.mattmck.events.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * In-memory implementation of {@link EventStore} backed by a pre-sorted list of
 * deduplicated events.
 *
 * <p>This store acts as a "materialized view" over the raw CSV data. At construction
 * time it receives an already-deduplicated, sorted list of events. Queries use binary
 * search to efficiently locate the start position, then scan forward to collect results.</p>
 *
 * <p>The hybrid approach (raw data stored separately, deduplicated view here) mirrors
 * how a database materialized view works: the raw rows are the source of truth, and this
 * store provides fast, consistent reads over the clean data.</p>
 *
 * <p><strong>How cursor lookup works:</strong></p>
 *
 * <p>{@link Event} implements {@link Comparable} with natural ordering by
 * {@code (startTime, id)}. This lets us use {@link java.util.Collections#binarySearch}
 * directly on the sorted list — the same algorithm a database B-tree index on
 * {@code (start_time, id)} would use, but over a plain {@link java.util.ArrayList}.</p>
 *
 * <p>When a cursor arrives (e.g. {@code startTime=1708646400, id="evt-c3"}), we
 * construct a synthetic {@link Event} as a search key and binary-search for its position.
 * If found, we start at {@code index + 1} (strictly after). If not found,
 * {@code binarySearch} returns the insertion point, which is already the first element
 * greater than the cursor. This gives us O(log n) cursor resolution.</p>
 *
 * <p><strong>Sort order support:</strong></p>
 *
 * <p>The store maintains the canonical ascending list and creates a reversed view on
 * demand for descending queries. Binary search and range filtering adapt to the
 * requested comparator, so cursors remain correct regardless of direction — as long
 * as the cursor was derived from the same sort order.</p>
 *
 * <p><strong>Future: database-backed implementation:</strong></p>
 *
 * <p>A database-backed {@link EventStore} would replace the binary search with a SQL
 * query using a composite {@code WHERE} clause:</p>
 * <pre>{@code
 *   WHERE (start_time, id) > (:cursorTime, :cursorId)
 *     AND start_time BETWEEN :rangeStart AND :rangeEnd
 *   ORDER BY start_time, id
 *   LIMIT :limit + 1
 * }</pre>
 * <p>With an index on {@code (start_time, id)}, the database performs the same logical
 * operation as our binary search. The {@link EventStore} interface stays unchanged.</p>
 */
public class InMemoryEventStore implements EventStore {

    private static final Logger Log = LoggerFactory.getLogger(InMemoryEventStore.class);

    private final List<Event> ascendingEvents;
    private final List<Event> descendingEvents;

    /**
     * Creates a new store from a pre-sorted, deduplicated list of events.
     *
     * @param sortedEvents events sorted by {@link Event#START_TIME_ASC}; must not contain
     *                     duplicate IDs
     */
    public InMemoryEventStore(List<Event> sortedEvents) {
        this.ascendingEvents = List.copyOf(sortedEvents);
        var reversed = new ArrayList<>(sortedEvents);
        Collections.reverse(reversed);
        this.descendingEvents = List.copyOf(reversed);
        Log.info("Initialized InMemoryEventStore with {} events", this.ascendingEvents.size());
    }

    @Override
    public List<Event> query(long rangeStart, long rangeEnd, int maxResults, Cursor afterCursor,
                             Comparator<Event> sortOrder, String payloadContains) {
        if (rangeStart > rangeEnd || maxResults <= 0) {
            return List.of();
        }

        var isDescending = sortOrder == Event.START_TIME_DESC;
        var events = isDescending ? descendingEvents : ascendingEvents;
        var filterLower = (payloadContains != null && !payloadContains.isBlank())
                ? payloadContains.toLowerCase()
                : null;

        var startIndex = findStartIndex(events, sortOrder, rangeStart, rangeEnd, afterCursor);
        var results = new ArrayList<Event>(Math.min(maxResults, events.size()));

        for (var i = startIndex; i < events.size() && results.size() < maxResults; i++) {
            var event = events.get(i);

            if (!isInRange(event, rangeStart, rangeEnd)) {
                break;
            }

            if (filterLower != null && !event.payload().toLowerCase().contains(filterLower)) {
                continue;
            }

            results.add(event);
        }

        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the total number of deduplicated events in this store.
     *
     * @return the event count
     */
    public int size() {
        return ascendingEvents.size();
    }

    /**
     * Checks whether an event falls within the time range. For ascending order,
     * we break when we exceed rangeEnd. For descending, we break when we go below
     * rangeStart.
     */
    private boolean isInRange(Event event, long rangeStart, long rangeEnd) {
        return event.startTime() >= rangeStart && event.startTime() <= rangeEnd;
    }

    /**
     * Finds the index of the first event to include in results, using binary search.
     *
     * <p>If a cursor is provided, finds the first event strictly after the cursor position
     * in the given sort order. Otherwise, finds the first event at or after the range
     * boundary appropriate for the sort direction.</p>
     */
    private int findStartIndex(List<Event> events, Comparator<Event> sortOrder,
                               long rangeStart, long rangeEnd, Cursor afterCursor) {
        if (afterCursor != null) {
            return findFirstIndexAfter(events, sortOrder, afterCursor.startTime(), afterCursor.id());
        }
        // For ascending, start at the first event >= rangeStart
        // For descending, start at the first event <= rangeEnd (which is the beginning of the desc list in range)
        var isDescending = sortOrder == Event.START_TIME_DESC;
        if (isDescending) {
            return findFirstDescendingInRange(events, rangeEnd);
        }
        return findFirstAscendingInRange(events, rangeStart);
    }

    /**
     * Binary search for the first event with {@code startTime >= target} in ascending list.
     */
    private int findFirstAscendingInRange(List<Event> events, long targetTime) {
        var searchKey = new Event(targetTime, "", "");
        var index = Collections.binarySearch(events, searchKey, Event.START_TIME_ASC);
        return index >= 0 ? index : -(index + 1);
    }

    /**
     * Binary search for the first event with {@code startTime <= target} in descending list.
     * In descending order, the list goes from highest to lowest startTime.
     * We need the first element where startTime <= rangeEnd.
     */
    private int findFirstDescendingInRange(List<Event> events, long rangeEnd) {
        // In descending list, find first event where startTime <= rangeEnd
        for (var i = 0; i < events.size(); i++) {
            if (events.get(i).startTime() <= rangeEnd) {
                return i;
            }
        }
        return events.size();
    }

    /**
     * Binary search for the first event strictly after {@code (targetTime, targetId)}
     * in the given sort order.
     */
    private int findFirstIndexAfter(List<Event> events, Comparator<Event> sortOrder,
                                    long targetTime, String targetId) {
        var searchKey = new Event(targetTime, targetId, "");
        var index = Collections.binarySearch(events, searchKey, sortOrder);

        if (index >= 0) {
            // Exact match found — return the next position
            return index + 1;
        }

        // Not found — insertion point is the first element strictly greater in sort order
        return -(index + 1);
    }
}
