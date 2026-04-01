package io.github.mattmck.events.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Raw representation of a single row from the events CSV file.
 *
 * <p>Maps directly to CSV columns in order: {@code row_index}, {@code start_time},
 * {@code id}, {@code payload}. Multiple rows may share the same {@code id} when the
 * source data contains duplicate entries for a logical event.</p>
 *
 * @param rowIndex  positional index of this row in the CSV (1-based)
 * @param startTime Unix timestamp in seconds for the event start
 * @param id        logical event identifier (e.g. {@code "evt-a1"})
 * @param payload   human-readable event description
 */
@JsonPropertyOrder({"row_index", "start_time", "id", "payload"})
public record CsvRow(int rowIndex, long startTime, String id, String payload) {
}
