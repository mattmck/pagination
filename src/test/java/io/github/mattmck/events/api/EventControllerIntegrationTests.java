package io.github.mattmck.events.api;

import io.github.mattmck.events.model.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the events endpoint using the full application context
 * and real HTTP calls.
 *
 * <p>These tests verify the core pagination contract: paginated totals match
 * non-paginated totals, no event ID appears on more than one page, and every
 * event in the date range appears exactly once across all pages.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventControllerIntegrationTests {

    /** Earliest startTime in sample_data.csv (Feb 23 2024 00:00 UTC). */
    private static final long DATA_START = 1708646400L;

    /** Latest startTime in sample_data.csv (Mar 10 2024 00:00 UTC). */
    private static final long DATA_END = 1710028800L;

    /** Total unique event IDs in sample_data.csv after deduplication. */
    private static final int TOTAL_UNIQUE_EVENTS = 34;

    @Autowired
    private WebTestClient webClient;

    @Test
    @DisplayName("full range with large limit returns all events and null cursor")
    void fullRangeNoPaginationReturnsAllEvents() {
        webClient.get()
                .uri("/api/events?start_time={s}&end_time={e}&limit=100",
                        DATA_START, DATA_END)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events.length()").isEqualTo(TOTAL_UNIQUE_EVENTS)
                .jsonPath("$.next_cursor").isEqualTo(null);
    }

    @Test
    @DisplayName("paginated totals match non-paginated totals")
    void paginatedTotalMatchesNonPaginatedTotal() {
        var allIds = collectAllPaginatedIds(5);

        assertThat(allIds).hasSize(TOTAL_UNIQUE_EVENTS);
    }

    @Test
    @DisplayName("no duplicate IDs appear across pages")
    void noDuplicateIdsAcrossPages() {
        var allIds = collectAllPaginatedIds(5);

        assertThat(allIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("no missing IDs — every event appears exactly once")
    void noMissingIdsAcrossPages() {
        var paginatedIds = new HashSet<>(collectAllPaginatedIds(5));

        // Get all IDs in a single request for comparison
        var singleRequestIds = new HashSet<>(collectAllPaginatedIds(200));

        assertThat(paginatedIds).isEqualTo(singleRequestIds);
    }

    @Test
    @DisplayName("empty date range returns empty events and null cursor")
    void emptyDateRangeReturnsEmptyResponse() {
        webClient.get()
                .uri("/api/events?start_time=9999999999&end_time=9999999999&limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events.length()").isEqualTo(0)
                .jsonPath("$.next_cursor").isEqualTo(null);
    }

    @Test
    @DisplayName("single page result returns null cursor")
    void singlePageReturnsNullCursor() {
        // Query a range that has fewer events than the limit
        webClient.get()
                .uri("/api/events?start_time={s}&end_time={e}&limit=100",
                        DATA_START, DATA_START)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.next_cursor").isEqualTo(null)
                .jsonPath("$.events.length()").value(count ->
                        assertThat((int) count).isLessThanOrEqualTo(100));
    }

    @Test
    @DisplayName("invalid cursor returns 400")
    void invalidCursorReturns400() {
        webClient.get()
                .uri("/api/events?start_time={s}&end_time={e}&cursor=!!!garbage!!!",
                        DATA_START, DATA_END)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("missing start_time returns 400")
    void missingStartTimeReturns400() {
        webClient.get()
                .uri("/api/events?end_time={e}", DATA_END)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("missing end_time returns 400")
    void missingEndTimeReturns400() {
        webClient.get()
                .uri("/api/events?start_time={s}", DATA_START)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("limit defaults to 20 when omitted")
    void limitDefaultsTo20() {
        webClient.get()
                .uri("/api/events?start_time={s}&end_time={e}", DATA_START, DATA_END)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events.length()").isEqualTo(20)
                .jsonPath("$.next_cursor").isNotEmpty();
    }

    @Test
    @DisplayName("boundary timestamps are inclusive")
    void boundaryTimestampsAreInclusive() {
        // Query exactly the first timestamp — should include events at that time
        webClient.get()
                .uri("/api/events?start_time={s}&end_time={e}&limit=100",
                        DATA_START, DATA_START)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events.length()").value(count ->
                        assertThat((int) count).isGreaterThan(0))
                .jsonPath("$.events[0].start_time").isEqualTo(DATA_START);
    }

    @Test
    @DisplayName("pagination works correctly with various limit sizes")
    void paginationWorksWithVariousLimits() {
        for (var limit : List.of(1, 3, 7, 10, 17, 34)) {
            var ids = collectAllPaginatedIds(limit);

            assertThat(ids)
                    .as("limit=%d should produce %d unique events", limit, TOTAL_UNIQUE_EVENTS)
                    .hasSize(TOTAL_UNIQUE_EVENTS)
                    .doesNotHaveDuplicates();
        }
    }

    // --- Sorting tests ---

    @Test
    @DisplayName("direction=desc returns events in descending order")
    void descDirectionReturnsDescendingOrder() {
        var response = webClient.get()
                .uri("/api/events?start_time={s}&end_time={e}&limit=100&direction=desc",
                        DATA_START, DATA_END)
                .exchange()
                .expectStatus().isOk()
                .expectBody(EventPageResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.events()).hasSize(TOTAL_UNIQUE_EVENTS);

        // Verify descending order: each event's startTime >= next event's startTime
        var events = response.events();
        for (var i = 0; i < events.size() - 1; i++) {
            assertThat(events.get(i).startTime())
                    .isGreaterThanOrEqualTo(events.get(i + 1).startTime());
        }
    }

    @Test
    @DisplayName("descending pagination produces same IDs as ascending")
    void descendingPaginationMatchesAscending() {
        var ascIds = collectAllPaginatedIds(5, "asc");
        var descIds = collectAllPaginatedIds(5, "desc");

        assertThat(new HashSet<>(descIds))
                .as("Descending pagination should cover the same events as ascending")
                .isEqualTo(new HashSet<>(ascIds));
    }

    @Test
    @DisplayName("descending pagination has no duplicates across pages")
    void descendingPaginationNoDuplicates() {
        var ids = collectAllPaginatedIds(5, "desc");

        assertThat(ids)
                .hasSize(TOTAL_UNIQUE_EVENTS)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("descending pagination works with various limit sizes")
    void descendingPaginationWorksWithVariousLimits() {
        for (var limit : List.of(1, 3, 7, 10, 17, 34)) {
            var ids = collectAllPaginatedIds(limit, "desc");

            assertThat(ids)
                    .as("desc limit=%d should produce %d unique events", limit, TOTAL_UNIQUE_EVENTS)
                    .hasSize(TOTAL_UNIQUE_EVENTS)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("invalid direction returns 400")
    void invalidDirectionReturns400() {
        webClient.get()
                .uri("/api/events?start_time={s}&end_time={e}&direction=sideways",
                        DATA_START, DATA_END)
                .exchange()
                .expectStatus().isBadRequest();
    }

    /**
     * Walks through all pages using the given limit and collects every event ID
     * encountered across all pages. Uses ascending sort direction.
     */
    private List<String> collectAllPaginatedIds(int limit) {
        return collectAllPaginatedIds(limit, "asc");
    }

    /**
     * Walks through all pages using the given limit and sort direction, collecting
     * every event ID encountered across all pages.
     */
    private List<String> collectAllPaginatedIds(int limit, String direction) {
        var allIds = new ArrayList<String>();
        String cursor = null;
        var maxPages = (TOTAL_UNIQUE_EVENTS / limit) + 2; // safety cap

        for (var page = 0; page < maxPages; page++) {
            var uri = cursor == null
                    ? "/api/events?start_time=%d&end_time=%d&limit=%d&direction=%s"
                            .formatted(DATA_START, DATA_END, limit, direction)
                    : "/api/events?start_time=%d&end_time=%d&limit=%d&direction=%s&cursor=%s"
                            .formatted(DATA_START, DATA_END, limit, direction, cursor);

            var response = webClient.get()
                    .uri(uri)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(EventPageResponse.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(response).isNotNull();
            assertThat(response.events().size()).isLessThanOrEqualTo(limit);

            for (var event : response.events()) {
                allIds.add(event.id());
            }

            cursor = response.nextCursor();
            if (cursor == null) {
                break;
            }
        }

        return allIds;
    }
}
