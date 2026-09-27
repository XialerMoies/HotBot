package com.bot.service;
import com.bot.config.AppConfig;
import com.bot.model.NewsItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestTemplate;
import java.nio.file.Path;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ArticleEvidenceServiceTest {
    @TempDir Path temp;
    @Test void statusDoesNotConfuseProcessReadinessWithGenerationAvailability() {
        var rest=mock(RestTemplate.class);var config=mock(AppConfig.BotConfig.class);when(config.getMlServerUrl()).thenReturn("http://ml");
        var service=new ArticleEvidenceService(new EventClusteringService(temp.resolve("status.json"),true),config,rest);
        when(rest.getForObject(anyString(),eq(Map.class))).thenReturn(Map.of("ready",true,"ner_status","ready"));
        assertEquals(true,service.status().get("reachable"));
        assertEquals("UNVERIFIED",service.status().get("generation"));
        when(rest.getForObject(anyString(),eq(Map.class))).thenThrow(new RuntimeException("offline"));
        assertEquals(false,service.status().get("reachable"));
    }
    @Test void persistsBodyAndCachesFailureWithoutChangingEventIdentity() {
        var events=new EventClusteringService(temp.resolve("events.json"),true);
        String id=events.clusterNews(NewsItem.builder().id("a").title("AI model release").source("News").url("https://news.real.org/a").category("tech").build()).event().id();
        var rest=mock(RestTemplate.class);var config=mock(AppConfig.BotConfig.class);when(config.getMlServerUrl()).thenReturn("http://ml");
        when(rest.postForObject(anyString(),any(),eq(Map.class))).thenReturn(Map.of("body","","status","FETCH_FAILED"));
        var service=new ArticleEvidenceService(events,config,rest);
        assertEquals("FETCH_FAILED",service.prepare(id).get(0).getContentStatus());
        service.prepare(id);verify(rest,times(1)).postForObject(anyString(),any(),eq(Map.class));
        events.saveArticleContent("a","","FETCH_FAILED","2020-01-01T00:00:00Z");
        when(rest.postForObject(anyString(),any(),eq(Map.class))).thenReturn(Map.of("body","公司正式发布产品，计划下月交付。","status","BODY"));
        assertEquals("公司正式发布产品，计划下月交付。",service.prepare(id).get(0).getFullBody());
        service.prepare(id);verify(rest,times(2)).postForObject(anyString(),any(),eq(Map.class));
        var restored=new EventClusteringService(temp.resolve("events.json"),true);
        assertEquals("公司正式发布产品，计划下月交付。",restored.articlesFor(id).get(0).getFullBody());
    }
}
