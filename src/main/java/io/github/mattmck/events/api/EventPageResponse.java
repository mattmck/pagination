package io.github.mattmck.events.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.mattmck.events.model.Event;

import java.util.List;

/**
 * Response body for the paginated events endpoint.
 *
 * <p>Contains the current page of events and an optional cursor for retrieving the next
 * page. When {@code nextCursor} is {@code null}, the client has reached the end of the
 * result set.</p>
 *
 * @param events     the events on this page, sorted by startTime then id
 * @param nextCursor opaque cursor for the next page, or {@code null} if this is the last page
 */
public record EventPageResponse(
        List<Event> events,
        @JsonInclude(JsonInclude.Include.ALWAYS) String nextCursor) {
}
