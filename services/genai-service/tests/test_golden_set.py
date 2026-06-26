"""Validates the golden attribute-extraction set (issues #55, #131).

Deterministic, no LLM: proves the golden set is well-formed and conformant to
the api/openapi.yaml contract. The regression test that runs real extraction
against these cases lands with #49; image-bearing cases (#131) carry an
optional `imagePath` instead of (or alongside) `description`.
"""

from __future__ import annotations

import collections
from pathlib import Path

import pytest

from tests.golden import load_golden_set
from tests.golden._contract import validator

CASES = load_golden_set()
CASE_IDS = [case["id"] for case in CASES]

ALLOWED_PROFILES = {
    "complete",
    "partial",
    "ambiguous",
    "incomplete",
    "image_only",
    "multimodal",
}
ALLOWED_TRAITS = {
    "greeting",
    "filler",
    "irrelevant_detail",
    "mixed_language",
    "prompt_injection",
    "image_pii",
}
NULLABLE_FIELDS = ("description", "brand", "color", "approximateTime", "location")

IMAGES_DIR = Path(__file__).parent / "golden" / "images"
# Sentinel base64 — structural-only; the runner reads real bytes from imagePath.
_SENTINEL_B64 = "c2VudGluZWw="


def test_golden_set_loads():
    assert CASES, "golden set is empty"


def test_ids_are_unique():
    dupes = [i for i, n in collections.Counter(CASE_IDS).items() if n > 1]
    assert not dupes, f"duplicate case ids: {dupes}"


@pytest.mark.parametrize("case", CASES, ids=CASE_IDS)
def test_request_side_conforms_to_contract(case):
    request: dict = {}
    if "description" in case:
        request["description"] = case["description"]
    if "language" in case:
        request["language"] = case["language"]
    if "imagePath" in case:
        request["image"] = {"contentType": "image/jpeg", "dataBase64": _SENTINEL_B64}
    assert request, f"{case['id']}: case has no description or imagePath"
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


def test_image_cases_cover_adr_section_11():
    """ADR 0001 §11 requires at least 8 image cases + 2 security cases."""
    image_cases = [c for c in CASES if "imagePath" in c]
    image_only = [c for c in image_cases if c.get("profile") == "image_only"]
    multimodal = [c for c in image_cases if c.get("profile") == "multimodal"]
    traits = [t for c in image_cases for t in c.get("traits", [])]

    assert len(image_cases) >= 10, f"only {len(image_cases)} image cases (need ≥10)"
    assert len(image_only) >= 3, f"only {len(image_only)} image-only cases (need ≥3)"
    assert len(multimodal) >= 5, f"only {len(multimodal)} multimodal cases (need ≥5)"
    assert traits.count("prompt_injection") >= 1, "no prompt-injection security case"
    assert traits.count("image_pii") >= 1, "no PII-in-image security case"


@pytest.mark.parametrize(
    "case", [c for c in CASES if "imagePath" in c], ids=lambda c: c["id"]
)
def test_image_fixture_exists(case):
    fixture = IMAGES_DIR / case["imagePath"]
    assert fixture.is_file(), f"missing image fixture: {fixture}"


@pytest.mark.parametrize(
    "case", [c for c in CASES if "imagePath" in c], ids=lambda c: c["id"]
)
def test_image_fixture_under_size_cap(case):
    """Per ADR §11: ≤200 KB each post-downscale."""
    fixture = IMAGES_DIR / case["imagePath"]
    if not fixture.is_file():
        pytest.skip("fixture missing — separately reported by test_image_fixture_exists")
    size = fixture.stat().st_size
    assert size <= 200 * 1024, f"{case['imagePath']}: {size} bytes exceeds 200 KB cap"
