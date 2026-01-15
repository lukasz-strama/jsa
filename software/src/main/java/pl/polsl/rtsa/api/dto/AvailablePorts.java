package pl.polsl.rtsa.api.dto;

import java.util.List;

/**
 * Data Transfer Object for available serial ports.
 * Immutable record for thread-safe access.
 *
 * @param ports            List of available port names.
 * @param lastRefreshTime  Unix timestamp of last refresh.
 */
public record AvailablePorts(
        List<String> ports,
        long lastRefreshTime
) {
    /**
     * Creates an empty ports list with timestamp = 0 to force refresh on first use.
     */
    public static AvailablePorts empty() {
        return new AvailablePorts(List.of(), 0);
    }

    /**
     * Checks if any ports are available.
     */
    public boolean hasAvailablePorts() {
        return ports != null && !ports.isEmpty();
    }

    /**
     * Returns the number of available ports.
     */
    public int count() {
        return ports != null ? ports.size() : 0;
    }
}
