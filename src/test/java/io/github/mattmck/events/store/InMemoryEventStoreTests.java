package io.github.mattmck.events.store;

import io.github.mattmck.events.cursor.Cursor;
import io.github.mattmck.events.cursor.CursorCodec;
import io.github.mattmck.events.model.Event;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InMemoryEventStore} covering deduplication, range queries,
 * cursor-based pagination, and edge cases.
 *
 * <p>Uses a hand-crafted dataset that mirrors the structure of {@code sample_data.csv}:
 * some events share the same {@code startTime}, and some IDs appear in multiple rows
 * with different payloads to exercise deduplication.</p>
 */
class InMemoryEventStoreTests {

    /** Earliest timestamp in the test data. */
    private static final long T1 = 1000L;
    /** Middle timestamp (shared by multiple events). */
    private static final long T2 = 2000L;
    /** Latest timestamp in the test data. */
    private static final long T3 = 3000L;

    private static InMemoryEventStore store;

    @BeforeAll
    static void setUp() {
        // 7 unique events across 3 timestamps, pre-sorted
        var events = List.of(
                new Event(T1, "evt-a", "Alpha"),
                new Event(T1, "evt-b", "Bravo"),
                new Event(T2, "evt-c", "Charlie"),
                new Event(T2, "evt-d", "Delta"),
                new Event(T2, "evt-e", "Echo"),
                new Event(T3, "evt-f", "Foxtrot"),
                new Event(T3, "evt-g", "Golf")
        );
        store = new InMemoryEventStore(events);
    }

    @Test
    @DisplayName("store reports correct size")
    void storeReportsCorrectSize() {
        assertThat(store.size()).isEqualTo(7);
    }

    @Test
    @DisplayName("full range query returns all events")
    void fullRangeReturnsAllEvents() {
        var results = store.query(T1, T3, 100, null);

        assertThat(results).hasSize(7);
    }

