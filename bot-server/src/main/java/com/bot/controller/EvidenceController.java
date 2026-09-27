package com.bot.controller;

import com.bot.model.EvidenceAnswerRequest;
import com.bot.model.EvidenceAnswerResponse;
import com.bot.service.EvidenceAnswerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {
    private final EvidenceAnswerService service;
    private final com.bot.service.ArticleEvidenceService content;

    public EvidenceController(EvidenceAnswerService service, com.bot.service.ArticleEvidenceService content) {
        this.service = service;
        this.content = content;
    }

    @GetMapping("/events/{eventId}/sources")
    public java.util.List<com.bot.model.NewsItem> sources(@PathVariable String eventId) { return content.prepare(eventId); }

    @GetMapping("/status")
    public java.util.Map<String,Object> status() { return content.status(); }

    @PostMapping("/answer")
    public EvidenceAnswerResponse answer(@RequestBody EvidenceAnswerRequest request) {
        return service.answer(request);
    }

    @PostMapping("/events/{eventId}/answer")
    public EvidenceAnswerResponse answerEvent(@PathVariable String eventId, @RequestBody java.util.Map<String, String> body) {
        return service.answerEvent(eventId, body == null ? "" : body.getOrDefault("question", ""));
    }
}
