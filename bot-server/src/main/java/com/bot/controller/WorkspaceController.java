package com.bot.controller;

import com.bot.model.WorkspaceModels;
import com.bot.service.WorkspaceService;
import com.bot.service.DailyDigestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceService service;
    private final DailyDigestService digestService;
    public WorkspaceController(WorkspaceService service, DailyDigestService digestService) { this.service = service; this.digestService = digestService; }

    @PostMapping("/register")
    public WorkspaceModels.AuthResponse register(@RequestBody WorkspaceModels.RegisterRequest request) { return service.register(request.username(), request.password()); }
    @PostMapping("/login")
    public WorkspaceModels.AuthResponse login(@RequestBody WorkspaceModels.LoginRequest request) { return service.login(request.username(), request.password()); }
    @GetMapping
    public WorkspaceModels.Workspace workspace(@RequestHeader("X-Workspace-Token") String token) { return service.workspace(token); }
    @PostMapping("/follows")
    public WorkspaceModels.Workspace addFollow(@RequestHeader("X-Workspace-Token") String token, @RequestBody WorkspaceModels.AddFollowRequest request) { return service.addFollow(token, request); }
    @DeleteMapping("/follows/{id}")
    public WorkspaceModels.Workspace removeFollow(@RequestHeader("X-Workspace-Token") String token, @PathVariable String id) { return service.removeFollow(token, id); }
    @PostMapping("/subscriptions")
    public WorkspaceModels.Workspace addSubscription(@RequestHeader("X-Workspace-Token") String token, @RequestBody WorkspaceModels.AddSubscriptionRequest request) { return service.addSubscription(token, request); }
    @PostMapping("/digests/generate")
    public WorkspaceModels.Workspace generateDigest(@RequestHeader("X-Workspace-Token") String token) { return digestService.generate(token); }
    @PatchMapping("/subscriptions/{id}")
    public WorkspaceModels.Workspace updateSubscription(@RequestHeader("X-Workspace-Token") String token, @PathVariable String id, @RequestBody WorkspaceModels.UpdateSubscriptionRequest request) { return service.updateSubscription(token, id, request.enabled()); }
    @DeleteMapping("/subscriptions/{id}")
    public WorkspaceModels.Workspace removeSubscription(@RequestHeader("X-Workspace-Token") String token, @PathVariable String id) { return service.removeSubscription(token, id); }
    @PostMapping("/notifications/{id}/read")
    public WorkspaceModels.Workspace readNotification(@RequestHeader("X-Workspace-Token") String token, @PathVariable String id) { return service.markNotificationRead(token, id); }
}
