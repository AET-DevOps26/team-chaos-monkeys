"""FastAPI dependency helpers.

Kept in its own module so route modules can import `get_llm` without
pulling in `app.main` (which would cause a circular import once routers
are mounted there).
"""

from __future__ import annotations

from fastapi import Request

from app.providers import LLMProvider


def get_llm(request: Request) -> LLMProvider:
    return request.app.state.llm
