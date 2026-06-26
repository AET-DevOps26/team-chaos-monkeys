"""Unit tests for the golden-set fuzzy comparator (tests/golden/_compare.py).

Deterministic and run in normal CI, so the gated real-LLM regression
(tests/integration/test_golden_extraction.py) rests on verified logic.
"""

from __future__ import annotations

from typing import Any

from tests.golden import load_golden_set
from tests.golden._compare import ALL_FIELDS, compare_case, failed_case


def _attrs(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "category": None,
        "description": None,
        "brand": None,
        "color": None,
        "distinguishingMarks": [],
        "approximateTime": None,
        "location": None,
    }
    base.update(overrides)
    return base


def _field(comparison, name: str):
    return next(field for field in comparison.fields if field.field == name)


# --- whole-case shape ----------------------------------------------------


def test_compare_case_covers_the_seven_fields():
    comparison = compare_case(_attrs(), _attrs())
    assert {field.field for field in comparison.fields} == set(ALL_FIELDS)
    assert comparison.total == 7


def test_identical_attributes_score_full():
    attrs = _attrs(
        category="jacket",
        description="black puffer jacket",
        brand="Sony",
        color="black",
        distinguishingMarks=["enamel pin"],
        approximateTime="11pm",
        location="the bar",
    )
    comparison = compare_case(attrs, dict(attrs))
    assert comparison.matched_count == 7
    assert comparison.score == 1.0


def test_all_null_attributes_match():
    comparison = compare_case(_attrs(), _attrs())
    assert comparison.matched_count == 7


# --- scalar null agreement ----------------------------------------------


def test_invented_scalar_is_a_miss():
    comparison = compare_case(_attrs(brand=None), _attrs(brand="Rolex"))
    assert not _field(comparison, "brand").matched


def test_missed_scalar_is_a_miss():
    comparison = compare_case(_attrs(color="red"), _attrs(color=None))
    assert not _field(comparison, "color").matched


# --- scalar fuzzy matching ----------------------------------------------


def test_casefold_difference_still_matches():
    comparison = compare_case(_attrs(color="Black"), _attrs(color="black"))
    assert _field(comparison, "color").matched


def test_containment_difference_still_matches():
    comparison = compare_case(
        _attrs(category="jacket"), _attrs(category="puffer jacket")
    )
    assert _field(comparison, "category").matched


def test_reordered_tokens_still_match():
    comparison = compare_case(
        _attrs(approximateTime="Saturday around 11pm"),
        _attrs(approximateTime="around 11pm on Saturday"),
    )
    assert _field(comparison, "approximateTime").matched


def test_unrelated_scalar_is_a_miss():
    comparison = compare_case(_attrs(category="jacket"), _attrs(category="wallet"))
    assert not _field(comparison, "category").matched


# --- distinguishingMarks -------------------------------------------------


def test_empty_marks_on_both_sides_match():
    comparison = compare_case(
        _attrs(distinguishingMarks=[]), _attrs(distinguishingMarks=[])
    )
    assert _field(comparison, "distinguishingMarks").matched


def test_invented_marks_is_a_miss():
    comparison = compare_case(
        _attrs(distinguishingMarks=[]), _attrs(distinguishingMarks=["a red pin"])
    )
    assert not _field(comparison, "distinguishingMarks").matched


def test_missed_marks_is_a_miss():
    comparison = compare_case(
        _attrs(distinguishingMarks=["a red pin"]), _attrs(distinguishingMarks=[])
    )
    assert not _field(comparison, "distinguishingMarks").matched


def test_paraphrased_mark_matches():
    comparison = compare_case(
        _attrs(distinguishingMarks=["small Berlin enamel pin on the chest"]),
        _attrs(distinguishingMarks=["Berlin enamel pin"]),
    )
    assert _field(comparison, "distinguishingMarks").matched


def test_half_the_marks_found_meets_threshold():
    comparison = compare_case(
        _attrs(distinguishingMarks=["torn zipper", "faded logo"]),
        _attrs(distinguishingMarks=["a torn zipper"]),
    )
    assert _field(comparison, "distinguishingMarks").matched


def test_unrelated_marks_is_a_miss():
    comparison = compare_case(
        _attrs(distinguishingMarks=["torn zipper"]),
        _attrs(distinguishingMarks=["faded university sticker"]),
    )
    assert not _field(comparison, "distinguishingMarks").matched


# --- counts, score, misses ----------------------------------------------


def test_mixed_case_counts_and_misses():
    expected = _attrs(category="jacket", color="red")
    actual = _attrs(category="jacket", color="blue")
    comparison = compare_case(expected, actual)
    # category matches, color misses, the five null/empty fields match.
    assert comparison.matched_count == 6
    assert [miss.field for miss in comparison.misses()] == ["color"]


def test_failed_case_misses_every_field():
    comparison = failed_case("provider timed out")
    assert comparison.matched_count == 0
    assert comparison.total == 7
    assert all("timed out" in miss.detail for miss in comparison.misses())


# --- against the real golden set ----------------------------------------


def test_each_golden_case_self_compares_full():
    for case in load_golden_set():
        comparison = compare_case(case["expected"], dict(case["expected"]))
        assert comparison.matched_count == 7, case["id"]
