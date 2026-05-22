"""Golden attribute-extraction test set for the GenAI service (issues #55, #90).

`load_golden_set()` is importable so the #49 extraction regression suite can
reuse the same cases.

Scope:
- Text-description cases — the original set from #55.
- Image-bearing cases (added under #90) carry an optional `imagePath` field;
  the runner reads the file, base64-encodes it, and passes it through
  `app.image.prepare_image` before calling `extract_attributes`. Fixtures
  live in `tests/golden/images/`.

Image fixtures must be PII-free (staged photos or neutral stock images) and
have EXIF stripped before commit — see ADR 0001 §11.
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
