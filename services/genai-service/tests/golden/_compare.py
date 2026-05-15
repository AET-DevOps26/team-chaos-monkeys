"""Field-level fuzzy comparison for the golden-set extraction regression.

Real LLM output is never character-exact, so `test_golden_extraction.py`
scores each extracted `ItemAttributes` against the hand-authored `expected`
field by field rather than asserting equality.

Strategy
--------
- Null agreement is the primary signal: a field the description does not
  support must come back null (scalars) or [] (marks). Inventing a value,
  or nulling out a value the description gives, is a miss.
- Scalar fields, both non-null: normalise (casefold + collapse whitespace),
  then accept an exact match, containment either way, or token overlap above
  a threshold — leniency for paraphrase ("jacket" vs "puffer jacket").
- `distinguishingMarks`: emptiness must agree; when both are non-empty, score
  the fraction of expected marks that have a fuzzily-matching actual mark.

The comparator is deterministic and unit-tested (`test_golden_compare.py`),
so the gated real-LLM run rests on verified logic.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from app.extraction import item_attribute_fields

# Derived from ItemAttributes so the comparator cannot silently drift from
# the model when a field is added or renamed.
ALL_FIELDS = tuple(item_attribute_fields())
MARKS_FIELD = "distinguishingMarks"

_SCALAR_THRESHOLD = 0.5
_MARK_THRESHOLD = 0.5


def _norm(text: str) -> str:
    return " ".join(text.casefold().split())


def _tokens(text: str) -> set[str]:
    return set(_norm(text).split())


def _jaccard(a: str, b: str) -> float:
    """Jaccard overlap of the word sets of two strings."""
    ta, tb = _tokens(a), _tokens(b)
    if not ta or not tb:
        return 1.0 if ta == tb else 0.0
    return len(ta & tb) / len(ta | tb)


def _containment(a: str, b: str) -> float:
    """Shared word fraction relative to the shorter phrase.

    Rewards subset relationships — the common paraphrase pattern where the
    model gives more or fewer words for the same feature ("small Berlin
    enamel pin on the chest" vs "Berlin enamel pin").
    """
    ta, tb = _tokens(a), _tokens(b)
    if not ta or not tb:
        return 1.0 if ta == tb else 0.0
    return len(ta & tb) / min(len(ta), len(tb))


def _scalar_matches(expected: str, actual: str) -> bool:
    e, a = _norm(expected), _norm(actual)
    if not e or not a:
        return e == a
    if e == a or e in a or a in e:
        return True
    return _jaccard(e, a) >= _SCALAR_THRESHOLD


def _marks_score(expected: list[str], actual: list[str]) -> float:
    """Fraction of expected marks with a fuzzily-matching actual mark.

    Empty-vs-empty scores 1.0; empty-vs-non-empty (or the reverse) scores 0.0.
    """
    if not expected:
        return 1.0 if not actual else 0.0
    matched = sum(
        1
        for want in expected
        if any(_containment(want, got) >= _MARK_THRESHOLD for got in actual)
    )
    return matched / len(expected)


@dataclass(frozen=True)
class FieldResult:
    field: str
    matched: bool
    detail: str


@dataclass(frozen=True)
class CaseComparison:
    fields: tuple[FieldResult, ...]

    @property
    def matched_count(self) -> int:
        return sum(1 for field in self.fields if field.matched)

    @property
    def total(self) -> int:
        return len(self.fields)

    @property
    def score(self) -> float:
        return self.matched_count / self.total if self.fields else 0.0

    def misses(self) -> list[FieldResult]:
        return [field for field in self.fields if not field.matched]


def _compare_scalar(field: str, expected: Any, actual: Any) -> FieldResult:
    if expected is None and actual is None:
        return FieldResult(field, True, "both null")
    if expected is None:
        return FieldResult(field, False, f"expected null, got {actual!r}")
    if actual is None:
        return FieldResult(field, False, f"expected {expected!r}, got null")
    if _scalar_matches(str(expected), str(actual)):
        return FieldResult(field, True, f"{expected!r} ~ {actual!r}")
    return FieldResult(field, False, f"expected {expected!r}, got {actual!r}")


def _compare_marks(expected: list[str], actual: list[str]) -> FieldResult:
    score = _marks_score(expected, actual)
    detail = f"expected {expected}, got {actual} (score {score:.2f})"
    return FieldResult(MARKS_FIELD, score >= _MARK_THRESHOLD, detail)


def compare_case(expected: dict[str, Any], actual: dict[str, Any]) -> CaseComparison:
    """Compare an extracted attribute set against the golden `expected`.

    Both dicts are camelCase-keyed `ItemAttributes` shapes. Use
    `ItemAttributes.model_dump(by_alias=True)` to produce `actual`.
    """
    results: list[FieldResult] = []
    for field in ALL_FIELDS:
        if field == MARKS_FIELD:
            results.append(
                _compare_marks(expected.get(field) or [], actual.get(field) or [])
            )
        else:
            results.append(
                _compare_scalar(field, expected.get(field), actual.get(field))
            )
    return CaseComparison(tuple(results))


def failed_case(reason: str) -> CaseComparison:
    """A comparison where every field is a miss — for a case that errored out."""
    return CaseComparison(
        tuple(FieldResult(field, False, reason) for field in ALL_FIELDS)
    )
