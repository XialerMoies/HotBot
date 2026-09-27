package com.bot.service;

import com.bot.model.WorkspaceModels;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
public class DataSourceStatusService {
    private final Map<String, WorkspaceModels.DataSourceStatus> statuses = new LinkedHashMap<>();
    public synchronized void started(String name) { statuses.put(name, new WorkspaceModels.DataSourceStatus(name, "RUNNING", attempts(name) + 1, null, Instant.now())); }
    public synchronized void succeeded(String name) { WorkspaceModels.DataSourceStatus old = statuses.get(name); statuses.put(name, new WorkspaceModels.DataSourceStatus(name, "SUCCESS", old == null ? 1 : old.attempts(), null, Instant.now())); }
    public synchronized void failed(String name, String error) { WorkspaceModels.DataSourceStatus old = statuses.get(name); statuses.put(name, new WorkspaceModels.DataSourceStatus(name, "FAILED", old == null ? 1 : old.attempts(), error, Instant.now())); }
    public synchronized List<WorkspaceModels.DataSourceStatus> list() { return List.copyOf(statuses.values()); }
    private int attempts(String name) { return statuses.containsKey(name) ? statuses.get(name).attempts() : 0; }
}
