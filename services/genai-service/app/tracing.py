"""OTLP distributed tracing (issue #280).

Exports spans straight to Tempo's OTLP HTTP receiver, mirroring the Spring
services' direct-to-Tempo setup (no collector hop). Active only when
OTEL_EXPORTER_OTLP_TRACES_ENDPOINT (or OTEL_EXPORTER_OTLP_ENDPOINT) is set —
docker-compose and Helm set it; bare `uvicorn --reload` and unit tests trace
nothing. FastAPI instrumentation extracts incoming W3C `traceparent`, so a
Spring caller's trace continues into this service; httpx instrumentation
carries it onward into provider calls (OpenAI/Ollama both use httpx).
"""

from __future__ import annotations

import os

from fastapi import FastAPI
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


def setup_tracing_if_configured(app: FastAPI) -> None:
    endpoint = os.getenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT") or os.getenv(
        "OTEL_EXPORTER_OTLP_ENDPOINT"
    )
    if not endpoint:
        return

    provider = TracerProvider(
        resource=Resource.create({"service.name": "genai-service"})
    )
    # OTLPSpanExporter reads the endpoint from the same env vars we gated on.
    provider.add_span_processor(BatchSpanProcessor(OTLPSpanExporter()))
    trace.set_tracer_provider(provider)

    # Health probes and Prometheus scrapes would dominate the trace volume.
    FastAPIInstrumentor.instrument_app(
        app, tracer_provider=provider, excluded_urls="health,metrics"
    )
    HTTPXClientInstrumentor().instrument(tracer_provider=provider)
