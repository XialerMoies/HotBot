package com.bot.service;

import com.bot.model.EventClusterRequest;
import com.bot.model.EventClusterResponse;
import com.bot.model.TechEvent;
import com.bot.model.NewsItem;
import com.bot.model.EntityMention;
import com.bot.model.EventSubject;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * In-memory first implementation of incremental event clustering.
 * Persistence can be added behind this service without changing the scoring contract.
 */
@Service
public class EventClusteringService {
    static final double AUTO_ASSIGN_THRESHOLD = 0.72;
    static final double CANDIDATE_THRESHOLD = 0.58;
    private static final Duration TIME_WINDOW = Duration.ofDays(14);

    private final Map<String, ClusterState> clusters = new LinkedHashMap<>();
    private final Map<String, NewsItem> articles = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Path stateFile;

    public EventClusteringService() {
        this(Path.of(System.getProperty("hotbot.event.state", "storage/events.json")));
    }

    EventClusteringService(Path stateFile) {
        this.stateFile = stateFile;
        restore();
    }

    public synchronized EventClusterResponse cluster(EventClusterRequest request) {
        validate(request);
        ClusterState existing = clusters.values().stream()
                .filter(cluster -> cluster.event().articleIds().contains(request.articleId()))
                .findFirst().orElse(null);
        if (existing != null) {
            return new EventClusterResponse(existing.event(), false, 1.0, List.of("already-ingested"));
        }
        Match best = clusters.values().stream()
                .map(cluster -> score(request, cluster))
                .filter(match -> match.score() >= CANDIDATE_THRESHOLD)
                .max((left, right) -> Double.compare(left.score(), right.score()))
                .orElse(null);

        if (best == null || best.score() < AUTO_ASSIGN_THRESHOLD) {
            TechEvent created = create(request);
            return new EventClusterResponse(created, true, best == null ? 0.0 : best.score(),
                    best == null ? List.of("new-event") : List.of("candidate-review"));
        }

        ClusterState updated = best.cluster().add(request);
        clusters.put(updated.event().id(), updated);
        persist();
        return new EventClusterResponse(updated.event(), false, best.score(), best.signals());
    }

    public synchronized EventClusterResponse clusterNews(com.bot.model.NewsItem item) {
        if (item != null && item.getId() != null) articles.put(item.getId(), item);
        String text = (item.getTitle() == null ? "" : item.getTitle()) + "\n" + item.summaryPreviewText();
        return cluster(new EventClusterRequest(item.getId(), item.getTitle(), text, parseTime(item.getPublishTime()),
                item.getEntities(), item.getKeywords() == null || item.getKeywords().isEmpty() ? item.getTags() : item.getKeywords(), item.getEmbedding() == null ? new double[0] : item.getEmbedding().stream().mapToDouble(Float::doubleValue).toArray(), item.getEntityMentions()));
    }

    public synchronized void registerArticles(List<NewsItem> items) {
        if (items == null) return;
        items.stream().filter(item -> item != null && item.getId() != null).forEach(item -> articles.put(item.getId(), item));
    }

    private static Instant parseTime(String value) {
        try { return value == null || value.isBlank() ? Instant.now() : Instant.parse(value); }
        catch (Exception ignored) { return Instant.now(); }
    }

    public synchronized List<TechEvent> list() {
        return clusters.values().stream().map(ClusterState::event).toList();
    }

    public synchronized List<NewsItem> allArticles() {
        return List.copyOf(articles.values());
    }

    public synchronized TechEvent find(String id) {
        ClusterState state = clusters.get(id);
        return state == null ? null : state.event();
    }

    public synchronized List<NewsItem> articlesFor(String id) {
        ClusterState state = clusters.get(id);
        if (state == null) return List.of();
        return state.event().articleIds().stream().map(articles::get).filter(java.util.Objects::nonNull).toList();
    }

    private TechEvent create(EventClusterRequest request) {
        String id = "event-" + UUID.randomUUID();
        ClusterState state = ClusterState.initial(id, request);
        clusters.put(id, state);
        persist();
        return state.event();
    }