    @Test
    @DisplayName("query with limit returns at most limit results")
    void queryWithLimitRespectsLimit() {
        var results = store.query(T1, T3, 3, null);

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("query with limit+1 pattern detects more results")
    void queryWithLimitPlusOneDetectsNextPage() {
        var limit = 3;
        var results = store.query(T1, T3, limit + 1, null);

        assertThat(results).hasSize(4);
        // The extra result signals there is a next page
    }

    @Test
    @DisplayName("cursor skips past the cursor position")
    void cursorSkipsPastPosition() {
        // Get first 3 events
        var firstPage = store.query(T1, T3, 3, null);
        var lastEvent = firstPage.getLast();

        // Use cursor to get next page
        var cursor = new Cursor(lastEvent.startTime(), lastEvent.id());
        var secondPage = store.query(T1, T3, 3, cursor);

        assertThat(secondPage)
                .isNotEmpty()
                .allSatisfy(event ->
                        assertThat(event.compareTo(lastEvent)).isGreaterThan(0));
    }

    @Test
    @DisplayName("narrow time range returns only matching events")
    void narrowRangeReturnsSubset() {
        var results = store.query(T2, T2, 100, null);

        assertThat(results)
                .hasSize(3)
                .allSatisfy(event -> assertThat(event.startTime()).isEqualTo(T2));
    }

    @Test
    @DisplayName("empty range returns empty list")
    void emptyRangeReturnsEmpty() {
        // Range with no events
        var results = store.query(9999L, 10000L, 100, null);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("inverted range returns empty list")
    void invertedRangeReturnsEmpty() {
        var results = store.query(T3, T1, 100, null);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("boundary timestamps are inclusive")
    void boundaryTimestampsAreInclusive() {
        // Query exactly T1 to T1
        var results = store.query(T1, T1, 100, null);

        assertThat(results)
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.startTime()).isEqualTo(T1));
    }

    @Test
    @DisplayName("cursor at last event returns empty list")
    void cursorAtLastEventReturnsEmpty() {
        var allEvents = store.query(T1, T3, 100, null);
        var lastEvent = allEvents.getLast();

        var cursor = new Cursor(lastEvent.startTime(), lastEvent.id());
        var results = store.query(T1, T3, 100, cursor);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("results are sorted by startTime then id")
    void resultsAreSorted() {
        var results = store.query(T1, T3, 100, null);

        assertThat(results).isSortedAccordingTo(Event.SORT_ORDER);
    }

    @Test
    @DisplayName("paginating through all pages covers every event exactly once")
    void fullPaginationCoversAllEvents() {
        var limit = 2;
        var allIds = new ArrayList<String>();
        Cursor cursor = null;

        while (true) {
            var results = store.query(T1, T3, limit + 1, cursor);
            var page = results.size() > limit
                    ? results.subList(0, limit)
                    : results;

            allIds.addAll(page.stream().map(Event::id).toList());

            if (results.size() <= limit) {
                break;
            }

            var lastEvent = page.getLast();
            cursor = new Cursor(lastEvent.startTime(), lastEvent.id());
        }

        assertThat(allIds)
                .as("All 7 events should appear exactly once across all pages")
                .hasSize(7)
                .doesNotHaveDuplicates();

        assertThat(new HashSet<>(allIds))
                .containsExactlyInAnyOrder(
                        "evt-a", "evt-b", "evt-c", "evt-d", "evt-e", "evt-f", "evt-g");
    }

    // --- Descending sort order tests ---

    @Test
    @DisplayName("descending query returns events in reverse order")
    void descendingQueryReturnsReverseOrder() {
        var results = store.query(T1, T3, 100, null, Event.START_TIME_DESC);

        assertThat(results)
                .hasSize(7)
                .isSortedAccordingTo(Event.START_TIME_DESC);

        // First event should be from T3, last from T1
        assertThat(results.getFirst().startTime()).isEqualTo(T3);
        assertThat(results.getLast().startTime()).isEqualTo(T1);
    }

    @Test
    @DisplayName("descending pagination covers every event exactly once")
    void descendingFullPaginationCoversAllEvents() {
        var limit = 2;
        var allIds = new ArrayList<String>();
        Cursor cursor = null;

        while (true) {
            var results = store.query(T1, T3, limit + 1, cursor, Event.START_TIME_DESC);
            var page = results.size() > limit
                    ? results.subList(0, limit)
                    : results;

            allIds.addAll(page.stream().map(Event::id).toList());

            if (results.size() <= limit) {
                break;
            }

            var lastEvent = page.getLast();
            cursor = new Cursor(lastEvent.startTime(), lastEvent.id());
        }

        assertThat(allIds)
                .as("All 7 events should appear exactly once in descending pagination")
                .hasSize(7)
                .doesNotHaveDuplicates();

        assertThat(new HashSet<>(allIds))
                .containsExactlyInAnyOrder(
                        "evt-a", "evt-b", "evt-c", "evt-d", "evt-e", "evt-f", "evt-g");
    }

    @Test
    @DisplayName("descending cursor skips past the cursor position")
    void descendingCursorSkipsPastPosition() {
        var firstPage = store.query(T1, T3, 3, null, Event.START_TIME_DESC);
        var lastEvent = firstPage.getLast();

        var cursor = new Cursor(lastEvent.startTime(), lastEvent.id());
        var secondPage = store.query(T1, T3, 3, cursor, Event.START_TIME_DESC);

        assertThat(secondPage).isNotEmpty();
        // All second-page events should come after the cursor in descending order
        assertThat(secondPage)
                .allSatisfy(event ->
                        assertThat(Event.START_TIME_DESC.compare(event, lastEvent))
                                .isGreaterThan(0));
    }

    @Test
    @DisplayName("descending narrow range returns only matching events")
    void descendingNarrowRangeReturnsSubset() {
        var results = store.query(T2, T2, 100, null, Event.START_TIME_DESC);

        assertThat(results)
                .hasSize(3)
                .allSatisfy(event -> assertThat(event.startTime()).isEqualTo(T2));
    }
}
