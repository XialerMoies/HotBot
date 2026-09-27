package com.bot.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataSourceStatusServiceTest {
    @Test void tracksAttemptsAndOutcome() {
        DataSourceStatusService service = new DataSourceStatusService();
        service.started("rss"); service.failed("rss", "timeout"); service.started("rss"); service.succeeded("rss");
        var status = service.list().get(0);
        assertEquals("SUCCESS", status.state());
        assertEquals(2, status.attempts());
    }
}
