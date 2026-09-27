package com.bot.model;

import java.time.Instant;
import java.util.List;

public record EventClusterRequest(
        String articleId,
        String title,
        String text,
        Instant publishedAt,
        List<String> entities,
        List<String> keywords,
        double[] embedding,
        List<EntityMention> entityMentions) {

    public EventClusterRequest {
        entities = entities == null ? List.of() : List.copyOf(entities);
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        embedding = embedding == null ? new double[0] : embedding.clone();
        entityMentions = entityMentions == null ? List.of() : List.copyOf(entityMentions);
    }

    public EventClusterRequest(String articleId, String title, String text, Instant publishedAt,
                               List<String> entities, List<String> keywords, double[] embedding) {
        this(articleId, title, text, publishedAt, entities, keywords, embedding, List.of());
    }
}
