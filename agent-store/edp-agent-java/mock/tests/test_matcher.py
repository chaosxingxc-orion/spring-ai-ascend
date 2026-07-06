"""Matcher regression tests against legacy mock routing behavior."""
from __future__ import annotations

import sys
import unittest
from pathlib import Path

_ROOT = Path(__file__).resolve().parent.parent
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from engine.loader import WorkflowStore
from engine.matcher import WorkflowMatcher


class MatcherRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.store = WorkflowStore()
        cls.matcher = WorkflowMatcher(cls.store)

    def resolve(self, query: str = "", **inputs: object) -> str:
        payload = {"query": query, **inputs}
        wf = self.matcher.resolve(payload)
        return wf["id"]

    def test_wealth_recommend(self) -> None:
        self.assertEqual(self.resolve("帮我推荐几款稳健型理财产品"), "wealth_recommend")

    def test_fund_recommend(self) -> None:
        self.assertEqual(self.resolve("帮我推荐基金"), "fund_recommend")

    def test_product_buy(self) -> None:
        q = "理财产品：产品名称：测试，产品代码：XLT1801，金额：300元"
        self.assertEqual(self.resolve(q), "product_buy")

    def test_product_buy_not_wealth(self) -> None:
        q = "理财产品：产品名称：测试，产品代码：XLT1801，金额：300元"
        self.assertNotEqual(self.resolve(q), "wealth_recommend")

    def test_balance_query(self) -> None:
        self.assertEqual(self.resolve("查余额"), "balance_query")

    def test_transfer_round1(self) -> None:
        self.assertEqual(self.resolve("转账1000元"), "transfer_round1")

    def test_transfer_confirmed_context(self) -> None:
        self.assertEqual(
            self.resolve(query="", menu_type="TRANSFER_MENU", menu_confirm=True),
            "transfer_confirmed",
        )

    def test_transfer_no_confirm_context(self) -> None:
        self.assertEqual(
            self.resolve(query="", menu_type="TRANSFER_MENU", menu_confirm=False),
            "transfer_no_confirm",
        )

    def test_context_overrides_query(self) -> None:
        self.assertEqual(
            self.resolve(query="转账1000元", menu_type="TRANSFER_MENU", menu_confirm=True),
            "transfer_confirmed",
        )

    def test_transfer_not_balance_when_query_has_account_word(self) -> None:
        q = "从尾号3344的卡转账300元到账户11223344，收款人：张三"
        self.assertEqual(self.resolve(q), "transfer_round1")

    def test_balance_query_still_works(self) -> None:
        self.assertEqual(self.resolve("查询尾号为3344的卡的余额"), "balance_query")


if __name__ == "__main__":
    unittest.main()
