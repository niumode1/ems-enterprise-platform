package com.company.ems.notification;

import lombok.Data;

import java.util.List;

public final class NotificationPreferenceDtos {

    private NotificationPreferenceDtos() {
    }

    public record PreferenceItem(String type, String label, boolean muted) {
    }

    public record PreferencesView(boolean enabled, String behavior,
                                  List<PreferenceItem> preferences) {
    }

    @Data
    public static class UpdateRequest {
        private List<Setting> preferences;
    }

    @Data
    public static class Setting {
        private String type;
        private Boolean muted;
    }
}
