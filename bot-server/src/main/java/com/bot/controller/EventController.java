package com.bot.controller;

import com.bot.model.EventClusterRequest;
import com.bot.model.EventClusterResponse;
import com.bot.model.TechEvent;
import com.bot.model.NewsItem;
import com.bot.service.EventClusteringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private final EventClusteringService clusteringService;

    public EventController(EventClusteringService clusteringService) {
        this.clusteringService = clusteringService;
    }

    @PostMapping("/cluster")
    public ResponseEntity<EventClusterResponse> cluster(@RequestBody EventClusterRequest request) {
        return ResponseEntity.ok(clusteringService.cluster(request));
    }

    @GetMapping
    public List<TechEvent> list() {
        return clusteringService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@org.springframework.web.bind.annotation.PathVariable String id) {
        TechEvent event = clusteringService.find(id);
        if (event == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(java.util.Map.of("event", event, "articles", clusteringService.articlesFor(id)));
    }

    @GetMapping("/articles")
    public List<NewsItem> articles() {
        return clusteringService.allArticles();
    }

    @PostMapping("/cluster/news")
    public ResponseEntity<EventClusterResponse> clusterNews(@RequestBody NewsItem item) {
        return ResponseEntity.ok(clusteringService.clusterNews(item));
    }
}
