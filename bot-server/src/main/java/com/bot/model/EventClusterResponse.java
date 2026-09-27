package com.bot.model;

import java.util.List;

public record EventClusterResponse(
        TechEvent event,
        boolean created,
        double score,
        List<String> matchedSignals) {

    public EventClusterResponse {
        matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
    }
}
