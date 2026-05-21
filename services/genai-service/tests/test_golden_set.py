"""Validates the golden attribute-extraction set (issue #55).

Deterministic, no LLM: proves the golden set is well-formed and conformant to
the api/openapi.yaml contract. The regression test that runs real extraction
against these cases lands with #49.
"""

from __future__ import annotations

import collections

import pytest

from tests.golden import load_golden_set
from tests.golden._contract import validator

CASES = load_golden_set()
CASE_IDS = [case["id"] for case in CASES]

ALLOWED_PROFILES = {"complete", "partial", "ambiguous", "incomplete"}
ALLOWED_TRAITS = {"greeting", "filler", "irrelevant_detail", "mixed_language"}
NULLABLE_FIELDS = ("brand", "color", "approximateTime", "location")


def test_golden_set_loads():
    assert CASES, "golden set is empty"


def test_ids_are_unique():
    dupes = [i for i, n in collections.Counter(CASE_IDS).items() if n > 1]
    assert not dupes, f"duplicate case ids: {dupes}"


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_request_side_conforms_to_contract(case):
    request = {"description": case["description"]}
    if "language" in case:
        request["language"] = case["language"]
    errors = [e.message for e in validator("ExtractAttributesRequest").iter_errors(request)]
    assert not errors, f"{case['id']}: {errors}"


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_expected_conforms_to_itemattributes_contract(case):
    errors = [e.message for e in validator("ItemAttributes").iter_errors(case["expected"])]
    assert not errors, f"{case['id']}: {errors}"


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_distinguishing_marks_always_a_list(case):
    marks = case["expected"]["distinguishingMarks"]
    assert isinstance(marks, list), f"{case['id']}: distinguishingMarks is {type(marks)}"
    assert all(isinstance(m, str) for m in marks), f"{case['id']}: non-string mark"


def test_set_covers_required_dimensions():
    expecteds = [case["expected"] for case in CASES]
    profiles = {case["profile"] for case in CASES}
    languages = {case["language"] for case in CASES if "language" in case}
    traits = [t for case in CASES for t in case.get("traits", [])]
    noisy = [case for case in CASES if case.get("traits")]
    categories = {e["category"] for e in expecteds if e["category"] is not None}

    assert profiles == ALLOWED_PROFILES, f"profile coverage: {profiles}"
    assert set(traits) <= ALLOWED_TRAITS, f"unknown trait(s): {set(traits) - ALLOWED_TRAITS}"

    assert len(categories) >= 8, f"only {len(categories)} distinct categories"
    assert len(languages) >= 6, f"only {len(languages)} distinct languages: {languages}"
    assert any("language" not in case for case in CASES), "no language-omitted (auto-detect) case"

    for field in NULLABLE_FIELDS:
        assert any(e[field] is None for e in expecteds), f"no case exercises null {field}"

    assert any(e["distinguishingMarks"] == [] for e in expecteds), "no empty-marks case"
    assert any(len(e["distinguishingMarks"]) >= 2 for e in expecteds), "no multi-mark case"

    assert len(noisy) >= 5, f"only {len(noisy)} noisy cases"
    assert traits.count("greeting") >= 2, "fewer than 2 greeting cases"
    assert "mixed_language" in traits, "no mixed-language case"
