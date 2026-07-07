"""JSON console logging (issue #278).

Emits ECS-style keys matching Spring Boot's native structured logging
(`logging.structured.format.console=ecs`), so LogQL `| json` queries hit the
same field names across every service. Active only when LOG_FORMAT=json —
set by docker-compose and Helm; local `uvicorn --reload` stays human-readable.
"""

from __future__ import annotations

import json
import logging
import os
from datetime import datetime, timezone

from opentelemetry import trace


class EcsJsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        doc = {
            "@timestamp": datetime.fromtimestamp(record.created, tz=timezone.utc).isoformat(),
            "log.level": record.levelname,
            "log.logger": record.name,
            "message": record.getMessage(),
        }
        # Same top-level keys Spring's ECS output uses for its MDC trace
        # context, so one Grafana derived-field regex covers every service
        # (issue #281). No-op when tracing is off: the span is non-recording
        # and its context invalid.
        ctx = trace.get_current_span().get_span_context()
        if ctx.is_valid:
            doc["traceId"] = format(ctx.trace_id, "032x")
            doc["spanId"] = format(ctx.span_id, "016x")
        if record.exc_info:
            doc["error.stack_trace"] = self.formatException(record.exc_info)
        return json.dumps(doc, ensure_ascii=False)


def setup_json_logging_if_configured() -> None:
    if os.getenv("LOG_FORMAT", "").lower() != "json":
        return
    handler = logging.StreamHandler()
    handler.setFormatter(EcsJsonFormatter())
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)
    # uvicorn wires its own handlers before importing the app; strip them so
    # access/error lines flow through the root JSON handler instead.
    for name in ("uvicorn", "uvicorn.access", "uvicorn.error"):
        uvicorn_logger = logging.getLogger(name)
        uvicorn_logger.handlers = []
        uvicorn_logger.propagate = True
