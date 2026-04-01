package io.github.mattmck.events.config;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import io.github.mattmck.events.model.CsvRow;
import io.github.mattmck.events.model.Event;
import io.github.mattmck.events.store.EventStore;
import io.github.mattmck.events.store.InMemoryEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.comparingInt;

/**
 * Loads event data from a CSV file and produces a deduplicated, sorted
 * {@link EventStore} bean.
 *
 * <p>This configuration implements a hybrid data pattern: raw CSV rows are parsed
 * in full, then a "materialized view" of deduplicated events is built at startup.
 * Deduplication uses upsert semantics — when multiple rows share the same event
 * {@code id}, the row with the highest {@code rowIndex} wins, ensuring the latest
 * data is always served.</p>
 */
@Configuration
public class CsvDataConfig {

    private static final Logger Log = LoggerFactory.getLogger(CsvDataConfig.class);

    /**
     * Parses the CSV file, deduplicates by event id, and returns an {@link EventStore}
     * backed by the resulting sorted event list.
     *
     * @param csvResource the CSV file to load, resolved from the {@code events.csv-path} property
     * @return a fully initialized {@link EventStore}
     * @throws IOException if the CSV file cannot be read or parsed
     */
    @Bean
    public EventStore eventStore(
            @Value("${events.csv-path}") Resource csvResource) throws IOException {

        var rawRows = parseCsv(csvResource);
        Log.info("Parsed {} raw CSV rows from {}", rawRows.size(), csvResource.getDescription());

        var events = deduplicate(rawRows);
        Log.info("Deduplicated to {} unique events", events.size());

        return new InMemoryEventStore(events);
    }

    /**
     * Parses the CSV resource into a list of raw rows using Jackson's CSV module.
     */
    private List<CsvRow> parseCsv(Resource csvResource) throws IOException {
        var csvMapper = CsvMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        var schema = CsvSchema.builder()
                .addColumn("row_index", CsvSchema.ColumnType.NUMBER)
                .addColumn("start_time", CsvSchema.ColumnType.NUMBER)
                .addColumn("id")
                .addColumn("payload")
                .build()
                .withHeader();

        try (var inputStream = csvResource.getInputStream()) {
            var iterator = csvMapper
                    .readerFor(CsvRow.class)
                    .with(schema)
                    .<CsvRow>readValues(inputStream);

            return iterator.readAll();
        }
    }

    /**
     * Deduplicates rows by event id, keeping the row with the highest rowIndex
     * (upsert semantics). Returns events sorted by {@link Event#SORT_ORDER}.
     */
    private List<Event> deduplicate(List<CsvRow> rawRows) {
        return rawRows.stream()
                .collect(Collectors.toMap(
                        CsvRow::id,
                        row -> row,
                        (existing, replacement) ->
                                replacement.rowIndex() > existing.rowIndex() ? replacement : existing
                ))
                .values()
                .stream()
                .map(row -> new Event(row.startTime(), row.id(), row.payload()))
                .sorted(Event.SORT_ORDER)
                .toList();
    }
}
