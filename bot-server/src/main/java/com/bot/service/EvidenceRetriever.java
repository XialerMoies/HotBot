package com.bot.service;

import com.bot.model.EvidenceAnswerRequest.EvidenceItem;
import com.bot.model.NewsItem;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded lexical retrieval with exact UTF-16 offsets into the saved source field. */
public final class EvidenceRetriever {
    private record Candidate(EvidenceItem item, double score) {}
    public List<EvidenceItem> retrieve(String question, List<NewsItem> articles) {
        List<Candidate> candidates = new ArrayList<>();
        boolean overview = question.matches(".*(?:发生了什么|为什么重要|后续.*进展|最新.*进展|总结|概述|各来源|有什么不同|what happened|summar).*" );
        Set<String> query = terms(question);
        for (NewsItem article : articles) {
            String body=article.getFullBody(), kind="BODY";
            if (body==null || body.isBlank()) { body=article.getDetailExcerpt(); kind="EXCERPT"; }
            if (body==null || body.isBlank()) { body=article.getSummary(); kind="SUMMARY"; }
            if (body==null || body.isBlank() || body.startsWith("Hacker News 热度") || body.equals(article.getTitle())) continue;
            String hash=UUID.nameUUIDFromBytes(body.getBytes(StandardCharsets.UTF_8)).toString();
            for (int offset=0;offset<body.length() && offset<60000;offset+=650) {
                int end=Math.min(offset+800,body.length());
                String quote=body.substring(offset,end);
                Set<String> terms=terms(quote);
                double score=query.stream().filter(terms::contains).count();
                if (score==0 && !overview) continue;
                String id="ev-"+UUID.nameUUIDFromBytes((article.getId()+"|"+kind+"|"+hash+"|"+offset).getBytes(StandardCharsets.UTF_8));
                candidates.add(new Candidate(new EvidenceItem(id,article.getId(),quote,article.getTitle(),article.getUrl(),
                        article.getPublishTime(),kind,offset,end,article.getSource(),hash),score));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<EvidenceItem> result=new ArrayList<>();
        Set<String> seenArticles=new HashSet<>();
        // Give each matching source article one slot before filling with additional passages.
        for (Candidate c:candidates) if (seenArticles.add(c.item().articleId()) && result.size()<8) result.add(c.item());
        for (Candidate c:candidates) if (result.size()<8 && !result.contains(c.item())) result.add(c.item());
        return result;
    }
    private static Set<String> terms(String text) {
        Set<String> result=new HashSet<>(EventEntityResolver.tokens(text));
        var matcher=java.util.regex.Pattern.compile("[\\p{IsHan}]{2,}").matcher(text);
        while(matcher.find()) for(int i=0;i<matcher.group().length()-1;i++) result.add(matcher.group().substring(i,i+2));
        result.removeAll(Set.of("什么","何时","如何","为什么","是否","时候","哪些"));
        return result;
    }
}
