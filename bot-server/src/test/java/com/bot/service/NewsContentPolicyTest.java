package com.bot.service;

import com.bot.model.NewsItem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NewsContentPolicyTest {
    private final NewsContentPolicy policy = new NewsContentPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"中国押注光计算芯片，它们能用来跑AI吗？", "新芯片性能测试结果发布",
            "OpenAI 发布订阅服务", "US jury says Apple owes $5.7B in haptic technology patent case",
            "Banks team up against Apple Pay fees", "Platform-Independent SIMD in Go",
            "What About Rails?", "Make Claude your assistant in excalidraw", "研究团队实现量子计算纠错突破",
            "Jury finds Facebook liable in Cambridge Analytica case", "机器人广告投放算法研究"})
    void acceptsTechnologyReports(String title) {
        assertTrue(policy.evaluate(article(title)).accepted(), title);
    }

    @ParameterizedTest
    @ValueSource(strings = {"《环球科学》2027年征订开启！", "AI 训练营限时优惠，扫码购买",
            "【广告】新款芯片促销", "Sponsored: cloud hosting special offer"})
    void rejectsPromotionsBeforeTechnologyMatch(String title) {
        assertEquals("PROMOTION", policy.evaluate(article(title)).reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"测试新闻", "测试数据：AI 发布", "E2E fixture chip report", "演示事件：机器人"})
    void rejectsExplicitFixtures(String title) {
        assertEquals("TEST_DATA", policy.evaluate(article(title)).reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"The Mafia may be keeping fentanyl out of Italy", "《环球科学》：章鱼大战螃蟹",
            "男子全款买房未入住", "台风造成多人失踪", "China daily holiday guide", "Air travel gets cheaper",
            "Silicon Valley sex assault list circulated", "I'm the Mom in That Viral Giants Clip"})
    void sourceCategoryAndSubstringsAreNotTechnologyEvidence(String title) {
        assertEquals("OUT_OF_SCOPE", policy.evaluate(article(title)).reason());
    }

    @Test void rejectsMissingProvenanceAndSyntheticDomains() {
        var item = article("AI 芯片发布");
        item.setUrl(null);
        assertEquals("MISSING_SOURCE", policy.evaluate(item).reason());
        item.setUrl("https://news.example.com/1");
        assertEquals("TEST_DATA", policy.evaluate(item).reason());
        item.setUrl("https://news.example.org/1");
        assertEquals("TEST_DATA", policy.evaluate(item).reason());
        item.setUrl("https://news.real.org/1");
        item.setSource(null);
        assertEquals("MISSING_SOURCE", policy.evaluate(item).reason());
    }

    @Test void acceptsSummaryEvidenceButNotSourceOrUrlKeywords() {
        var item = article("新产品正式发布");
        item.setSummary("开发者可通过开源接口使用大语言模型");
        assertTrue(policy.evaluate(item).accepted());
        item.setSummary("最新资讯");
        item.setSource("AI科技媒体");
        item.setUrl("https://news.real.org/ai/chip");
        assertFalse(policy.evaluate(item).accepted());
    }

    @Test void rejectsGeneralFeedAndWarReportsEvenWhenTheyMentionTechnology() {
        var item = article("AI 产业合作会谈");
        item.setCategory("general");
        assertEquals("SOURCE_OUT_OF_SCOPE", policy.evaluate(item).reason());
        item.setCategory("tech");
        item.setTitle("联军拦截导弹和无人机");
        assertEquals("OUT_OF_SCOPE", policy.evaluate(item).reason());
    }

    static NewsItem article(String title) {
        return NewsItem.builder().id("real-1").title(title).source("科技报道")
                .url("https://news.real.org/report/1").category("tech").build();
    }
}
