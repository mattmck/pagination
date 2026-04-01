package io.github.mattmck.events.store;

import io.github.mattmck.events.cursor.Cursor;
import io.github.mattmck.events.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
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
 * <h3>How cursor lookup works</h3>
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
 * <h3>Future: database-backed implementation</h3>
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

    private final List<Event> sortedEvents;

    /**
     * Creates a new store from a pre-sorted, deduplicated list of events.
     *
     * @param sortedEvents events sorted by {@link Event#SORT_ORDER}; must not contain
     *                     duplicate IDs
     */
    public InMemoryEventStore(List<Event> sortedEvents) {
        this.sortedEvents = List.copyOf(sortedEvents);
        Log.info("Initialized InMemoryEventStore with {} events", this.sortedEvents.size());
    }

    @Override
    public List<Event> query(long rangeStart, long rangeEnd, int maxResults, Cursor afterCursor) {
        if (rangeStart > rangeEnd || maxResults <= 0) {
            return List.of();
        }

        var startIndex = findStartIndex(rangeStart, afterCursor);
        var results = new ArrayList<Event>(Math.min(maxResults, sortedEvents.size()));

        for (var i = startIndex; i < sortedEvents.size() && results.size() < maxResults; i++) {
            var event = sortedEvents.get(i);

            if (event.startTime() > rangeEnd) {
                break;
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
        return sortedEvents.size();
    }

    /**
     * Finds the index of the first event to include in results, using binary search.
     *
     * <p>If a cursor is provided, finds the first event strictly after the cursor position
     * in sort order. Otherwise, finds the first event at or after {@code rangeStart}.</p>
     */
    private int findStartIndex(long rangeStart, Cursor afterCursor) {
        if (afterCursor != null) {
            return findFirstIndexAfter(afterCursor.startTime(), afterCursor.id());
        }
        return findFirstIndexAtOrAfter(rangeStart);
    }

    /**
     * Binary search for the first event with {@code startTime >= target}.
     */
    private int findFirstIndexAtOrAfter(long targetTime) {
        var searchKey = new Event(targetTime, "", "");
        var index = Collections.binarySearch(sortedEvents, searchKey);
        // binarySearch returns (-(insertion point) - 1) when key is not found
        return index >= 0 ? index : -(index + 1);
    }

    /**
     * Binary search for the first event strictly after {@code (targetTime, targetId)}
     * in sort order.
     */
    private int findFirstIndexAfter(long targetTime, String targetId) {
        // Search for an event matching the cursor position
        var searchKey = new Event(targetTime, targetId, "");
        var index = Collections.binarySearch(sortedEvents, searchKey);

        if (index >= 0) {
            // Exact match found — return the next position
            return index + 1;
        }

        // Not found — insertion point is where the cursor would be, which is also
        // the first element strictly greater than the cursor
        return -(index + 1);
    }
}
