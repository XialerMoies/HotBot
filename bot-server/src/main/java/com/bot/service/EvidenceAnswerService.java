package com.bot.service;

import com.bot.config.AppConfig;
import com.bot.model.EvidenceAnswerRequest;
import com.bot.model.EvidenceAnswerResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class EvidenceAnswerService {
    private final RestTemplate restTemplate;
    private final AppConfig.BotConfig config;
    private final EventClusteringService clusteringService;
    private final ArticleEvidenceService content;
    public EvidenceAnswerService(RestTemplate restTemplate, AppConfig.BotConfig config, EventClusteringService clusteringService) {
        this(restTemplate,config,clusteringService,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public EvidenceAnswerService(RestTemplate restTemplate, AppConfig.BotConfig config, EventClusteringService clusteringService, ArticleEvidenceService content) {
        this.restTemplate=restTemplate;this.config=config;this.clusteringService=clusteringService;this.content=content;
    }
    public EvidenceAnswerResponse answerEvent(String eventId, String question) {
        validateQuestion(question);
        if(clusteringService.find(eventId)==null) throw new IllegalArgumentException("event not found");
        var articles=content==null ? clusteringService.articlesFor(eventId) : content.prepare(eventId);
        return answer(new EvidenceAnswerRequest(question,new EvidenceRetriever().retrieve(question,articles)));
    }
    private static void validateQuestion(String question) {
        if(question==null || question.isBlank() || question.length()>2000) throw new IllegalArgumentException("question requires 1-2000 characters");
    }
    public EvidenceAnswerResponse answer(EvidenceAnswerRequest request) {
        if(request==null) throw new IllegalArgumentException("question is required");
        validateQuestion(request.question());
        var selected=request.evidence().stream().filter(e->e.id()!=null && e.quote()!=null && !e.quote().isBlank()).limit(8).toList();
        List<Map<String,Object>> evidence=selected.stream().map(EvidenceAnswerService::evidenceMap).toList();
        if(evidence.isEmpty()) return new EvidenceAnswerResponse("当前没有检索到足以回答该问题的正文或摘要证据。标题与热度数据不能作为事实依据。",List.of(),List.of(),true);
        try {
            Map<?,?> response=restTemplate.postForObject(config.getMlServerUrl()+"/api/evidence/answer",Map.of("question",request.question(),"evidence",evidence),Map.class);
            if(response==null || !(response.get("claims") instanceof List<?> raw) || raw.isEmpty() || raw.size()>12) throw new IllegalArgumentException("invalid claims");
            Set<String> known=new HashSet<>();selected.forEach(e->known.add(e.id()));
            List<Map<String,Object>> claims=new ArrayList<>();
            StringJoiner answer=new StringJoiner("\n");
            for(Object value:raw) {
                if(!(value instanceof Map<?,?> c) || !(c.get("text") instanceof String text) || text.isBlank() || text.length()>4000
                        || !(c.get("evidenceIds") instanceof List<?> ids) || ids.isEmpty()
                        || ids.stream().anyMatch(id->!(id instanceof String) || !known.contains(id))) throw new IllegalArgumentException("invalid citation");
                List<String> references=ids.stream().map(String::valueOf).distinct().toList();
                claims.add(Map.of("text",text,"evidenceIds",references));
                answer.add(text+" "+references.stream().map(id->"["+id+"]").collect(java.util.stream.Collectors.joining("")));
            }
            return new EvidenceAnswerResponse(answer.toString(),claims,evidence,Boolean.TRUE.equals(response.get("fallback")));
        } catch(Exception ignored) {
            List<Map<String,Object>> claims=selected.stream().map(e->Map.<String,Object>of("text",e.quote(),"evidenceIds",List.of(e.id()))).toList();
            String text="生成服务不可用或引用校验未通过，以下仅展示检索片段，不构成完整结论：\n"+selected.stream().map(e->e.quote()+" ["+e.id()+"]").collect(java.util.stream.Collectors.joining("\n"));
            return new EvidenceAnswerResponse(text,claims,evidence,true);
        }
    }
    private static Map<String,Object> evidenceMap(EvidenceAnswerRequest.EvidenceItem item) {
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("id",item.id());value.put("articleId",item.articleId());value.put("quote",item.quote());
        value.put("title",item.title());value.put("url",item.url());value.put("publishedAt",item.publishedAt());
        value.put("kind",item.kind());value.put("startOffset",item.startOffset());value.put("endOffset",item.endOffset());
        value.put("source",item.source());value.put("contentHash",item.contentHash());
        return value;
    }
}
