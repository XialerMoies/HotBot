package com.bot.service;

import com.bot.model.EntityMention;
import com.bot.model.NewsItem;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/** Evidence-bound aliases supplement (not replace) model NER when the ML backend is unavailable. */
public final class EventEntityResolver {
    private record Entry(String name, String type, Pattern pattern) {}
    private static final List<Entry> ENTRIES = new ArrayList<>();
    static {
        add("OpenAI", "COMPANY", "OpenAI");
        add("Anthropic", "COMPANY", "Anthropic");
        add("NVIDIA", "COMPANY", "NVIDIA", "英伟达", "英偉達");
        add("Apple", "COMPANY", "Apple", "苹果公司", "苹果");
        add("Microsoft", "COMPANY", "Microsoft", "微软");
        add("Google", "COMPANY", "Google", "谷歌");
        add("Meta", "COMPANY", "Meta", "Facebook");
        add("AMD", "COMPANY", "AMD", "超威半导体");
        add("Intel", "COMPANY", "Intel", "英特尔");
        add("Oracle", "COMPANY", "Oracle", "甲骨文");
        add("Samsung", "COMPANY", "Samsung", "三星");
        add("Huawei", "COMPANY", "Huawei", "华为");
        add("腾讯", "COMPANY", "腾讯", "Tencent");
        add("阿里巴巴", "COMPANY", "阿里巴巴", "Alibaba");
        add("字节跳动", "COMPANY", "字节跳动", "ByteDance");
        add("DeepSeek", "COMPANY", "DeepSeek", "深度求索");
        add("SpaceX", "COMPANY", "SpaceX");
        add("Tesla", "COMPANY", "Tesla", "特斯拉");
        add("Sam Altman", "PERSON", "Sam Altman", "山姆·奥尔特曼", "萨姆·奥尔特曼", "奥尔特曼");
        add("Jensen Huang", "PERSON", "Jensen Huang", "黄仁勋");
        add("Elon Musk", "PERSON", "Elon Musk", "埃隆·马斯克", "马斯克");
        add("Tim Cook", "PERSON", "Tim Cook", "蒂姆·库克");
        add("联合国", "ORGANIZATION", "联合国", "United Nations", "UN");
        add("NASA", "ORGANIZATION", "NASA", "美国宇航局");
        add("Claude Code", "PRODUCT", "Claude Code");
        add("Claude", "PRODUCT", "Claude");
        add("ChatGPT", "PRODUCT", "ChatGPT");
        add("Apple Pay", "PRODUCT", "Apple Pay");
        add("GitHub Copilot", "PRODUCT", "GitHub Copilot");
        add("GitHub", "PRODUCT", "GitHub");
        add("Excalidraw", "PRODUCT", "Excalidraw");
        add("NixOS", "PRODUCT", "NixOS");
        add("PostgreSQL", "PRODUCT", "PostgreSQL", "Postgres");
        add("Mac Mini", "PRODUCT", "Mac Mini");
        add("Avast", "PRODUCT", "Avast");
        add("Ruby on Rails", "PRODUCT", "Ruby on Rails", "Rails");
        add("CUDA", "TECHNOLOGY", "CUDA");
        add("SIMD", "TECHNOLOGY", "SIMD");
        add("Rust", "TECHNOLOGY", "Rust");
        add("量子计算", "TECHNOLOGY", "量子计算", "quantum computing");
        add("人工智能", "TECHNOLOGY", "人工智能", "artificial intelligence", "AI");
    }
    private static final Pattern VERSIONED = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:GPT[- ]?\\d+(?:\\.\\d+)?[a-z]?|(?:Blackwell\\s+)?B[12]00|H100|" +
            "Claude\\s+(?:(?:Sonnet|Opus|Haiku)\\s+)?\\d+(?:\\.\\d+)?|" +
            "(?:iPhone|Nova|Gemini|Llama)\\s*\\d+(?:\\.\\d+)?(?:\\s+(?:Pro|Ultra|Max))?)(?![a-z0-9])");
    private static final Pattern IDENTIFIER = Pattern.compile("(?i)(?<![a-z0-9])CVE-\\d{4}-\\d{4,}(?![a-z0-9])");
    private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*|[\\p{IsHan}]{2,}");
    private static final Set<String> STOP = Set.of("the", "a", "an", "and", "or", "to", "of", "for", "with", "in", "on", "at", "by", "is", "as", "its", "new", "from", "your", "has", "will", "are", "it", "this", "that", "be", "can", "how", "show", "hn", "news", "report", "model", "models", "chip", "chips", "gpu", "ai", "announces", "announce", "announced", "releases", "release", "released", "launches", "launch", "unveils", "unveil", "introduces", "introduce", "begins", "starts", "ships", "shipping", "available", "delivery", "发布", "推出", "公布", "宣布", "芯片", "模型", "正式", "开始", "交付", "上市", "报道", "后续", "最新");

    private static void add(String name, String type, String... aliases) {
        ENTRIES.add(new Entry(name, type, Pattern.compile(Arrays.stream(aliases)
                .map(EventEntityResolver::literal).collect(Collectors.joining("|")), Pattern.CASE_INSENSITIVE)));
    }
    private static String literal(String value) {
        return "(?<![a-zA-Z0-9])" + Pattern.quote(value) + "(?![a-zA-Z0-9])";
    }
    public static String text(NewsItem item) {
        return safe(item.getTitle()) + "\n" + safe(item.getSummary()) + "\n" + safe(item.getDetailExcerpt());
    }
    private static String safe(String value) { return value == null ? "" : value; }

    public List<EntityMention> resolve(String text, List<EntityMention> supplied) {
        String evidence = safe(text);
        Map<String, EntityMention> result = new LinkedHashMap<>();
        Matcher versions = VERSIONED.matcher(evidence);
        while (versions.find()) put(result, canonical(versions.group()), "PRODUCT");
        for (Entry entry : ENTRIES) {
            if (entry.pattern().matcher(evidence).find()) put(result, entry.name(), entry.type());
        }
        if (supplied != null) for (EntityMention mention : supplied) {
            if (mention == null || mention.name().isBlank() || !occurs(evidence, mention.name())) continue;
            String name = canonical(mention.name());
            String type = normalizeType(mention.type(), name);
            if (!"UNKNOWN".equals(type)) put(result, name, type);
        }
        // Prefer the specific product over a nested generic mention (Claude Code over Claude).
        Set<String> nested = new HashSet<>();
        for (EntityMention shortName : result.values()) for (EntityMention longName : result.values()) {
            if (shortName.type().equals("PRODUCT") && longName.type().equals("PRODUCT")
                    && longName.name().length() > shortName.name().length()
                    && longName.name().toLowerCase(Locale.ROOT).startsWith(shortName.name().toLowerCase(Locale.ROOT)))
                nested.add(shortName.name().toLowerCase(Locale.ROOT));
        }
        nested.forEach(result::remove);
        return List.copyOf(result.values());
    }
    private static void put(Map<String, EntityMention> values, String name, String type) {
        values.putIfAbsent(name.toLowerCase(Locale.ROOT), new EntityMention(name, type));
    }
    public void enrich(NewsItem item) {
        List<EntityMention> mentions = resolve(text(item), item.getEntityMentions());
        item.setEntityMentions(mentions);
        LinkedHashSet<String> entities = mentions.stream().map(EntityMention::name).collect(Collectors.toCollection(LinkedHashSet::new));
        if (item.getEntities() != null) item.getEntities().stream().filter(name -> name != null && !name.isBlank() && occurs(text(item), name))
                .map(EventEntityResolver::canonical).forEach(entities::add);
        item.setEntities(new ArrayList<>(entities));
        item.setKeywords(new ArrayList<>(tokens(text(item))));
    }
    public static boolean occurs(String text, String name) {
        if (name == null || name.isBlank()) return false;
        return Pattern.compile(literal(name), Pattern.CASE_INSENSITIVE).matcher(text).find();
    }
    public static String canonical(String name) {
        String value = safe(name).strip();
        for (Entry entry : ENTRIES) if (entry.pattern().matcher(value).matches()) return entry.name();
        if (value.matches("(?i)(?:Blackwell\\s+)?B[12]00")) return value.toUpperCase(Locale.ROOT).replace("BLACKWELL ", "");
        if (value.matches("(?i)GPT[- ]?\\d+(?:\\.\\d+)?[a-z]?")) return value.toUpperCase(Locale.ROOT).replaceFirst("GPT[- ]?", "GPT-");
        return value.replaceAll("\\s+", " ");
    }
    public static String normalizeType(String type, String name) {
        for (Entry entry : ENTRIES) if (entry.name().equalsIgnoreCase(name)) return entry.type();
        return switch (safe(type).toUpperCase(Locale.ROOT)) {
            case "COMPANY" -> "COMPANY";
            case "ORG", "ORGANIZATION" -> name.endsWith("公司") || name.matches("(?i).*\\b(?:inc|corp|ltd)\\.?$") ? "COMPANY" : "ORGANIZATION";
            case "PERSON", "PER" -> "PERSON";
            case "PRODUCT" -> "PRODUCT";
            case "TECH", "TECHNOLOGY" -> "TECHNOLOGY";
            default -> "UNKNOWN";
        };
    }
    public static Set<String> tokens(String input) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(safe(input).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String value = matcher.group();
            if (value.matches("[\\p{IsHan}]+")) {
                for (int i = 0; i < value.length() - 1; i++) {
                    String gram = value.substring(i, i + 2);
                    if (!STOP.contains(gram)) tokens.add(gram);
                }
            } else if (!STOP.contains(value)) tokens.add(value);
        }
        return tokens;
    }
    public static Set<String> identifiers(String text) {
        Set<String> ids = new HashSet<>();
        Matcher matcher = IDENTIFIER.matcher(safe(text));
        while (matcher.find()) ids.add(matcher.group().toUpperCase(Locale.ROOT));
        return ids;
    }
    public String withoutSubjects(String text, List<EntityMention> mentions) {
        String result = safe(text);
        for (Entry entry : ENTRIES) result = entry.pattern().matcher(result).replaceAll(" ");
        result = VERSIONED.matcher(result).replaceAll(" ");
        for (EntityMention mention : mentions) result = result.replace(mention.name(), " ");
        return result;
    }
}
