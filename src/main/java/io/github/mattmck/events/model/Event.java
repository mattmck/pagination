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

    /** Comparator: startTime ascending, then id ascending (default). */
    public static final Comparator<Event> START_TIME_ASC =
            Comparator.comparingLong(Event::startTime).thenComparing(Event::id);

    /** Comparator: startTime descending, then id descending. */
    public static final Comparator<Event> START_TIME_DESC =
            START_TIME_ASC.reversed();

    /**
     * Default sort order used for natural ordering ({@link #START_TIME_ASC}).
     */
    public static final Comparator<Event> SORT_ORDER = START_TIME_ASC;

    /**
     * Returns the appropriate comparator for the given direction.
     *
     * @param direction {@code "asc"} or {@code "desc"} (case-insensitive)
     * @return the corresponding comparator
     * @throws IllegalArgumentException if direction is not {@code "asc"} or {@code "desc"}
     */
    public static Comparator<Event> comparatorForDirection(String direction) {
        return switch (direction.toLowerCase()) {
            case "asc" -> START_TIME_ASC;
            case "desc" -> START_TIME_DESC;
            default -> throw new IllegalArgumentException(
                    "Invalid direction: " + direction + ". Must be 'asc' or 'desc'.");
        };
    }

    @Override
    public int compareTo(Event other) {
        return SORT_ORDER.compare(this, other);
    }
}
