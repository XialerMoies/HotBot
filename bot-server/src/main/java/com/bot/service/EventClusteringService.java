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
@lombok.extern.slf4j.Slf4j
public class EventClusteringService {
    static final double AUTO_ASSIGN_THRESHOLD = 0.72;
    static final double CANDIDATE_THRESHOLD = 0.58;
    private static final Duration TIME_WINDOW = Duration.ofDays(14);

    private final Map<String, ClusterState> clusters = new LinkedHashMap<>();
    private final Map<String, NewsItem> articles = new LinkedHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Path stateFile;
    private final boolean enforceContentPolicy;
    private final NewsContentPolicy contentPolicy = new NewsContentPolicy();
    private final EventEntityResolver entityResolver = new EventEntityResolver();

    public EventClusteringService() {
        this(Path.of(System.getProperty("hotbot.event.state", "storage/events.json")), true);
    }

    EventClusteringService(Path stateFile) {
        this(stateFile, false); // Package-private algorithm fixtures use isolated temporary state.
    }

    EventClusteringService(Path stateFile, boolean enforceContentPolicy) {
        this.stateFile = stateFile;
        this.enforceContentPolicy = enforceContentPolicy;
        if (enforceContentPolicy) {
            try {
                var report = new EventDataMaintenance().clean(stateFile, true);
                log.info("Event scope maintenance: {}", report);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Event maintenance failed; original state preserved", exception);
            }
        }
        restore();
        if (enforceContentPolicy) refreshStoredFeatures();
    }

    public synchronized EventClusterResponse cluster(EventClusterRequest request) {
        validate(request);
        if (enforceContentPolicy) {
            NewsItem article = articles.get(request.articleId());
            if (article == null || !request.title().equals(article.getTitle()))
                throw new IllegalArgumentException("Source article required; use /api/events/cluster/news with source and URL");
            contentPolicy.requireAccepted(article);
        }
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
        if (item == null || item.getId() == null || item.getId().isBlank() || item.getTitle() == null || item.getTitle().isBlank())
            throw new IllegalArgumentException("Article id and title are required");
        if (enforceContentPolicy) contentPolicy.requireAccepted(item);
        item = objectMapper.convertValue(item, NewsItem.class); // Caller mutations cannot corrupt stored identity/features.
        entityResolver.enrich(item);
        final NewsItem incoming = item;
        NewsItem existingArticle = articles.values().stream().filter(a ->
                a.getId().equals(incoming.getId()) || (incoming.getUrl() != null && incoming.getUrl().equals(a.getUrl())
                && java.util.Objects.equals(incoming.getSource(), a.getSource())))
                .filter(a -> clusters.values().stream().anyMatch(c -> c.event().articleIds().contains(a.getId())))
                .findFirst().orElse(null);
        if (existingArticle != null) return cluster(requestFor(existingArticle));
        articles.putIfAbsent(item.getId(), item);
        return cluster(requestFor(item));
    }

    private static EventClusterRequest requestFor(NewsItem item) {
        return new EventClusterRequest(item.getId(), item.getTitle(), EventEntityResolver.text(item), parseTime(item.getPublishTime()),
                item.getEntities(), item.getKeywords(), item.getEmbedding() == null ? new double[0]
                : item.getEmbedding().stream().mapToDouble(Float::doubleValue).toArray(), item.getEntityMentions());
    }

    public synchronized void registerArticles(List<NewsItem> items) {
        if (items == null) return;
        items.stream().filter(item -> item != null && item.getId() != null)
                .filter(item -> !enforceContentPolicy || contentPolicy.evaluate(item).accepted())
                .forEach(item -> articles.putIfAbsent(item.getId(), item));
    }

