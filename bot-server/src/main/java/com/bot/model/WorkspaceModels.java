package com.bot.model;

import java.time.Instant;
import java.util.List;

public final class WorkspaceModels {
    private WorkspaceModels() {}

    public record RegisterRequest(String username, String password) {}
    public record LoginRequest(String username, String password) {}
    public record AuthResponse(String token, User user, Workspace workspace) {}
    public record User(String id, String username, Instant createdAt) {}
    public record Workspace(String id, String userId, String name, List<FollowItem> follows,
                            List<Subscription> subscriptions, List<Notification> notifications,
                            List<DailyDigest> digests) {
        public Workspace {
            follows = follows == null ? List.of() : List.copyOf(follows);
            subscriptions = subscriptions == null ? List.of() : List.copyOf(subscriptions);
            notifications = notifications == null ? List.of() : List.copyOf(notifications);
            digests = digests == null ? List.of() : List.copyOf(digests);
        }
    }
    public record FollowItem(String id, String type, String value, Instant createdAt) {}
    public record Subscription(String id, String query, boolean enabled, Instant createdAt) {}
    public record Notification(String id, String title, String content, boolean read, Instant createdAt) {}
    public record DailyDigest(String id, String date, String title, String content, Instant createdAt) {}
    public record AddFollowRequest(String type, String value) {}
    public record AddSubscriptionRequest(String query) {}
    public record UpdateSubscriptionRequest(Boolean enabled) {}
    public record DataSourceStatus(String name, String state, int attempts, String lastError, Instant lastRunAt) {}
}
