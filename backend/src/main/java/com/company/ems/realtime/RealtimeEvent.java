package com.company.ems.realtime;

import java.time.LocalDateTime;

/**
 * Envelope pushed to browsers. {@code type} is the event name, {@code data} the
 * event-specific payload and {@code at} the server timestamp.
 */
public record RealtimeEvent(String type, Object data, LocalDateTime at) {

    public static RealtimeEvent of(String type, Object data) {
        return new RealtimeEvent(type, data, LocalDateTime.now());
    }
}