    private static Instant parseTime(String value) {
        return com.bot.util.NewsTimeUtils.parseInstant(value);
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

    public synchronized void saveArticleContent(String id, String body, String status, String fetchedAt) {
        NewsItem article=articles.get(id);
        if(article==null) throw new IllegalArgumentException("article not found");
        if(body!=null && !body.isBlank()) article.setFullBody(body);
        article.setContentStatus(status); article.setContentFetchedAt(fetchedAt);
        persist();
    }

    private TechEvent create(EventClusterRequest request) {
        String id = "event-" + UUID.randomUUID();
        ClusterState state = ClusterState.initial(id, request);
        clusters.put(id, state);
        persist();
        return state.event();
    }

    private Match score(EventClusterRequest request, ClusterState cluster) {
        if (articles.containsKey(request.articleId())) {
            // Match against original reports, never a centroid that drifts between unrelated incidents.
            // The first report bounds cluster lifetime so daily updates cannot chain forever.
            if (request.publishedAt() == null || cluster.event().firstSeenAt() == null
                    || Math.abs(Duration.between(request.publishedAt(), cluster.event().firstSeenAt()).toSeconds()) >= TIME_WINDOW.toSeconds())
                return new Match(cluster, 0, List.of("outside-time-window"));
            return cluster.event().articleIds().stream().map(articles::get).filter(java.util.Objects::nonNull)
                    .map(article -> EventMatchPolicy.compare(request, requestFor(article)))
                    .map(result -> new Match(cluster, result.score(), result.signals()))
                    .max(java.util.Comparator.comparingDouble(Match::score))
                    .orElse(new Match(cluster, 0, List.of("missing-article")));
        }
        if (request.publishedAt() == null || timeScore(request.publishedAt(), cluster.event().firstSeenAt()) == 0)
            return new Match(cluster, 0, List.of("outside-time-window"));
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
            Path staged = Files.createTempFile(stateFile.toAbsolutePath().getParent(), "events-save-", ".tmp");
            try {
                objectMapper.writeValue(staged.toFile(), clusters.values().stream()
                        .map(state -> PersistedCluster.from(state, articles)).toList());
                Files.move(staged, stateFile.toAbsolutePath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(staged); }
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to persist event state", exception);
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
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to restore event state; refusing to overwrite", exception);
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

    /** Backfill only evidence-supported subjects/timestamps; keep public event IDs and grouping stable. */
    private void refreshStoredFeatures() {
        if (clusters.isEmpty()) return;
        try {
            String before = objectMapper.writeValueAsString(clusters.values().stream().map(s -> PersistedCluster.from(s, articles)).toList());
            articles.values().forEach(entityResolver::enrich);
            for (var entry : new ArrayList<>(clusters.entrySet())) {
                ClusterState old = entry.getValue();
                List<EntityMention> mentions = new ArrayList<>();
                LinkedHashSet<String> entities = new LinkedHashSet<>(), keywords = new LinkedHashSet<>();
                List<TechEvent.TimelineNode> timeline = new ArrayList<>();
                for (String id : old.event().articleIds()) {
                    NewsItem article = articles.get(id);
                    if (article == null) continue;
                    mentions.addAll(article.getEntityMentions()); entities.addAll(article.getEntities()); keywords.addAll(article.getKeywords());
                    Instant published = parseTime(article.getPublishTime());
                    if (published == null) published = old.event().timeline().stream().filter(n -> n.articleId().equals(id))
                            .map(TechEvent.TimelineNode::occurredAt).filter(java.util.Objects::nonNull).findFirst().orElse(old.event().firstSeenAt());
                    timeline.add(new TechEvent.TimelineNode(id, published, "UPDATE", article.getTitle()));
                }
                if (timeline.isEmpty()) continue;
                timeline = chronological(timeline);
                var event = old.event();
                var request = new EventClusterRequest("backfill", event.name(), event.name(), timeline.get(0).occurredAt(),
                        new ArrayList<>(entities), new ArrayList<>(keywords), old.embedding(), mentions);
                var updated = new TechEvent(event.id(), event.name(), event.status(), timeline.get(0).occurredAt(), timeline.get(timeline.size()-1).occurredAt(),
                        new ArrayList<>(entities), inferSubjects(request), event.articleIds(), event.confidence(), timeline);
                clusters.put(entry.getKey(), new ClusterState(updated, old.embedding(), ClusterState.normalizeSet(new ArrayList<>(entities)), keywords));
            }
            String after = objectMapper.writeValueAsString(clusters.values().stream().map(s -> PersistedCluster.from(s, articles)).toList());
            if (!before.equals(after)) {
                Path backup = stateFile.toAbsolutePath().getParent().resolve("quarantine").resolve("features-" + Instant.now().toEpochMilli());
                Files.createDirectories(backup);
                Files.copy(stateFile, backup.resolve("events.backup.json"));
                persist();
                log.info("Backfilled subjects and publication times for {} events; backup={}", clusters.size(), backup);
            }
        } catch (Exception exception) { throw new IllegalStateException("Feature backfill failed; original backup preserved", exception); }
    }

    private static List<TechEvent.TimelineNode> chronological(List<TechEvent.TimelineNode> nodes) {
        List<TechEvent.TimelineNode> sorted = nodes.stream().sorted(java.util.Comparator.comparing(TechEvent.TimelineNode::occurredAt,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).thenComparing(TechEvent.TimelineNode::articleId)).toList();
        List<TechEvent.TimelineNode> result = new ArrayList<>();
        for (int i=0;i<sorted.size();i++) {
            var n=sorted.get(i);
            result.add(new TechEvent.TimelineNode(n.articleId(), n.occurredAt(), i==0 ? "FIRST_REPORT" : "UPDATE", n.summary()));
        }
        return result;
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
                timeline = chronological(timeline);
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
            incoming.forEach(subject -> merged.merge(normalize(subject.name()), subject,
                    (old, next) -> old.type().equals("UNKNOWN") ? next : old));
            List<EventSubject> result = new ArrayList<>();
            merged.values().forEach(subject -> result.add(new EventSubject(subject.name(), subject.type(),
                    result.isEmpty() ? "PRIMARY" : "RELATED", subject.confidence())));
            return result;
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
                .sorted(java.util.Comparator.<EntityMention>comparingInt(value ->
                        new EventEntityResolver().resolve(request.title(), List.of(value)).stream()
                                .anyMatch(found -> found.name().equalsIgnoreCase(value.name())) ? 1 : 0).reversed()
                        .thenComparing(java.util.Comparator.comparingInt((EntityMention value) -> subjectPriority(value.type())).reversed()))
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
            case "ORG", "ORGANIZATION", "COMPANY" -> 5;
            case "PERSON" -> 4;
            case "PRODUCT" -> 3;
            case "TECHNOLOGY", "TECH" -> 2;
            default -> 1;
        };
    }

    private static String subjectType(String type) {
        return switch (type == null ? "" : type.toUpperCase(Locale.ROOT)) {
            case "COMPANY" -> "COMPANY";
            case "ORG", "ORGANIZATION" -> "ORGANIZATION";
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
