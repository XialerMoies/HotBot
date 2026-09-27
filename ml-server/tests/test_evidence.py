import unittest
import json
from unittest.mock import patch

from services.evidence import EvidenceItem, build_evidence_answer


class EvidenceAnswerTest(unittest.TestCase):
    @patch('services.evidence.call_single_turn')
    def test_reasoning_model_has_budget_for_claim_output(self, model):
        def reply(*args, **kwargs):
            return json.dumps({'claims':[{'text':'公司公布计划','evidenceIds':['ev-1']}]}) if kwargs['max_tokens'] >= 3000 else ''
        model.side_effect=reply
        self.assertFalse(build_evidence_answer('发生了什么',[EvidenceItem('ev-1','a','公司公布计划')])['fallback'])

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

    @patch("services.evidence.call_single_turn", return_value=json.dumps({"claims":[{"text":"公司发布了产品。","evidenceIds":["ev-1"]}]}))
    def test_answer_keeps_numbered_evidence(self, _mock):
        result = build_evidence_answer("发生了什么", [EvidenceItem("ev-1", "a-1", "公司发布了产品")])
        self.assertIn("[ev-1]", result["answer"])
        self.assertFalse(result["fallback"])

    def test_unknown_or_missing_citations_never_escape_as_generated_answer(self):
        for response in [json.dumps({'claims':[{'text':'利润翻倍','evidenceIds':['invented']}]}),
                         json.dumps({'claims':[{'text':'利润翻倍','evidenceIds':[]}]}), '利润翻倍 [ev-1]']:
            with self.subTest(response=response), patch('services.evidence.call_single_turn', return_value=response):
                result=build_evidence_answer('利润如何', [EvidenceItem('ev-1','a','公司仅公布产品计划')])
                self.assertTrue(result['fallback'])
                self.assertNotIn('利润翻倍',result['answer'])

    @patch('services.evidence.call_single_turn', return_value=json.dumps({'claims':[{'text':'公司计划下月交付','evidenceIds':['ev-2']}]}))
    def test_claims_are_actual_model_claims_not_one_claim_per_source(self, _mock):
        result=build_evidence_answer('何时交付',[EvidenceItem('ev-1','a','昨日发布'),EvidenceItem('ev-2','b','下月交付')])
        self.assertEqual([{'text':'公司计划下月交付','evidenceIds':['ev-2']}],result['claims'])

    @patch('services.evidence.call_single_turn')
    def test_no_evidence_does_not_call_model(self, model):
        build_evidence_answer('为什么',[])
        model.assert_not_called()


if __name__ == "__main__":
    unittest.main()
