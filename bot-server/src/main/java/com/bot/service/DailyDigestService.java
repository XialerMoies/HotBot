package com.bot.service;

import com.bot.model.NewsItem;
import com.bot.model.TechEvent;
import com.bot.model.WorkspaceModels;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class DailyDigestService {
    private final WorkspaceService workspaceService;
    private final EventClusteringService eventService;

    public DailyDigestService(WorkspaceService workspaceService, EventClusteringService eventService) {
        this.workspaceService = workspaceService;
        this.eventService = eventService;
    }

    public synchronized WorkspaceModels.Workspace generate(String token) {
        String userId = workspaceService.userIdForToken(token);
        WorkspaceModels.Workspace workspace = workspaceService.workspace(token);
        List<TechEvent> events = eventService.list();
        List<String> terms = new ArrayList<>();
        workspace.follows().forEach(v -> terms.add(v.value().toLowerCase(Locale.ROOT)));
        workspace.subscriptions().stream().filter(WorkspaceModels.Subscription::enabled).forEach(v -> terms.add(v.query().toLowerCase(Locale.ROOT)));
        List<TechEvent> matches = events.stream().filter(e -> terms.isEmpty() || matches(e, terms)).limit(10).toList();
        String content = matches.isEmpty() ? "今天暂未发现与你关注项匹配的科技事件。" : matches.stream().map(e -> "- " + e.name() + "（" + e.status() + "）").reduce((a,b) -> a + "\n" + b).orElse("");
        WorkspaceModels.DailyDigest digest = new WorkspaceModels.DailyDigest("digest-" + UUID.randomUUID(), LocalDate.now().toString(), "每日科技摘要", content, Instant.now());
        WorkspaceModels.Workspace updated = workspaceService.addDigest(userId, digest);
        for (TechEvent event : matches) workspaceService.addNotification(userId, "事件更新：" + event.name(), "事件状态：" + event.status());
        return workspaceService.workspace(token);
    }

    private boolean matches(TechEvent event, List<String> terms) {
        String text = (event.name() + " " + String.join(" ", event.entities())).toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(text::contains);
    }
}
