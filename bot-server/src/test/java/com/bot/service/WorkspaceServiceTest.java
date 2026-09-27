package com.bot.service;

import com.bot.model.WorkspaceModels;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkspaceServiceTest {
    private Path file;
    @BeforeEach void setup() throws Exception { file = Files.createTempFile("hotbot-workspace", ".json"); Files.deleteIfExists(file); System.setProperty("hotbot.workspace.state", file.toString()); }
    @AfterEach void cleanup() throws Exception { System.clearProperty("hotbot.workspace.state"); Files.deleteIfExists(file); }
    @Test void registerLoginAndPersist() {
        WorkspaceService service = new WorkspaceService();
        WorkspaceModels.AuthResponse created = service.register("alice", "secret1");
        assertEquals("alice", created.user().username());
        service.addFollow(created.token(), new WorkspaceModels.AddFollowRequest("company", "OpenAI"));
        assertEquals(1, service.login("alice", "secret1").workspace().follows().size());
        assertThrows(IllegalArgumentException.class, () -> service.login("alice", "wrongpw"));
        WorkspaceService restored = new WorkspaceService();
        assertEquals(1, restored.login("alice", "secret1").workspace().follows().size());
    }

    @Test void subscriptionChangesPersistAndRespectWorkspaceBoundaries() {
        WorkspaceService service = new WorkspaceService();
        var alice = service.register("alice", "secret1");
        var bob = service.register("bob", "secret2");
        var added = service.addSubscription(alice.token(), new WorkspaceModels.AddSubscriptionRequest("AI"));
        String id = added.subscriptions().get(0).id();
        assertThrows(IllegalArgumentException.class, () -> service.updateSubscription(bob.token(), id, false));
        assertThrows(IllegalArgumentException.class, () -> service.updateSubscription(alice.token(), id, null));
        service.updateSubscription(alice.token(), id, false);
        assertFalse(new WorkspaceService().workspace(alice.token()).subscriptions().get(0).enabled());
        assertTrue(service.updateSubscription(alice.token(), id, true).subscriptions().get(0).enabled());
        service.removeSubscription(bob.token(), id);
        assertEquals(1, service.workspace(alice.token()).subscriptions().size());
        service.removeSubscription(alice.token(), id);
        assertTrue(new WorkspaceService().workspace(alice.token()).subscriptions().isEmpty());
    }
}
