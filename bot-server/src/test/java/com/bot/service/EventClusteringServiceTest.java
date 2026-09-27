package com.bot.service;

import com.bot.model.EventClusterRequest;
import com.bot.model.EventClusterResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventClusteringServiceTest {
    @Test
    void articleLibraryContainsReportsFromDifferentEventsWithoutDuplicates() throws Exception {
        var file = Files.createTempDirectory("article-library-").resolve("events.json");
        var service = new EventClusteringService(file);
        var a = com.bot.model.NewsItem.builder().id("one").title("First").summary("First report").entities(List.of()).keywords(List.of()).build();
        var b = com.bot.model.NewsItem.builder().id("two").title("Second").summary("Second report").entities(List.of()).keywords(List.of()).build();
        service.clusterNews(a);
        service.clusterNews(b);
        service.clusterNews(a);
        assertEquals(2, service.allArticles().size());
        assertEquals(2, new EventClusteringService(file).allArticles().size());
    }
    @Test
    void groupsReportsWithSharedSignals() {
        EventClusteringService service = newService();
        Instant published = Instant.parse("2026-09-25T08:00:00Z");
        EventClusterResponse first = service.cluster(request("a-1", "某公司发布新芯片", published,
                List.of("某公司", "新芯片"), List.of("芯片", "发布"), new double[]{1, 0}));
        EventClusterResponse second = service.cluster(request("a-2", "某公司公布芯片产品", published.plusSeconds(3600),
                List.of("某公司", "新芯片"), List.of("芯片", "产品"), new double[]{0.99, 0.01}));

        assertTrue(first.created());
        assertFalse(second.created());
        assertEquals(1, service.list().size());
        assertEquals(2, second.event().articleIds().size());
    }

    @Test
    void createsSeparateEventWhenSignalsDoNotMatch() {
        EventClusteringService service = newService();
        Instant published = Instant.parse("2026-09-25T08:00:00Z");
        service.cluster(request("a-1", "AI 芯片发布", published, List.of("芯片"), List.of("AI"), new double[]{1, 0}));
        EventClusterResponse second = service.cluster(request("a-2", "量子计算突破", published, List.of("量子计算"), List.of("量子"), new double[]{0, 1}));

        assertTrue(second.created());
        assertEquals(2, service.list().size());
    }

    @Test
    void restoresFeaturesSoRestartedServiceCanMatch() throws Exception {
        var file = Files.createTempDirectory("event-restart-").resolve("events.json");
        Instant published = Instant.parse("2026-09-25T08:00:00Z");
        EventClusteringService first = new EventClusteringService(file);
        first.cluster(request("a-1", "某公司发布新芯片", published, List.of("某公司"), List.of("芯片"), new double[]{1, 0}));
        EventClusteringService restarted = new EventClusteringService(file);
        EventClusterResponse second = restarted.cluster(request("a-2", "某公司公布芯片产品", published.plusSeconds(3600), List.of("某公司"), List.of("芯片"), new double[]{0.99, 0.01}));
        assertFalse(second.created());
        assertEquals(1, restarted.list().size());
    }

    @Test
    void framesTypedEntitiesAsPrimaryAndRelatedSubjects() {
        EventClusteringService service = newService();
        var article = com.bot.model.NewsItem.builder()
                .id("subject-1")
                .title("某公司发布新芯片")
                .entities(List.of("某公司", "新芯片"))
                .entityMentions(List.of(
                        new com.bot.model.EntityMention("某公司", "ORG"),
                        new com.bot.model.EntityMention("新芯片", "PRODUCT")))
                .keywords(List.of("芯片"))
                .build();

        var event = service.clusterNews(article).event();

        assertEquals("某公司", event.subjects().get(0).name());
        assertEquals("COMPANY", event.subjects().get(0).type());
        assertEquals("PRIMARY", event.subjects().get(0).role());
        assertEquals("新芯片", event.subjects().get(1).name());
        assertEquals("RELATED", event.subjects().get(1).role());
    }

    private static EventClusterRequest request(String id, String title, Instant published,
                                                List<String> entities, List<String> keywords, double[] embedding) {
        return new EventClusterRequest(id, title, title, published, entities, keywords, embedding);
    }

    private static EventClusteringService newService() {
        try {
            return new EventClusteringService(Files.createTempDirectory("event-test-").resolve("events.json"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
