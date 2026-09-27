package com.bot.service;

import com.bot.config.AppConfig;
import com.bot.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class EvidencePipelineTest {
    private NewsItem article(String id) {
        return NewsItem.builder().id(id).title("芯片发布").source("科技来源").url("https://news.real.org/"+id).build();
    }
    @Test void doesNotTreatTitleOrHnHeatAsEvidence() {
        var item=article("a"); item.setSummary("Hacker News 热度：100 分，20 条评论");
        assertTrue(new EvidenceRetriever().retrieve("发生了什么",List.of(item)).isEmpty());
    }
    @Test void chunkOffsetsExactlyLocateBodyAndRetrieveQuestionRelevantText() {
        var item=article("a");
        item.setFullBody("介绍背景。".repeat(150)+"\n\n芯片交付安排在十月，首批面向开发者，随后逐步扩大开放范围。".repeat(15));
        var result=new EvidenceRetriever().retrieve("何时交付",List.of(item));
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).quote().contains("交付"));
        for(var e:result) {
            assertEquals("BODY",e.kind());
            assertEquals(item.getFullBody().substring(e.startOffset(),e.endOffset()),e.quote());
            assertEquals(e.id(),new EvidenceRetriever().retrieve("何时交付",List.of(item)).stream().filter(x->x.startOffset()==e.startOffset()).findFirst().orElseThrow().id());
        }
    }
    @Test void labelsSummaryAndKeepsSourcesSeparate() {
        var a=article("a");a.setSummary("公司宣布新芯片将于十月交付，目前尚未公布定价。");
        var b=article("b");b.setSource("另一来源");b.setSummary("公司公布的交付计划为十一月，发布日期尚不确定。");
        var result=new EvidenceRetriever().retrieve("交付时间有什么不同",List.of(a,b));
        assertEquals(2,result.size());assertTrue(result.stream().allMatch(e->e.kind().equals("SUMMARY")));
    }
    @Test void foreignQuestionDoesNotReturnArbitraryChunks() {
        var a=article("a");a.setFullBody("公司于十月发布新芯片，面向开发者提供推理加速服务。");
        assertTrue(new EvidenceRetriever().retrieve("今晚巴黎天气如何",List.of(a)).isEmpty());
    }
    @Test void importancePresetRetrievesEnglishEvidenceForChineseQuestion() {
        var a=article("a");a.setFullBody("Portable SIMD supports efficient vector operations across CPU platforms.");
        assertFalse(new EvidenceRetriever().retrieve("为什么重要？",List.of(a)).isEmpty());
    }
    @Test void modelCannotInventEvidenceOrChangeSourceUrl() {
        var rest=mock(RestTemplate.class);var config=mock(AppConfig.BotConfig.class);
        when(config.getMlServerUrl()).thenReturn("http://ml");
        when(rest.postForObject(anyString(),any(),eq(Map.class))).thenReturn(Map.of("answer","虚构结论 [fake]","claims",List.of(Map.of("text","虚构结论","evidenceIds",List.of("fake"))),"evidence",List.of(Map.of("id","fake","url","https://evil.org"))));
        var service=new EvidenceAnswerService(rest,config,mock(EventClusteringService.class));
        var evidence=new EvidenceAnswerRequest.EvidenceItem("ev-1","a","仅公布产品计划","发布","https://news.real.org/a","2026-09-27");
        var result=service.answer(new EvidenceAnswerRequest("发生了什么",List.of(evidence)));
        assertTrue(result.fallback());assertFalse(result.answer().contains("虚构结论"));
        assertEquals("https://news.real.org/a",result.evidence().get(0).get("url"));
    }
    @Test void validClaimsUseServerOwnedEvidenceAndConsistentAnswerText() {
        var rest=mock(RestTemplate.class);var config=mock(AppConfig.BotConfig.class);
        when(config.getMlServerUrl()).thenReturn("http://ml");
        when(rest.postForObject(anyString(),any(),eq(Map.class))).thenReturn(Map.of("answer","malicious unused text","claims",List.of(Map.of("text","公司公布计划","evidenceIds",List.of("ev-1"))),"evidence",List.of(Map.of("id","ev-1","quote","伪造片段","url","https://evil.org"))));
        var service=new EvidenceAnswerService(rest,config,mock(EventClusteringService.class));
        var evidence=new EvidenceAnswerRequest.EvidenceItem("ev-1","a","原始片段","发布","https://news.real.org/a","2026-09-27");
        var result=service.answer(new EvidenceAnswerRequest("发生了什么",List.of(evidence)));
        assertFalse(result.fallback());assertEquals("公司公布计划 [ev-1]",result.answer());
        assertEquals("原始片段",result.evidence().get(0).get("quote"));
    }
}
