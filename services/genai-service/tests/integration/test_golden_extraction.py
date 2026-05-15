"""Gated real-LLM regression: extraction against the 22-case golden set.

NOT run in normal CI. Set GENAI_RUN_GOLDEN=1 plus the provider env
(GENAI_PROVIDER and its credentials) to run it. This mirrors the
env-gating of test_real_provider.py (#82) but uses its own switch: the
nightly genai-integration workflow runs a tiny Ollama model that is too
small to extract reliably, so it must not pick this suite up.

    GENAI_RUN_GOLDEN=1 GENAI_PROVIDER=openai OPENAI_API_KEY=sk-... \\
        pytest tests/integration/test_golden_extraction.py -s

The run makes one LLM call per case (22 total, plus one retry on transient
failures); expect a few minutes. It prints a per-case report and asserts
loose aggregate floors — not a tight gate, but enough to catch a prompt
change that tanks extraction quality.
"""

from __future__ import annotations

import os
from collections.abc import AsyncIterator
from statistics import mean

import pytest

from app.api.schemas import ItemAttributes
from app.config import Settings
from app.exceptions import (
    LLMError,
    LLMRateLimitError,
    LLMTimeoutError,
    ModelOutputError,
)
from app.extraction import extract_attributes
from app.providers import LLMProvider, build_provider
from tests.golden import load_golden_set
from tests.golden._compare import CaseComparison, compare_case, failed_case

pytestmark = pytest.mark.skipif(
    os.getenv("GENAI_RUN_GOLDEN") != "1",
    reason="set GENAI_RUN_GOLDEN=1 (and provider env) to run the real-LLM golden regression",
)

# Aggregate floors, calibrated against a llama3.2:3b run (field accuracy
# 93.2%, case pass rate 90.9%) and set well below it to absorb model and
# run-to-run variance — Ollama samples at a non-zero temperature. They exist
# to catch a catastrophic regression (e.g. prompt drift that leaks example
# values, which measured 82.6% / 63.6%), not to gate on a tight quality bar.
# Re-check if the default provider or model changes.
MIN_FIELD_ACCURACY = 0.85  # mean fraction of fields matched across all cases
MIN_CASE_PASS_RATE = 0.72  # fraction of cases matching >= _CASE_PASS_FIELDS
_CASE_PASS_FIELDS = 5

# Transient failures worth one retry before the case is counted as failed.
_RETRYABLE = (ModelOutputError, LLMRateLimitError, LLMTimeoutError)


@pytest.fixture
async def provider() -> AsyncIterator[LLMProvider]:
    llm = build_provider(Settings())
    try:
        yield llm
    finally:
        await llm.aclose()


async def _extract_with_retry(
    case: dict, provider: LLMProvider, attempts: int = 2
) -> ItemAttributes:
    """Extract one case, retrying once on a transient failure."""
    for attempt in range(1, attempts + 1):
        try:
            return await extract_attributes(
                case["description"], case.get("language"), provider
            )
        except _RETRYABLE:
            if attempt == attempts:
                raise
    raise AssertionError("unreachable")


async def test_golden_extraction_regression(provider: LLMProvider) -> None:
    results: list[tuple[str, CaseComparison]] = []
    for case in load_golden_set():
        try:
            attrs = await _extract_with_retry(case, provider)
            comparison = compare_case(case["expected"], attrs.model_dump(by_alias=True))
        except (ModelOutputError, LLMError) as exc:
            comparison = failed_case(f"{type(exc).__name__}: {exc}")
        results.append((case["id"], comparison))

    field_accuracy = mean(comparison.score for _, comparison in results)
    case_pass_rate = mean(
        1.0 if comparison.matched_count >= _CASE_PASS_FIELDS else 0.0
        for _, comparison in results
    )
    print(_format_report(results, field_accuracy, case_pass_rate))

    assert (
        field_accuracy >= MIN_FIELD_ACCURACY
    ), f"field accuracy {field_accuracy:.1%} below floor {MIN_FIELD_ACCURACY:.0%}"
    assert (
        case_pass_rate >= MIN_CASE_PASS_RATE
    ), f"case pass rate {case_pass_rate:.1%} below floor {MIN_CASE_PASS_RATE:.0%}"


def _format_report(
    results: list[tuple[str, CaseComparison]],
    field_accuracy: float,
    case_pass_rate: float,
) -> str:
    lines = ["", "Golden extraction regression report", "=" * 44]
    for case_id, comparison in results:
        lines.append(f"  {comparison.matched_count}/{comparison.total}  {case_id}")
        for miss in comparison.misses():
            lines.append(f"          miss {miss.field}: {miss.detail}")
    lines.append("-" * 44)
    lines.append(f"  field accuracy:  {field_accuracy:.1%}")
    lines.append(
        f"  case pass rate:  {case_pass_rate:.1%}"
        f"  (>= {_CASE_PASS_FIELDS}/6 fields per case)"
    )
    return "\n".join(lines)
