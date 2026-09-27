import unittest
from unittest.mock import patch

from services.evidence import EvidenceItem, build_evidence_answer


class EvidenceAnswerTest(unittest.TestCase):
    def test_blank_model_response_uses_original_evidence(self):
        for value in ["", "  ", None]:
            with self.subTest(value=value), patch("services.evidence.call_single_turn", return_value=value):
                result = build_evidence_answer("发生了什么", [EvidenceItem("ev-1", "a-1", "公司发布了产品")])
                self.assertTrue(result["fallback"])
                self.assertIn("公司发布了产品 [ev-1]", result["answer"])

    def test_empty_evidence_is_explicit_fallback(self):
        result = build_evidence_answer("发生了什么", [])
        self.assertTrue(result["fallback"])
        self.assertEqual([], result["evidence"])

    @patch("services.evidence.call_single_turn", return_value="公司发布了产品。[ev-1]")
    def test_answer_keeps_numbered_evidence(self, _mock):
        result = build_evidence_answer("发生了什么", [EvidenceItem("ev-1", "a-1", "公司发布了产品")])
        self.assertIn("[ev-1]", result["answer"])
        self.assertFalse(result["fallback"])


if __name__ == "__main__":
    unittest.main()
