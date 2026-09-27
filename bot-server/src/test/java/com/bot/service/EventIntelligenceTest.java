package com.bot.service;

import com.bot.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventIntelligenceTest {
    @TempDir Path directory;
    private EventClusteringService service() { return new EventClusteringService(directory.resolve("events.json"), true); }
    private NewsItem report(String id, String title, String time) {
        return NewsItem.builder().id(id).title(title).source("来源-"+id).url("https://news.real.org/"+id)
                .category("tech").publishTime(time).build();
    }
    private static final String DAY = "2026-09-20T08:00:00Z";

    @Test void identifiesCompaniesPeopleProductsWithoutVectorBackend() {
        var event = service().clusterNews(report("one", "OpenAI CEO Sam Altman announces GPT-5 model", DAY)).event();
        assertTrue(event.subjects().stream().anyMatch(s -> s.name().equals("OpenAI") && s.type().equals("COMPANY")));
        assertTrue(event.subjects().stream().anyMatch(s -> s.name().equals("Sam Altman") && s.type().equals("PERSON")));
        assertTrue(event.subjects().stream().anyMatch(s -> s.name().equals("GPT-5") && s.type().equals("PRODUCT")));
        assertEquals(1, event.subjects().stream().filter(s -> s.role().equals("PRIMARY")).count());
    }

    @Test void mergesMultisourceAndAppendsFollowupAcrossRestartWithAliases() {
        var service = service();
        var initial = service.clusterNews(report("a", "英伟达发布 Blackwell B200 芯片", DAY));
        var second = service.clusterNews(report("b", "NVIDIA unveils Blackwell B200 GPU", "Sun, 20 Sep 2026 12:00:00 GMT"));
        assertEquals(initial.event().id(), second.event().id());
        var restarted = service();
        var followup = restarted.clusterNews(report("c", "英伟达 Blackwell B200 芯片开始交付", "2026-09-22T08:00:00Z"));
        assertEquals(initial.event().id(), followup.event().id());
        assertEquals(3, followup.event().articleIds().size());
        assertEquals(3, restarted.articlesFor(initial.event().id()).stream().map(NewsItem::getSource).distinct().count());
        assertEquals(3, followup.event().timeline().size());
        assertEquals(Instant.parse(DAY), followup.event().firstSeenAt());
        assertEquals(Instant.parse("2026-09-22T08:00:00Z"), followup.event().lastUpdatedAt());
        assertEquals("ONGOING", followup.event().status());
        restarted.clusterNews(report("c", "英伟达 Blackwell B200 芯片开始交付", "2026-09-22T08:00:00Z"));
        assertEquals(3, restarted.find(initial.event().id()).timeline().size());
    }

    @Test void sameCompanyDifferentProductOrActionMustNotMergeEvenWithIdenticalVectors() {
        var service = service();
        for (var entry : List.of(
                report("a", "OpenAI releases GPT-5 model", DAY),
                report("b", "OpenAI releases GPT-6 model", DAY),
                report("c", "OpenAI faces copyright lawsuit over GPT-5 model", DAY),
                report("d", "OpenAI GPT-5 suffers service outage", DAY))) {
            entry.setEmbedding(List.of(1f, 0f));
            service.clusterNews(entry);
        }
        assertEquals(4, service.list().size());
    }

    @Test void sharedProductAndActionAloneDoNotMergeUnrelatedIncidents() {
        var service = service();
        service.clusterNews(report("a", "Apple faces haptic technology patent lawsuit", DAY));
        service.clusterNews(report("b", "Apple Pay faces antitrust lawsuit over bank fees", DAY));
        assertEquals(2, service.list().size());
    }

    @Test void repeatedFeedDeliveryWithNewIdDoesNotCreateOrphanArticle() {
        var service = service();
        var article = report("a", "OpenAI releases GPT-5 model", DAY);
        var first = service.clusterNews(article);
        article.setId("new-feed-id");
        var second = service.clusterNews(article);
        assertEquals(first.event().id(), second.event().id());
        assertEquals(1, service.allArticles().size());
    }

    @Test void unrelatedCompaniesWithSimilarTitlesDoNotMergeOnGenericTechnology() {
        var service = service();
        service.clusterNews(report("a", "OpenAI announces new AI model subscription", DAY));
        service.clusterNews(report("b", "Google announces new AI model subscription", DAY));
        assertEquals(2, service.list().size());
    }

    @Test void sameLegalCaseReportsAndAppealAppendWithoutMergingAnotherCase() {
        var service = service();
        var first = service.clusterNews(report("a", "Apple faces haptic technology patent lawsuit", DAY));
        var second = service.clusterNews(report("b", "Jury orders Apple to pay in haptic technology patent lawsuit", "2026-09-21T08:00:00Z"));
        var followup = service.clusterNews(report("c", "Apple appeals haptic technology patent lawsuit verdict", "2026-09-22T08:00:00Z"));
        assertEquals(first.event().id(), second.event().id());
        assertEquals(first.event().id(), followup.event().id());
        service.clusterNews(report("d", "Apple Pay faces antitrust lawsuit over bank fees", DAY));
        assertEquals(2, service.list().size());
    }

    @Test void reportedSubjectsMustBePrioritizedOverIncidentalCompanyInSummary() {
        var item = report("a", "Sam Altman discusses AI safety", DAY);
        item.setSummary("The article also mentions Microsoft.");
        assertEquals("Sam Altman", service().clusterNews(item).event().subjects().get(0).name());
    }

    @Test void timeWindowIsAHardGateAndUnknownTimeCannotCreateFalseFollowup() {
        var service = service();
        service.clusterNews(report("a", "OpenAI releases GPT-5 model", DAY));
        service.clusterNews(report("b", "OpenAI releases GPT-5 model", "2026-11-20T08:00:00Z"));
        service.clusterNews(report("c", "OpenAI releases GPT-5 model", "not-a-date"));
        assertEquals(3, service.list().size());
    }

    @Test void outOfOrderReportsHaveExactlyOneChronologicalFirstReport() {
        var service = service();
        var later = service.clusterNews(report("a", "NVIDIA unveils Blackwell B200 GPU", "2026-09-21T08:00:00Z"));
        var earlier = service.clusterNews(report("b", "英伟达发布 Blackwell B200 芯片", DAY));
        assertEquals(later.event().id(), earlier.event().id());
        assertEquals("b", earlier.event().timeline().get(0).articleId());
        assertEquals("FIRST_REPORT", earlier.event().timeline().get(0).stage());
        assertEquals("UPDATE", earlier.event().timeline().get(1).stage());
    }

    @Test void restartBackfillsStoredSubjectsPreservingEventAndArticleIds() throws Exception {
        var path = directory.resolve("events.json");
        var service = service();
        String id = service.clusterNews(report("a", "Make Claude your assistant in Excalidraw", DAY)).event().id();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var stored = mapper.readTree(path.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode)stored.get(0).get("event")).putArray("subjects");
        ((com.fasterxml.jackson.databind.node.ObjectNode)stored.get(0).get("articles").get(0)).putArray("entityMentions");
        mapper.writeValue(path.toFile(), stored);
        var restored = service();
        assertEquals(List.of("a"), restored.find(id).articleIds());
        assertTrue(restored.find(id).subjects().stream().anyMatch(s -> s.name().equals("Claude") && s.type().equals("PRODUCT")));
    }

    @Test void organizationAndCompanyRemainDistinctAndUnmentionedNamesAreNotInvented() {
        var service = service();
        var article = report("a", "联合国公布人工智能治理报告", DAY);
        article.setEntityMentions(List.of(new EntityMention("联合国", "ORG"), new EntityMention("虚构公司", "COMPANY")));
        var subjects = service.clusterNews(article).event().subjects();
        assertTrue(subjects.stream().anyMatch(s -> s.name().equals("联合国") && s.type().equals("ORGANIZATION")));
        assertFalse(subjects.stream().anyMatch(s -> s.name().equals("虚构公司")));
    }
}
