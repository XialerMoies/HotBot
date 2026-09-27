export function dateTime(value) {
  if (!value) return "暂无记录";
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? String(value)
    : new Intl.DateTimeFormat("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      }).format(date);
}
export function statusLabel(value) {
  return (
    {
      EMERGING: "首次报道",
      ONGOING: "持续更新",
      FIRST_REPORT: "首次报道",
      UPDATE: "后续进展",
      SUCCESS: "成功",
      RUNNING: "运行中",
      FAILED: "失败",
      PENDING: "等待中",
    }[value] ||
    value ||
    "未知"
  );
}
export function safeUrl(value) {
  try {
    const url = new URL(value);
    return ["https:", "http:"].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}
export function sortEvents(items) {
  return [...items].sort(
    (a, b) =>
      (Date.parse(b.lastUpdatedAt) || 0) - (Date.parse(a.lastUpdatedAt) || 0),
  );
}

export function subjectTypeLabel(type) {
  return ({COMPANY:'公司',ORGANIZATION:'机构',ORG:'机构',PERSON:'人物',PRODUCT:'产品',TECHNOLOGY:'技术',TECH:'技术',UNKNOWN:'主体',keyword:'关键词'})[type] || '主体';
}
