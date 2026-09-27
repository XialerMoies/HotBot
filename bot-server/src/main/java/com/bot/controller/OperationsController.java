package com.bot.controller;

import com.bot.model.WorkspaceModels;
import com.bot.service.DataSourceStatusService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    private final DataSourceStatusService service;
    public OperationsController(DataSourceStatusService service) { this.service = service; }
    @GetMapping("/sources")
    public List<WorkspaceModels.DataSourceStatus> sources() { return service.list(); }
}
