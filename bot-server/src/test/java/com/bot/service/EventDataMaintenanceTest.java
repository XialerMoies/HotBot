package com.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EventDataMaintenanceTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void dryRunIsReadOnlyAndApplyArchivesFullClustersIdempotently() throws Exception {
        Path state = directory.resolve("events.json");
        var legacy = new EventClusteringService(state);
        legacy.clusterNews(NewsContentPolicyTest.article("芯片性能测试发布"));
        var bad = NewsContentPolicyTest.article("《环球科学》征订开启");
        bad.setId("promo");
        bad.setUrl("https://news.real.org/promotion");
        legacy.clusterNews(bad);
        byte[] original = Files.readAllBytes(state);
        var maintenance = new EventDataMaintenance();
        var plan = maintenance.clean(state, false);
        assertEquals(1, plan.quarantinedEvents());
        assertArrayEquals(original, Files.readAllBytes(state));
        assertEquals(1, Files.list(directory).count());
        var applied = maintenance.clean(state, true);
        assertEquals(1, applied.retainedEvents());
        assertArrayEquals(original, Files.readAllBytes(Path.of(applied.archiveDirectory()).resolve("events.backup.json")));
        assertEquals(1, mapper.readTree(state.toFile()).size());
        var quarantined = mapper.readTree(Path.of(applied.archiveDirectory()).resolve("quarantine.json").toFile());
        assertEquals("PROMOTION", quarantined.get(0).get("reasons").get(0).asText());
        assertTrue(quarantined.get(0).has("cluster"));
        assertEquals(0, maintenance.clean(state, true).quarantinedEvents());
    }

    @Test void quarantinesMixedClustersWithoutDanglingTimelineOrMissingArticles() throws Exception {
        Path state = directory.resolve("events.json");
        var legacy = new EventClusteringService(state);
        legacy.clusterNews(NewsContentPolicyTest.article("芯片发布"));
        ArrayNode records = (ArrayNode)mapper.readTree(state.toFile());
        var cluster = records.get(0);
        ((ArrayNode)cluster.get("event").get("articleIds")).add("missing");
        mapper.writeValue(state.toFile(), records);
        var result = new EventDataMaintenance().clean(state, true);
        assertEquals(0, result.retainedEvents());
        assertEquals(1, result.reasonCounts().get("MISSING_ARTICLE"));
    }

    @Test void malformedStateFailsClosedWithoutOverwriting() throws Exception {
        Path state = directory.resolve("events.json");
        Files.writeString(state, "{broken");
        assertThrows(Exception.class, () -> new EventDataMaintenance().clean(state, true));
        assertEquals("{broken", Files.readString(state));
    }

    @Test void productionIngestionRejectsBeforeMutatingAndSurvivesRestart() throws Exception {
        Path state = directory.resolve("events.json");
        var service = new EventClusteringService(state, true);
        assertThrows(IllegalArgumentException.class, () -> service.clusterNews(NewsContentPolicyTest.article("测试新闻")));
        service.registerArticles(List.of(NewsContentPolicyTest.article("征订开启")));
        assertEquals(0, service.allArticles().size());
        service.clusterNews(NewsContentPolicyTest.article("芯片测试结果发布"));
        assertEquals(1, new EventClusteringService(state, true).list().size());
        assertEquals(1, service.allArticles().size());
        assertThrows(IllegalArgumentException.class, () -> service.cluster(new com.bot.model.EventClusterRequest(
                "fixture", "AI 芯片", "AI 芯片", java.time.Instant.now(), List.of(), List.of(), new double[0])));
    }
}
