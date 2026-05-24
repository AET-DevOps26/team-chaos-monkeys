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
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012


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


_SPEC_URI = "urn:openapi"


@functools.lru_cache(maxsize=1)
def _registry() -> Registry:
    """Registry that resolves OpenAPI `#/components/schemas/<Name>` refs.

    Required for schemas that reference siblings (e.g. ExtractAttributesRequest
    → ImageContent) — without it `iter_errors` raises `PointerToNowhere` on
    the first `$ref` because the schema fragment has no base URI.
    """
    return Registry().with_resource(
        uri=_SPEC_URI,
        resource=Resource(contents=_spec(), specification=DRAFT202012),
    )


@functools.lru_cache(maxsize=None)
def validator(name: str) -> Draft202012Validator:
    """Return a cached Draft 2020-12 validator for a named contract schema.

    The validator schema is a proxy `$ref` into the spec resource; that
    ref-from-registry establishes the base URI so the *inner* relative refs
    (e.g. `#/components/schemas/ImageContent`) resolve against the same spec.
    """
    if name not in _spec()["components"]["schemas"]:
        raise KeyError(f"{name!r} not in api/openapi.yaml components.schemas")
    proxy = {"$ref": f"{_SPEC_URI}#/components/schemas/{name}"}
    return Draft202012Validator(proxy, registry=_registry())
