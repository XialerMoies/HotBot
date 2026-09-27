from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from services.providers import call_single_turn


@dataclass(frozen=True)
class EvidenceItem:
    id: str
    article_id: str
    quote: str
    title: str = ""
    url: str = ""
    published_at: str = ""


def build_evidence_answer(question: str, evidence: list[EvidenceItem]) -> dict[str, Any]:
    """Generate an answer whose claims can be traced to numbered evidence."""
    if not evidence:
        return {"answer": "当前没有检索到足以支持该问题的原文证据。", "claims": [], "evidence": [], "fallback": True}
    context = "\n".join(f"[{item.id}] {item.quote}" for item in evidence)
    prompt = (
        "请只依据下面的原文证据回答问题。每个事实性结论末尾标注对应证据编号，如 [ev-1]。"
        "证据不足时明确说证据不足，不要补写。\n问题：" + question + "\n证据：\n" + context
    )
    try:
        answer = call_single_turn("你是一个严格的证据问答助手。", prompt)
        if not isinstance(answer, str) or not answer.strip():
            raise ValueError("Evidence model returned no answer")
        fallback = False
    except Exception:
        answer = "基于检索到的原文：\n" + "\n".join(f"- {item.quote} [{item.id}]" for item in evidence)
        fallback = True
    return {
        "answer": answer,
        "claims": [{"text": item.quote, "evidenceIds": [item.id]} for item in evidence],
        "evidence": [item.__dict__ for item in evidence],
        "fallback": fallback,
    }
