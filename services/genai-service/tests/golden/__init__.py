"""Golden attribute-extraction test set for the GenAI service (issue #55).

`load_golden_set()` is importable so the #49 extraction regression suite can
reuse the same cases.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

GOLDEN_SET_PATH = Path(__file__).parent / "golden_set.json"


def load_golden_set() -> list[dict[str, Any]]:
    """Return the golden cases as a list of dict records."""
    with GOLDEN_SET_PATH.open(encoding="utf-8") as fh:
        return json.load(fh)
