package com.bot.service;

import com.bot.config.AppConfig;
import com.bot.model.EvidenceAnswerRequest;
import com.bot.model.EvidenceAnswerResponse;
import com.bot.model.NewsItem;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceAnswerService {
    private final RestTemplate restTemplate;
    private final AppConfig.BotConfig config;
    private final EventClusteringService clusteringService;

    public EvidenceAnswerService(RestTemplate restTemplate, AppConfig.BotConfig config, EventClusteringService clusteringService) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.clusteringService = clusteringService;
    }

    public EvidenceAnswerResponse answerEvent(String eventId, String question) {
        List<EvidenceAnswerRequest.EvidenceItem> evidence = clusteringService.articlesFor(eventId).stream()
                .map(this::toEvidence).toList();
        return answer(new EvidenceAnswerRequest(question, evidence));
    }

    private EvidenceAnswerRequest.EvidenceItem toEvidence(NewsItem item) {
        String quote = item.preferredDetailText();
        if (quote == null || quote.isBlank()) quote = item.summaryPreviewText();
        if (quote == null || quote.isBlank()) quote = item.getTitle();
        return new EvidenceAnswerRequest.EvidenceItem("article-" + item.getId(), item.getId(), quote,
                item.getTitle(), item.getUrl(), item.getPublishTime());
    }

    @SuppressWarnings("unchecked")
    public EvidenceAnswerResponse answer(EvidenceAnswerRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("question is required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("question", request.question());
        body.put("evidence", request.evidence().stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", item.id()); value.put("article_id", item.articleId());
            value.put("quote", item.quote()); value.put("title", item.title());
            value.put("url", item.url()); value.put("published_at", item.publishedAt());
            return value;
        }).toList());
        try {
            Map<String, Object> response = restTemplate.postForObject(
                    config.getMlServerUrl() + "/api/evidence/answer", body, Map.class);
            if (response == null || !(response.get("answer") instanceof String text) || text.isBlank()) {
                throw new IllegalStateException("Evidence model returned no answer");
            }
            return new EvidenceAnswerResponse(
                    String.valueOf(response.getOrDefault("answer", "")),
                    (List<Map<String, Object>>) response.getOrDefault("claims", List.of()),
                    (List<Map<String, Object>>) response.getOrDefault("evidence", List.of()),
                    Boolean.TRUE.equals(response.get("fallback")));
        } catch (Exception ignored) {
            List<Map<String, Object>> evidence = new ArrayList<>();
            List<Map<String, Object>> claims = new ArrayList<>();
            StringBuilder answer = new StringBuilder("基于检索到的原文：");
            for (EvidenceAnswerRequest.EvidenceItem item : request.evidence()) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", item.id()); value.put("articleId", item.articleId());
                value.put("quote", item.quote()); value.put("url", item.url());
                evidence.add(value);
                claims.add(Map.of("text", item.quote(), "evidenceIds", List.of(item.id())));
                answer.append("\n- ").append(item.quote()).append(" [").append(item.id()).append("]");
            }
            if (request.evidence().isEmpty()) answer.append("\n暂无足够证据。");
            return new EvidenceAnswerResponse(answer.toString(), claims, evidence, true);
        }
    }
}
