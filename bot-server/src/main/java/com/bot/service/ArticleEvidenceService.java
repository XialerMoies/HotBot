package com.bot.service;

import com.bot.config.AppConfig;
import com.bot.model.NewsItem;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import java.time.Instant;
import java.util.*;

/** Fetch on demand with a bounded per-request budget and persisted success/failure cache. */
@Service
public class ArticleEvidenceService {
    private final EventClusteringService events;
    private final AppConfig.BotConfig config;
    private final RestTemplate client;
    @org.springframework.beans.factory.annotation.Autowired
    public ArticleEvidenceService(EventClusteringService events, AppConfig.BotConfig config) {
        this.events=events;this.config=config;
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(2000);factory.setReadTimeout(10000);
        this.client=new RestTemplate(factory);
    }
    ArticleEvidenceService(EventClusteringService events, AppConfig.BotConfig config, RestTemplate client) {
        this.events=events;this.config=config;this.client=client;
    }
    public Map<String,Object> status() {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("generation","UNVERIFIED");
        result.put("checkedAt",Instant.now().toString());
        try {
            Map<?,?> ready=client.getForObject(config.getMlServerUrl()+"/api/ready",Map.class);
            result.put("reachable",ready!=null);
            result.put("ner",ready==null ? "unknown" : String.valueOf(ready.get("ner_status")));
        } catch(RuntimeException ignored) { result.put("reachable",false);result.put("ner","unknown"); }
        return result;
    }
    @SuppressWarnings("unchecked")
    public List<NewsItem> prepare(String eventId) {
        if(events.find(eventId)==null) throw new IllegalArgumentException("event not found");
        List<NewsItem> articles=events.articlesFor(eventId);
        int fetched=0;
        for (NewsItem article:articles) {
            if (article.getFullBody()!=null && !article.getFullBody().isBlank()) continue;
            if (fetched>=2) break;
            try {
                if (article.getContentFetchedAt()!=null && Instant.parse(article.getContentFetchedAt()).isAfter(Instant.now().minusSeconds(600))) continue;
            } catch (RuntimeException ignored) { }
            fetched++;
            String body="", status="FETCH_FAILED";
            try {
                Map<String,Object> response=client.postForObject(config.getMlServerUrl()+"/api/articles/content",
                        Map.of("url", article.getUrl()==null ? "" : article.getUrl()),Map.class);
                if(response!=null) {
                    status=String.valueOf(response.getOrDefault("status","FETCH_FAILED"));
                    if("BODY".equals(status) && response.get("body") instanceof String text) body=text.substring(0,Math.min(60000,text.length()));
                }
            } catch (RuntimeException ignored) {status="FETCH_UNAVAILABLE";}
            events.saveArticleContent(article.getId(),body,status,Instant.now().toString());
        }
        return events.articlesFor(eventId);
    }
}
