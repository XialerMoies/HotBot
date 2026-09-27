package com.bot.model;

import java.util.List;

public record EvidenceAnswerRequest(String question, List<EvidenceItem> evidence) {
    public EvidenceAnswerRequest {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public record EvidenceItem(String id, String articleId, String quote, String title, String url, String publishedAt,
                               String kind, int startOffset, int endOffset, String source, String contentHash) {
        public EvidenceItem(String id, String articleId, String quote, String title, String url, String publishedAt) {
            this(id, articleId, quote, title, url, publishedAt, "EXCERPT", 0, quote == null ? 0 : quote.length(), "", "");
        }
    }
}
