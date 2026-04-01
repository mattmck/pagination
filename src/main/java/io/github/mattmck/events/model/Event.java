package io.github.mattmck.events.model;

import java.util.Comparator;

/**
 * A deduplicated event ready for API responses.
 *
 * <p>Each {@code Event} represents a unique logical event identified by {@code id}.
 * When the source data contains duplicate rows for the same {@code id}, the row with
 * the highest {@code rowIndex} wins (upsert semantics — latest data is kept).</p>
 *
 * <p>Natural ordering is by {@code startTime} ascending, then {@code id} ascending,
 * providing a stable, deterministic sort for cursor-based pagination.</p>
 *
 * @param startTime Unix timestamp in seconds for the event start
 * @param id        unique logical event identifier
 * @param payload   human-readable event description
 */
public record Event(long startTime, String id, String payload) implements Comparable<Event> {

    /** Comparator defining the canonical sort order: startTime ascending, then id ascending. */
    public static final Comparator<Event> SORT_ORDER =
            Comparator.comparingLong(Event::startTime).thenComparing(Event::id);

    @Override
    public int compareTo(Event other) {
        return SORT_ORDER.compare(this, other);
    }
}
