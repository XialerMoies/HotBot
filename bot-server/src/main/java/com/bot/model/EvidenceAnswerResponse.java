package com.bot.model;

import java.util.List;
import java.util.Map;

public record EvidenceAnswerResponse(String answer, List<Map<String, Object>> claims,
                                     List<Map<String, Object>> evidence, boolean fallback) {}
