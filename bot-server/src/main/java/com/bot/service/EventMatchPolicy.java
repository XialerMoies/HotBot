package com.bot.service;

import com.bot.model.EventClusterRequest;
import com.bot.model.EntityMention;
import java.time.Duration;
import java.util.*;

/** Conservative pairwise event matching: actors are a prerequisite, not proof of a shared event. */
final class EventMatchPolicy {
    private static final EventEntityResolver ENTITIES = new EventEntityResolver();
    record Result(double score, List<String> signals) {}

    static Result compare(EventClusterRequest a, EventClusterRequest b) {
        if (a.publishedAt() == null || b.publishedAt() == null) return none("unknown-time");
        long seconds = Math.abs(Duration.between(a.publishedAt(), b.publishedAt()).getSeconds());
        if (seconds >= Duration.ofDays(14).getSeconds()) return none("outside-time-window");
        var left = ENTITIES.resolve(a.title(), a.entityMentions());
        var right = ENTITIES.resolve(b.title(), b.entityMentions());
        Set<String> lp = names(left, "PRODUCT"), rp = names(right, "PRODUCT");
        Set<String> lc = names(left, "COMPANY"), rc = names(right, "COMPANY");
        if (!lc.isEmpty() && !rc.isEmpty() && Collections.disjoint(lc, rc)) return none("different-company");
        Set<String> li = EventEntityResolver.identifiers(a.title()), ri = EventEntityResolver.identifiers(b.title());
        if (!lp.isEmpty() && !rp.isEmpty() && Collections.disjoint(lp, rp)) return none("different-product");
        if (!li.isEmpty() && !ri.isEmpty() && Collections.disjoint(li, ri)) return none("different-incident-id");
        Set<String> la = actions(a.title()), ra = actions(b.title());
        if (!la.isEmpty() && !ra.isEmpty() && Collections.disjoint(la, ra)) return none("different-action");
        Set<String> le = canonical(a.entities()), re = canonical(b.entities());
        Set<String> concreteLeft = new HashSet<>(), concreteRight = new HashSet<>();
        left.stream().filter(m -> !m.type().equals("TECHNOLOGY")).forEach(m -> concreteLeft.add(m.name().toLowerCase(Locale.ROOT)));
        right.stream().filter(m -> !m.type().equals("TECHNOLOGY")).forEach(m -> concreteRight.add(m.name().toLowerCase(Locale.ROOT)));
        boolean actor = !Collections.disjoint(concreteLeft, concreteRight);
        boolean product = !Collections.disjoint(lp, rp);
        double lexical = dice(EventEntityResolver.tokens(ENTITIES.withoutSubjects(a.title(), left)),
                EventEntityResolver.tokens(ENTITIES.withoutSubjects(b.title(), right)));
        double semantic = cosine(a.embedding(), b.embedding());
        boolean action = !Collections.disjoint(la, ra);
        String at = a.title().toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]", "");
        String bt = b.title().toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]", "");
        if (at.equals(bt) && at.length() >= 10) return new Result(.99, List.of("exact-title", "time-window"));
        if (!Collections.disjoint(li, ri)) return new Result(.97, List.of("incident-id", "time-window"));
        // A versioned launch followed by availability/shipping is a strong non-vector signal.
        boolean specificProduct = lp.stream().filter(rp::contains).anyMatch(s -> s.matches(".*\\d.*"));
        if (actor && specificProduct && action && la.contains("launch"))
            return new Result(.92, List.of("versioned-product", "launch-progress", "time-window"));
        if (actor && action && lexical >= .46)
            return new Result(.80 + .12 * lexical, List.of("entity", "action", "title-overlap", "time-window"));
        if (actor && semantic >= .84 && lexical >= .22 && (action || product))
            return new Result(.78 + .15 * semantic, List.of("semantic", "entity", "event-anchor", "time-window"));
        if (actor && lexical >= .76)
            return new Result(.83, List.of("entity", "strong-title-overlap", "time-window"));
        return new Result(actor && (lexical >= .3 || semantic >= .75) ? .6 : 0,
                List.of("insufficient-event-evidence"));
    }
    private static Result none(String reason) { return new Result(0, List.of(reason)); }
    private static Set<String> names(List<EntityMention> mentions, String type) {
        Set<String> result = new HashSet<>();
        mentions.stream().filter(m -> m.type().equals(type)).forEach(m -> result.add(m.name().toLowerCase(Locale.ROOT)));
        return result;
    }
    private static Set<String> canonical(List<String> names) {
        Set<String> result = new HashSet<>();
        names.stream().map(EventEntityResolver::canonical).map(s -> s.toLowerCase(Locale.ROOT)).forEach(result::add);
        return result;
    }
    private static Set<String> actions(String title) {
        Set<String> actions = new HashSet<>();
        String t = title.toLowerCase(Locale.ROOT);
        if (t.matches(".*(?:诉讼|起诉|判决|裁决|赔偿|上诉|和解|反垄断|\\blawsuit\\b|\\bsues?\\b|\\bjury\\b|\\bpatent\\b|\\bantitrust\\b|\\bappeal\\b|\\bsettlement\\b).*")) actions.add("legal");
        if (t.matches(".*(?:宕机|中断|故障|恢复服务|\\boutage\\b|\\bdisruption\\b|\\brestored\\b).*")) actions.add("outage");
        if (t.matches(".*(?:收购|并购|\\bacquir\\w*|\\bmerger\\b).*")) actions.add("acquisition");
        if (t.matches(".*(?:融资|募资|\\bfunding\\b|\\braises\\b).*")) actions.add("funding");
        if (t.matches(".*(?:发布|推出|公布|宣布|上市|交付|发售|开放使用|\\breleas\\w*|\\blaunch\\w*|\\bunveil\\w*|\\bannounc\\w*|\\bintroduc\\w*|\\bship\\w*|\\bavailability\\b|\\bavailable\\b).*")) actions.add("launch");
        // 'announces lawsuit' is legal, not a product launch.
        if (actions.size() > 1) actions.remove("launch");
        return actions;
    }
    private static double dice(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        return 2.0 * left.stream().filter(right::contains).count() / (left.size() + right.size());
    }
    private static double cosine(double[] a, double[] b) {
        if (a.length == 0 || a.length != b.length) return 0;
        double dot=0, na=0, nb=0;
        for (int i=0;i<a.length;i++) {
            if (!Double.isFinite(a[i]) || !Double.isFinite(b[i])) return 0;
            dot+=a[i]*b[i]; na+=a[i]*a[i]; nb+=b[i]*b[i];
        }
        return na==0 || nb==0 ? 0 : dot/Math.sqrt(na*nb);
    }
}
