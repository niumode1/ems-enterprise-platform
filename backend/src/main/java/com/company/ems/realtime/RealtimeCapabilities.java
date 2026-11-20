package com.company.ems.realtime;

/**
 * Company-level realtime policy returned to the frontend so it can decide whether
 * to open a WebSocket and how often to send heartbeats.
 */
public record RealtimeCapabilities(boolean enabled,
                                   boolean chatEnabled,
                                   boolean notificationEnabled,
                                   int heartbeatSeconds,
                                   int maxConnectionsPerUser) {
}