    private Match score(EventClusterRequest request, ClusterState cluster) {
        double semantic = cosine(request.embedding(), cluster.embedding());
        double entity = overlap(request.entities(), cluster.entities());
        double keyword = overlap(request.keywords(), cluster.keywords());
        double time = timeScore(request.publishedAt(), cluster.event().lastUpdatedAt());
        double score = 0.55 * semantic + 0.25 * entity + 0.10 * keyword + 0.10 * time;
        List<String> signals = new ArrayList<>();
        if (semantic > 0) signals.add("semantic");
        if (entity > 0) signals.add("entity");
        if (keyword > 0) signals.add("keyword");
        if (time > 0) signals.add("time-window");
        return new Match(cluster, score, signals);
    }

    private static double cosine(double[] left, double[] right) {
        if (left.length == 0 || right.length == 0 || left.length != right.length) return 0.0;
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0.0 : Math.max(0, dot / Math.sqrt(leftNorm * rightNorm));
    }

    private static double overlap(List<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        Set<String> normalized = left.stream().map(EventClusteringService::normalize).collect(Collectors.toSet());
        long intersection = normalized.stream().filter(right::contains).count();
        return intersection / (double) Math.max(normalized.size(), right.size());
    }

    private static double timeScore(Instant left, Instant right) {
        if (left == null || right == null) return 0.0;
        long hours = Math.abs(Duration.between(left, right).toHours());
        return hours >= TIME_WINDOW.toHours() ? 0.0 : 1.0 - hours / (double) TIME_WINDOW.toHours();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static void validate(EventClusterRequest request) {
        if (request == null || request.articleId() == null || request.articleId().isBlank()) {
            throw new IllegalArgumentException("articleId is required");
        }
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
    }

    private void persist() {
        try {
            Path parent = stateFile.getParent();
            if (parent != null) Files.createDirectories(parent);
            objectMapper.writeValue(stateFile.toFile(), clusters.values().stream()
                    .map(state -> PersistedCluster.from(state, articles)).toList());
        } catch (Exception ignored) {
            // Persistence failure must not break ingestion; the task log can report it later.
        }
    }

    private void restore() {
        if (!Files.exists(stateFile)) return;
        try {
            PersistedCluster[] saved = objectMapper.readValue(Files.readString(stateFile, StandardCharsets.UTF_8), PersistedCluster[].class);
            for (PersistedCluster value : saved) {
                TechEvent restoredEvent = migrateSubjects(value.event);
                if (restoredEvent != null) clusters.put(restoredEvent.id(), new ClusterState(restoredEvent,
                        value.embedding == null ? new double[0] : value.embedding,
                        value.entities == null ? Set.of() : new LinkedHashSet<>(value.entities),
                        value.keywords == null ? Set.of() : new LinkedHashSet<>(value.keywords)));
                if (value.articles != null) value.articles.forEach(article -> articles.put(article.getId(), article));
            }
        } catch (Exception ignored) {
            // Ignore malformed stale state and start clean; new events will overwrite it.
        }
    }

    private static TechEvent migrateSubjects(TechEvent event) {
        if (event == null || !event.subjects().isEmpty() || event.entities().isEmpty()) return event;
        List<EventSubject> subjects = event.entities().stream().filter(value -> value != null && !value.isBlank())
                .limit(5)
                .map(value -> new EventSubject(value, "UNKNOWN", "PRIMARY", 0.35))
                .toList();
        return new TechEvent(event.id(), event.name(), event.status(), event.firstSeenAt(), event.lastUpdatedAt(),
                event.entities(), subjects, event.articleIds(), event.confidence(), event.timeline());
    }

    private record Match(ClusterState cluster, double score, List<String> signals) {}

    private static final class PersistedCluster {
        public TechEvent event;
        public double[] embedding;
        public List<String> entities;
        public List<String> keywords;
        public List<NewsItem> articles;
        static PersistedCluster from(ClusterState state, Map<String, NewsItem> articles) {
            PersistedCluster value = new PersistedCluster();
            value.event = state.event();
            value.embedding = state.embedding();
            value.entities = new ArrayList<>(state.entities());
            value.keywords = new ArrayList<>(state.keywords());
            value.articles = state.event().articleIds().stream().map(articles::get)
                    .filter(java.util.Objects::nonNull).toList();
            return value;
        }
    }

    private record ClusterState(TechEvent event, double[] embedding, Set<String> entities, Set<String> keywords) {
        static ClusterState initial(String id, EventClusterRequest request) {
            Instant now = request.publishedAt() == null ? Instant.now() : request.publishedAt();
            TechEvent event = new TechEvent(id, request.title(), "EMERGING", now, now,
                    request.entities(), inferSubjects(request), List.of(request.articleId()), 1.0,
                    List.of(new TechEvent.TimelineNode(request.articleId(), now, "FIRST_REPORT", request.title())));
            return new ClusterState(event, request.embedding().clone(), normalizeSet(request.entities()), normalizeSet(request.keywords()));
        }

        ClusterState add(EventClusterRequest request) {
            Instant published = request.publishedAt() == null ? Instant.now() : request.publishedAt();
            Instant first = event.firstSeenAt().isBefore(published) ? event.firstSeenAt() : published;
            Instant latest = event.lastUpdatedAt().isAfter(published) ? event.lastUpdatedAt() : published;
            Set<String> mergedEntities = new LinkedHashSet<>(entities);
            mergedEntities.addAll(normalizeSet(request.entities()));
            Set<String> mergedKeywords = new LinkedHashSet<>(keywords);
            mergedKeywords.addAll(normalizeSet(request.keywords()));
            List<String> articles = new ArrayList<>(event.articleIds());
            if (!articles.contains(request.articleId())) articles.add(request.articleId());
            List<TechEvent.TimelineNode> timeline = new ArrayList<>(event.timeline());
            if (timeline.stream().noneMatch(node -> node.articleId().equals(request.articleId()))) {
                timeline.add(new TechEvent.TimelineNode(request.articleId(), published, "UPDATE", request.title()));
                timeline.sort(java.util.Comparator.comparing(TechEvent.TimelineNode::occurredAt));
            }
            List<EventSubject> mergedSubjects = mergeSubjects(event.subjects(), inferSubjects(request));
            TechEvent updated = new TechEvent(event.id(), event.name(), "ONGOING", first, latest,
                    new ArrayList<>(mergedEntities), mergedSubjects, articles, Math.min(1.0, 0.5 + articles.size() * 0.1), timeline);
            return new ClusterState(updated, embedding.length == 0 ? request.embedding().clone() : embedding,
                    mergedEntities, mergedKeywords);
        }

        private static List<EventSubject> mergeSubjects(List<EventSubject> existing, List<EventSubject> incoming) {
            Map<String, EventSubject> merged = new LinkedHashMap<>();
            existing.forEach(subject -> merged.put(normalize(subject.name()), subject));
            incoming.forEach(subject -> merged.putIfAbsent(normalize(subject.name()), subject));
            return new ArrayList<>(merged.values());
        }

        private static Set<String> normalizeSet(List<String> values) {
            return values.stream().map(EventClusteringService::normalize).filter(value -> !value.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static List<EventSubject> inferSubjects(EventClusterRequest request) {
        List<EntityMention> mentions = request.entityMentions() == null ? List.of() : request.entityMentions();
        List<EventSubject> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        mentions.stream()
                .filter(value -> value != null && value.name() != null && !value.name().isBlank())
                .sorted((left, right) -> Integer.compare(subjectPriority(right.type()), subjectPriority(left.type())))
                .forEach(value -> {
                    String key = normalize(value.name());
                    if (seen.add(key)) {
                        result.add(new EventSubject(value.name(), subjectType(value.type()), result.isEmpty() ? "PRIMARY" : "RELATED",
                                subjectConfidence(value.type())));
                    }
                });
        if (result.isEmpty()) {
            request.entities().stream().filter(value -> value != null && !value.isBlank()).forEach(value -> {
                if (seen.add(normalize(value))) result.add(new EventSubject(value, "UNKNOWN", result.isEmpty() ? "PRIMARY" : "RELATED", 0.35));
            });
        }
        return result.stream().limit(5).toList();
    }

    private static int subjectPriority(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "ORG", "COMPANY" -> 5;
            case "PERSON" -> 4;
            case "PRODUCT" -> 3;
            case "TECHNOLOGY", "TECH" -> 2;
            default -> 1;
        };
    }

    private static String subjectType(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "ORG" -> "COMPANY";
            case "PERSON" -> "PERSON";
            case "PRODUCT" -> "PRODUCT";
            case "TECHNOLOGY", "TECH" -> "TECHNOLOGY";
            default -> "UNKNOWN";
        };
    }

    private static double subjectConfidence(String type) {
        return switch (subjectPriority(type)) {
            case 5 -> 0.9;
            case 4 -> 0.82;
            case 3 -> 0.75;
            case 2 -> 0.68;
            default -> 0.35;
        };
    }
}
