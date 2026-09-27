package com.bot.model;

/** A first-class subject in an event frame, separate from incidental entities. */
public record EventSubject(String name, String type, String role, double confidence) {
    public EventSubject {
        name = name == null ? "" : name.trim();
        type = type == null || type.isBlank() ? "UNKNOWN" : type.trim().toUpperCase();
        role = role == null || role.isBlank() ? "RELATED" : role.trim().toUpperCase();
        confidence = Math.max(0, Math.min(1, confidence));
    }
}
