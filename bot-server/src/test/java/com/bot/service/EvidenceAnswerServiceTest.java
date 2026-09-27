package com.bot.service;

import com.bot.config.AppConfig;
import com.bot.model.EvidenceAnswerRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EvidenceAnswerServiceTest {
    @Test void blankModelAnswerFallsBackToEvidence() {
        var rest = mock(RestTemplate.class);
        var config = mock(AppConfig.BotConfig.class);
        when(config.getMlServerUrl()).thenReturn("http://ml");
        when(rest.postForObject(anyString(), any(), eq(Map.class))).thenReturn(Map.of("answer", " "));
        var service = new EvidenceAnswerService(rest, config, mock(EventClusteringService.class));
        var item = new EvidenceAnswerRequest.EvidenceItem("ev-1", "a-1", "公司发布了产品", "产品新闻", "https://example.com/news", "2026-09-25");
        var answer = service.answer(new EvidenceAnswerRequest("发生了什么", List.of(item)));
        assertTrue(answer.fallback());
        assertTrue(answer.answer().contains("公司发布了产品"));
        assertEquals("https://example.com/news", answer.evidence().get(0).get("url"));
    }
}
