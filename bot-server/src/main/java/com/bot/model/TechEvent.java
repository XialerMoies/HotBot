package com.bot.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Event-level view over multiple reports describing the same technology story. */
public record TechEvent(
        String id,
        String name,
        String status,
        Instant firstSeenAt,
        Instant lastUpdatedAt,
        List<String> entities,
        List<EventSubject> subjects,
        List<String> articleIds,
        double confidence,
        List<TimelineNode> timeline) {

    public TechEvent {
        entities = entities == null ? List.of() : List.copyOf(entities);
        subjects = subjects == null ? List.of() : subjects.stream().filter(subject -> subject != null && !subject.name().isBlank()).toList();
        articleIds = articleIds == null ? List.of() : List.copyOf(articleIds);
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }

    /** Compatibility constructor for persisted callers created before subject framing. */
    public TechEvent(String id, String name, String status, Instant firstSeenAt, Instant lastUpdatedAt,
                     List<String> entities, List<String> articleIds, double confidence,
                     List<TimelineNode> timeline) {
        this(id, name, status, firstSeenAt, lastUpdatedAt, entities, List.of(), articleIds, confidence, timeline);
    }

    public record TimelineNode(String articleId, Instant occurredAt, String stage, String summary) {}
}
