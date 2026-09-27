package com.bot.service;

import com.bot.model.NewsItem;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deterministic, model-independent admission policy. Uncertain content is held for review. */
public final class NewsContentPolicy {
    public static final String VERSION = "tech-scope-v1";
    private static final Pattern FIXTURE = Pattern.compile(
            "测试(?:新闻|数据|事件)|演示(?:新闻|数据|事件)|示例(?:新闻|数据|事件)|验收(?:新闻|数据|事件)|" +
            "(?i)\\b(?:e2e|fixture|mock data|test article|test event|demo event)\\b");
    private static final Pattern PROMOTION = Pattern.compile(
            "征订|订阅.{0,8}(?:杂志|期刊)|(?:杂志|期刊).{0,8}订阅|限时(?:优惠|抢购|特价)|" +
            "扫码(?:购买|下单|抢购|领取)|领取优惠券|广告招租|招商加盟|【广告】|\\[广告\\]|" +
            "(?i)\\b(?:sponsored|advertorial|buy now|special offer|subscribe now)\\b");
    private static final Pattern TECHNOLOGY = Pattern.compile(
            "人工智能|大(?:语言)?模型|机器学习|深度学习|生成式|智能体|芯片|半导体|光刻|算力|" +
            "量子(?:计算|通信|纠错)|光计算|计算机|数据库|云计算|数据中心|云服务|服务器|" +
            "开源|编程|编译器|操作系统|软件|算法|网络安全|数据泄露|隐私保护|机器人|自动驾驶|" +
            "无人机|脑机接口|神经接口|核聚变|航天|卫星|火箭|固态电池|光伏|储能|" +
            "基因编辑|生物技术|生物芯片|量子芯片|超导|超算|纳米材料|光子|原子钟|" +
            "(?i)(?<![a-z0-9])(?:ai|llms?|gpt(?:-?\\d+)?|openai|anthropic|claude|deepseek|" +
            "cuda|gpu|cpu|nvidia|semiconductors?|chips?|machine learning|neural networks?|" +
            "quantum computing|robotics?|autonomous driving|data cent(?:er|re)s?|cloud computing|" +
            "software|hardware|programming|code review|compilers?|operating systems?|" +
            "cybersecurity|antivirus|cve-\\d+|github|copilot|postgres(?:ql)?|databases?|" +
            "linux|nixos|rust|python|javascript|typescript|kubernetes|simd|pentium|" +
            "mac mini|apple pay|haptic technology|ruby on rails|rails|vision models|" +
            "facebook|excalidraw|homelab)(?![a-z0-9])");

    public Decision evaluate(NewsItem item) {
        if (item == null || blank(item.getId()) || blank(item.getTitle())) return reject("INVALID_ARTICLE");
        String title = clean(item.getTitle());
        if (FIXTURE.matcher(title).find()) return reject("TEST_DATA");
        if (PROMOTION.matcher(title).find()) return reject("PROMOTION");
        if (!blank(item.getCategory()) && !"tech".equalsIgnoreCase(item.getCategory())) return reject("SOURCE_OUT_OF_SCOPE");
        if (title.matches(".*(?:联军|武装|空袭|击落|拦截导弹|国事访问|足球|男篮|女篮|女排|亚运会).*")
                && !title.matches(".*(?:研发|技术突破|芯片|人工智能|算法|机器人).*")) return reject("OUT_OF_SCOPE");
        if (blank(item.getSource()) || blank(item.getUrl())) return reject("MISSING_SOURCE");
        try {
            URI uri = URI.create(item.getUrl());
            String host = uri.getHost();
            if (host == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null) return reject("MISSING_SOURCE");
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("localhost") || host.startsWith("127.") || host.equals("[::1]")
                    || host.endsWith(".test") || host.endsWith(".invalid") || host.endsWith(".example")
                    || host.matches("(?:.*\\.)?example\\.(?:com|org|net)")) return reject("TEST_DATA");
        } catch (IllegalArgumentException exception) { return reject("MISSING_SOURCE"); }
        // Only editorial text is evidence: source labels, URLs and tags cannot grant admission.
        String preview = clean(item.getSummary()) + " " + clean(item.getDetailExcerpt());
        if (!TECHNOLOGY.matcher(title).find() && PROMOTION.matcher(preview).find()) return reject("PROMOTION");
        if (TECHNOLOGY.matcher(title + " " + preview).find()) return new Decision(true, "TECHNOLOGY");
        return reject("OUT_OF_SCOPE");
    }

    public void requireAccepted(NewsItem item) {
        Decision decision = evaluate(item);
        if (!decision.accepted()) throw new IllegalArgumentException("Article rejected by " + VERSION + ": " + decision.reason());
    }

    private static Decision reject(String reason) { return new Decision(false, reason); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String clean(String value) { return value == null ? "" : value.replaceAll("<[^>]+>", " "); }
    public record Decision(boolean accepted, String reason) {}
}
