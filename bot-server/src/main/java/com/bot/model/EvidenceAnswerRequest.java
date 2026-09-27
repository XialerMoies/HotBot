package com.bot.model;

import java.util.List;

public record EvidenceAnswerRequest(String question, List<EvidenceItem> evidence) {
    public EvidenceAnswerRequest {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public record EvidenceItem(String id, String articleId, String quote, String title, String url, String publishedAt) {}
}
