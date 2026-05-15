"""Schema fragments from the repo-root OpenAPI contract (api/openapi.yaml).

The golden set is validated against the contract directly so it cannot silently
drift from the API the GenAI service implements (issue #48).
"""

from __future__ import annotations

import functools
from pathlib import Path
from typing import Any

import yaml
from jsonschema import Draft202012Validator


def _find_openapi_spec() -> Path:
    for parent in Path(__file__).resolve().parents:
        candidate = parent / "api" / "openapi.yaml"
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        "could not locate api/openapi.yaml above "
        f"{Path(__file__).resolve()}; the golden set validates against the "
        "repo-root OpenAPI contract"
    )


@functools.lru_cache(maxsize=1)
def _spec() -> dict[str, Any]:
    with _find_openapi_spec().open(encoding="utf-8") as fh:
        return yaml.safe_load(fh)


def schema(name: str) -> dict[str, Any]:
    """Return a named schema from `components.schemas` of the contract."""
    schemas = _spec()["components"]["schemas"]
    if name not in schemas:
        raise KeyError(f"{name!r} not in api/openapi.yaml components.schemas")
    return schemas[name]


@functools.lru_cache(maxsize=None)
def validator(name: str) -> Draft202012Validator:
    """Return a cached Draft 2020-12 validator for a named contract schema."""
    return Draft202012Validator(schema(name))
