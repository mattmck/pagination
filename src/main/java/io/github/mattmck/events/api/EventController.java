package io.github.mattmck.events.api;

import io.github.mattmck.events.cursor.Cursor;
import io.github.mattmck.events.cursor.CursorCodec;
import io.github.mattmck.events.model.Event;
import io.github.mattmck.events.store.EventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller exposing the paginated events endpoint.
 *
 * <p>Clients query events within a date range using cursor-based pagination.
 * The endpoint fetches {@code limit + 1} events from the store: if more than
 * {@code limit} are returned, a {@code next_cursor} is included in the response
 * for the client to request the next page.</p>
 *
 * @see EventPageResponse
 */
@RestController
@RequestMapping("/api")
public class EventController {

    private static final Logger Log = LoggerFactory.getLogger(EventController.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final EventStore eventStore;

    /**
     * Creates the controller with the given event store.
     *
     * @param eventStore the store to query for events
     */
    public EventController(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    /**
     * Returns a page of events within the specified date range.
     *
     * <p>Both {@code start_time} and {@code end_time} are inclusive Unix timestamps
     * in seconds. Results are ordered by {@code start_time} with {@code id} as a
     * stable tie-breaker. The sort direction defaults to ascending but can be
     * overridden with the {@code direction} parameter.</p>
     *
     * @param startTime the beginning of the date range (inclusive, Unix seconds)
     * @param endTime   the end of the date range (inclusive, Unix seconds)
     * @param limit     maximum number of events per page (default 20, max 100)
     * @param cursor    opaque cursor from a previous response's {@code next_cursor}
     * @param direction sort direction: {@code "asc"} (default) or {@code "desc"}
     * @return a page of events with an optional cursor for the next page
     */
    @GetMapping("/events")
    public ResponseEntity<?> getEvents(
            @RequestParam("start_time") Long startTime,
            @RequestParam("end_time") Long endTime,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "direction", required = false, defaultValue = "asc") String direction) {

        if (startTime == null || endTime == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "start_time and end_time are required"));
        }

        java.util.Comparator<Event> sortOrder;
        try {
            sortOrder = Event.comparatorForDirection(direction);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }

        var effectiveLimit = clampLimit(limit);

        Cursor afterCursor = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                afterCursor = CursorCodec.decode(cursor);
            } catch (IllegalArgumentException e) {
                Log.warn("Invalid cursor received: {}", cursor, e);
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid cursor: " + e.getMessage()));
            }
        }

        var results = eventStore.query(startTime, endTime, effectiveLimit + 1, afterCursor, sortOrder);

        String nextCursor = null;
        var events = results;

        if (results.size() > effectiveLimit) {
            events = results.subList(0, effectiveLimit);
            var lastEvent = events.getLast();
            nextCursor = CursorCodec.encode(lastEvent.startTime(), lastEvent.id());
        }

        return ResponseEntity.ok(new EventPageResponse(events, nextCursor));
    }

    /**
     * Clamps the requested limit to the allowed range [{@code 1}, {@link #MAX_LIMIT}],
     * defaulting to {@link #DEFAULT_LIMIT} if not specified.
     */
    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.clamp(limit, 1, MAX_LIMIT);
    }
}
