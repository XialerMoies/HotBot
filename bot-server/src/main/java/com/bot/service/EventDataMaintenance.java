package com.bot.service;

import com.bot.model.NewsItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Offline/startup migration. Must run before serving or ingesting, never beside a running writer. */
public final class EventDataMaintenance {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final NewsContentPolicy policy = new NewsContentPolicy();

    public Report clean(Path stateFile, boolean apply) throws IOException {
        if (!Files.exists(stateFile)) return new Report(0, 0, 0, Map.of(), null);
        byte[] original = Files.readAllBytes(stateFile);
        JsonNode input = mapper.readTree(original);
        if (input == null || !input.isArray()) throw new IOException("Event state must be an array; original left untouched");
        ArrayNode retained = mapper.createArrayNode();
        ArrayNode quarantine = mapper.createArrayNode();
        Map<String, Integer> reasonCounts = new TreeMap<>();
        Set<String> acceptedArticleIds = new HashSet<>();
        Set<String> acceptedEventIds = new HashSet<>();
        for (JsonNode cluster : input) {
            Set<String> reasons = new LinkedHashSet<>();
            JsonNode event = cluster.path("event");
            JsonNode ids = event.path("articleIds");
            JsonNode articles = cluster.path("articles");
            if (!event.isObject() || event.path("id").asText().isBlank() || !ids.isArray() || ids.isEmpty()
                    || !articles.isArray() || articles.isEmpty()) reasons.add("MISSING_ARTICLE");
            Set<String> actualIds = new HashSet<>();
            if (articles.isArray()) for (JsonNode article : articles) {
                NewsItem news = mapper.treeToValue(article, NewsItem.class);
                var decision = policy.evaluate(news);
                if (!decision.accepted()) reasons.add(decision.reason());
                if (news != null) actualIds.add(news.getId());
            }
            Set<String> expectedIds = new HashSet<>();
            if (ids.isArray()) for (JsonNode id : ids) expectedIds.add(id.asText());
            if (!actualIds.equals(expectedIds)) reasons.add("MISSING_ARTICLE");
            if (event.path("timeline").isArray()) for (JsonNode node : event.path("timeline")) {
                if (!expectedIds.contains(node.path("articleId").asText())) reasons.add("INVALID_TIMELINE");
            }
            if (!Collections.disjoint(acceptedArticleIds, expectedIds) || acceptedEventIds.contains(event.path("id").asText()))
                reasons.add("DUPLICATE_RECORD");
            if (reasons.isEmpty()) {
                retained.add(cluster);
                acceptedArticleIds.addAll(expectedIds);
                acceptedEventIds.add(event.path("id").asText());
            } else {
                var row = quarantine.addObject();
                row.set("reasons", mapper.valueToTree(reasons));
                row.set("cluster", cluster); // Keep complete event, timeline and features for review/restoration.
                reasons.forEach(reason -> reasonCounts.merge(reason, 1, Integer::sum));
            }
        }
        String archive = null;
        if (apply && !quarantine.isEmpty()) {
            Path directory = stateFile.toAbsolutePath().getParent().resolve("quarantine")
                    .resolve("scope-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8));
            Files.createDirectories(directory);
            Files.write(directory.resolve("events.backup.json"), original, StandardOpenOption.CREATE_NEW);
            mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("quarantine.json").toFile(), quarantine);
            archive = directory.toString();
            var report = new Report(input.size(), retained.size(), quarantine.size(), reasonCounts, archive);
            mapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("manifest.json").toFile(),
                    Map.of("policyVersion", NewsContentPolicy.VERSION, "createdAt", Instant.now().toString(),
                            "stateFile", stateFile.toAbsolutePath().toString(), "report", report));
            // Do not replace state unless every archive write succeeded, and refuse concurrent changes.
            if (!Arrays.equals(original, Files.readAllBytes(stateFile))) throw new IOException("State changed during maintenance; stop the writer and retry");
            Path staged = Files.createTempFile(stateFile.toAbsolutePath().getParent(), "events-clean-", ".tmp");
            try {
                mapper.writeValue(staged.toFile(), retained);
                Files.move(staged, stateFile.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(staged); }
        }
        return new Report(input.size(), retained.size(), quarantine.size(), reasonCounts, archive);
    }

    public record Report(int originalEvents, int retainedEvents, int quarantinedEvents,
                         Map<String, Integer> reasonCounts, String archiveDirectory) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || (args.length == 2 && !"--apply".equals(args[1])))
            throw new IllegalArgumentException("Usage: EventDataMaintenance <events.json> [--apply]; stop backend before --apply");
        var report = new EventDataMaintenance().clean(Path.of(args[0]), args.length == 2);
        System.out.println(new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
}
