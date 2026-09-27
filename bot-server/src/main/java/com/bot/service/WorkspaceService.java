package com.bot.service;

import com.bot.model.WorkspaceModels;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class WorkspaceService {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path stateFile = Path.of(System.getProperty("hotbot.workspace.state", "storage/workspaces.json"));
    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, WorkspaceModels.Workspace> workspaces = new LinkedHashMap<>();

    public WorkspaceService() { restore(); }

    public synchronized WorkspaceModels.AuthResponse register(String username, String password) {
        validateCredentials(username, password);
        if (accounts.values().stream().anyMatch(a -> a.user().username().equalsIgnoreCase(username.trim())))
            throw new IllegalArgumentException("username already exists");
        WorkspaceModels.User user = new WorkspaceModels.User("user-" + UUID.randomUUID(), username.trim(), Instant.now());
        WorkspaceModels.Workspace workspace = emptyWorkspace(user.id(), username.trim() + " 的工作区");
        accounts.put(user.id(), new Account(user, hash(password), UUID.randomUUID().toString()));
        workspaces.put(user.id(), workspace);
        persist();
        return response(accounts.get(user.id()), workspace);
    }

    public synchronized WorkspaceModels.AuthResponse login(String username, String password) {
        Account account = accounts.values().stream().filter(a -> a.user().username().equalsIgnoreCase(username == null ? "" : username.trim())).findFirst().orElse(null);
        if (account == null || !account.passwordHash().equals(hash(password))) throw new IllegalArgumentException("invalid credentials");
        return response(account, workspaces.get(account.user().id()));
    }

    public synchronized WorkspaceModels.Workspace workspace(String token) { return requireWorkspace(token); }
    public synchronized WorkspaceModels.Workspace addFollow(String token, WorkspaceModels.AddFollowRequest request) {
        WorkspaceModels.Workspace old = requireWorkspace(token);
        if (request == null || request.value() == null || request.value().isBlank()) throw new IllegalArgumentException("follow value is required");
        List<WorkspaceModels.FollowItem> values = new ArrayList<>(old.follows());
        values.add(new WorkspaceModels.FollowItem("follow-" + UUID.randomUUID(), request.type() == null ? "keyword" : request.type(), request.value().trim(), Instant.now()));
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), values, old.subscriptions(), old.notifications(), old.digests()));
    }
    public synchronized WorkspaceModels.Workspace removeFollow(String token, String id) {
        WorkspaceModels.Workspace old = requireWorkspace(token);
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), old.follows().stream().filter(v -> !v.id().equals(id)).toList(), old.subscriptions(), old.notifications(), old.digests()));
    }
    public synchronized WorkspaceModels.Workspace addSubscription(String token, WorkspaceModels.AddSubscriptionRequest request) {
        WorkspaceModels.Workspace old = requireWorkspace(token);
        if (request == null || request.query() == null || request.query().isBlank()) throw new IllegalArgumentException("query is required");
        List<WorkspaceModels.Subscription> values = new ArrayList<>(old.subscriptions());
        values.add(new WorkspaceModels.Subscription("sub-" + UUID.randomUUID(), request.query().trim(), true, Instant.now()));
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), old.follows(), values, old.notifications(), old.digests()));
    }
    public synchronized WorkspaceModels.Workspace updateSubscription(String token, String id, Boolean enabled) {
        WorkspaceModels.Workspace old = requireWorkspace(token);
        if (enabled == null) throw new IllegalArgumentException("enabled is required");
        if (old.subscriptions().stream().noneMatch(v -> v.id().equals(id))) throw new IllegalArgumentException("subscription not found");
        var values = old.subscriptions().stream().map(v -> v.id().equals(id)
                ? new WorkspaceModels.Subscription(v.id(), v.query(), enabled, v.createdAt()) : v).toList();
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), old.follows(), values, old.notifications(), old.digests()));
    }

    public synchronized WorkspaceModels.Workspace removeSubscription(String token, String id) {
        WorkspaceModels.Workspace old = requireWorkspace(token);
        var values = old.subscriptions().stream().filter(v -> !v.id().equals(id)).toList();
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), old.follows(), values, old.notifications(), old.digests()));
    }

    public synchronized WorkspaceModels.Workspace addNotification(String userId, String title, String content) {
        WorkspaceModels.Workspace old = workspaces.get(userId); if (old == null) return null;
        List<WorkspaceModels.Notification> values = new ArrayList<>(old.notifications());
        values.add(new WorkspaceModels.Notification("notice-" + UUID.randomUUID(), title, content, false, Instant.now()));
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), old.follows(), old.subscriptions(), values, old.digests()));
    }
    public synchronized WorkspaceModels.Workspace addDigest(String userId, WorkspaceModels.DailyDigest digest) {
        WorkspaceModels.Workspace old = workspaces.get(userId); if (old == null) return null;
        List<WorkspaceModels.DailyDigest> values = new ArrayList<>(old.digests());
        values.removeIf(v -> Objects.equals(v.date(), digest.date()));
        values.add(digest);
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), old.follows(), old.subscriptions(), old.notifications(), values));
    }
    public synchronized List<WorkspaceModels.Workspace> allWorkspaces() { return List.copyOf(workspaces.values()); }
    public synchronized WorkspaceModels.Workspace markNotificationRead(String token, String id) {
        WorkspaceModels.Workspace old = requireWorkspace(token);
        List<WorkspaceModels.Notification> values = old.notifications().stream().map(v -> v.id().equals(id) ? new WorkspaceModels.Notification(v.id(), v.title(), v.content(), true, v.createdAt()) : v).toList();
        return save(old, new WorkspaceModels.Workspace(old.id(), old.userId(), old.name(), old.follows(), old.subscriptions(), values, old.digests()));
    }

    public synchronized String userIdForToken(String token) { return requireAccount(token).user().id(); }
    private WorkspaceModels.Workspace save(WorkspaceModels.Workspace old, WorkspaceModels.Workspace next) { workspaces.put(old.userId(), next); persist(); return next; }
    private WorkspaceModels.AuthResponse response(Account a, WorkspaceModels.Workspace w) { return new WorkspaceModels.AuthResponse(a.token(), a.user(), w); }
    private WorkspaceModels.Workspace requireWorkspace(String token) { return workspaces.get(requireAccount(token).user().id()); }
    private Account requireAccount(String token) { return accounts.values().stream().filter(a -> a.token().equals(token)).findFirst().orElseThrow(() -> new IllegalArgumentException("invalid token")); }
    private static WorkspaceModels.Workspace emptyWorkspace(String userId, String name) { return new WorkspaceModels.Workspace("workspace-" + UUID.randomUUID(), userId, name, List.of(), List.of(), List.of(), List.of()); }
    private static void validateCredentials(String username, String password) { if (username == null || username.trim().length() < 3 || password == null || password.length() < 6) throw new IllegalArgumentException("username requires 3 chars and password requires 6 chars"); }
    private static String hash(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)); return HexFormat.of().formatHex(bytes); } catch (Exception e) { throw new IllegalStateException(e); } }
    private void persist() { try { Path parent = stateFile.getParent(); if (parent != null) Files.createDirectories(parent); mapper.writeValue(stateFile.toFile(), new State(new ArrayList<>(accounts.values()), new ArrayList<>(workspaces.values()))); } catch (Exception ignored) {} }
    private void restore() { if (!Files.exists(stateFile)) return; try { State state = mapper.readValue(stateFile.toFile(), State.class); if (state.accounts != null) state.accounts.forEach(a -> accounts.put(a.user().id(), a)); if (state.workspaces != null) state.workspaces.forEach(w -> workspaces.put(w.userId(), w)); } catch (Exception ignored) {} }
    private record Account(WorkspaceModels.User user, String passwordHash, String token) {}
    private record State(List<Account> accounts, List<WorkspaceModels.Workspace> workspaces) {}
}
