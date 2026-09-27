package com.bot.model;

/** A named entity mention retained from the NER service before event framing. */
public record EntityMention(String name, String type) {
    public EntityMention {
        name = name == null ? "" : name.trim();
        type = type == null || type.isBlank() ? "UNKNOWN" : type.trim().toUpperCase();
    }
}
