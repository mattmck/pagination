package io.github.mattmck.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Events API application.
 *
 * <p>Loads event data from CSV at startup and exposes a cursor-based
 * paginated REST endpoint for querying events by date range.</p>
 */
@SpringBootApplication
public class EventsApplication {

    /** Creates a new application instance. */
    public EventsApplication() {
        // default constructor
    }

    /**
     * Application entry point. Starts the Spring Boot embedded server.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(EventsApplication.class, args);
    }
}
