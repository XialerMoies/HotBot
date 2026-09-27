package com.bot.service;

import com.bot.controller.EventController;
import com.bot.controller.ApiExceptionHandler;
import com.bot.model.NewsItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP serialization/controller/service/persistence flow, with all state in a disposable directory. */
class EventHttpFlowTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc api() {
        return MockMvcBuilders.standaloneSetup(new EventController(new EventClusteringService(directory.resolve("events.json"), true)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    private String ingest(MockMvc api, String id, String title, String source, String date) throws Exception {
        String body = mapper.writeValueAsString(NewsItem.builder().id(id).title(title).source(source)
                .url("https://news.real.org/"+id).publishTime(date).category("tech").build());
        String response = api.perform(post("/api/events/cluster/news").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("event").path("id").asText();
    }
    @Test void multisourceFollowupRestartDetailAndRejectedInput() throws Exception {
        MockMvc api = api();
        String id = ingest(api,"a","英伟达发布 Blackwell B200 芯片","甲科技","2026-09-20T08:00:00Z");
        assertEquals(id, ingest(api,"b","NVIDIA unveils Blackwell B200 GPU","乙科技","Sun, 20 Sep 2026 12:00:00 GMT"));
        api = api(); // Read persisted features in a new service instance.
        assertEquals(id, ingest(api,"c","英伟达 Blackwell B200 芯片开始交付","丙科技","2026-09-22T08:00:00Z"));
        assertNotEquals(id, ingest(api,"d","NVIDIA B200 GPU faces patent lawsuit","丁科技","2026-09-22T08:00:00Z"));
        assertEquals(id, ingest(api,"c","英伟达 Blackwell B200 芯片开始交付","丙科技","2026-09-22T08:00:00Z"));
        var detail = api.perform(get("/api/events/"+id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.articles.length()").value(3))
                .andExpect(jsonPath("$.event.timeline.length()").value(3))
                .andExpect(jsonPath("$.event.status").value("ONGOING"))
                .andReturn().getResponse().getContentAsString();
        var node = mapper.readTree(detail);
        Set<String> sources = new HashSet<>(); node.path("articles").forEach(a->sources.add(a.path("source").asText()));
        assertEquals(3, sources.size());
        api.perform(get("/api/events")).andExpect(jsonPath("$.length()").value(2));
        api.perform(get("/api/events/articles")).andExpect(jsonPath("$.length()").value(4));
        api.perform(post("/api/events/cluster/news").contentType("application/json")
                .content("{\"id\":\"bad\",\"title\":\"测试新闻\"}"))
                .andExpect(status().isBadRequest());
        api.perform(get("/api/events")).andExpect(jsonPath("$.length()").value(2));
    }
}
