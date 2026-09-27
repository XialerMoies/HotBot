package com.bot.controller;

import com.bot.model.EvidenceAnswerRequest;
import com.bot.model.EvidenceAnswerResponse;
import com.bot.service.EvidenceAnswerService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {
    private final EvidenceAnswerService service;

    public EvidenceController(EvidenceAnswerService service) {
        this.service = service;
    }

    @PostMapping("/answer")
    public EvidenceAnswerResponse answer(@RequestBody EvidenceAnswerRequest request) {
        return service.answer(request);
    }

    @PostMapping("/events/{eventId}/answer")
    public EvidenceAnswerResponse answerEvent(@PathVariable String eventId, @RequestBody java.util.Map<String, String> body) {
        return service.answerEvent(eventId, body == null ? "" : body.getOrDefault("question", ""));
    }
}
