"""Tracing setup (issue #280): env gate + W3C traceparent continuity."""

from fastapi import FastAPI
from fastapi.testclient import TestClient
from opentelemetry.sdk.trace.export.in_memory_span_exporter import (
    InMemorySpanExporter,
)

from app import tracing

TRACE_ID = "0af7651916cd43dd8448eb211c80319c"
TRACEPARENT = f"00-{TRACE_ID}-b7ad6b7169203331-01"


def test_noop_without_endpoint(monkeypatch):
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", raising=False)
    monkeypatch.delenv("OTEL_EXPORTER_OTLP_ENDPOINT", raising=False)

    app = FastAPI()
    tracing.setup_tracing_if_configured(app)

    assert app.user_middleware == []


def test_incoming_traceparent_continues_the_trace(monkeypatch):
    """A span from a Spring caller must parent the genai span (issue #280)."""
    exporter = InMemorySpanExporter()
    monkeypatch.setenv(
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "http://tempo:4318/v1/traces"
    )
    # Swap the OTLP exporter for an in-memory one; everything else is the
    # production code path.
    monkeypatch.setattr(tracing, "OTLPSpanExporter", lambda: exporter)

    app = FastAPI()

    @app.get("/probe")
    def probe():
        return {"ok": True}

    tracing.setup_tracing_if_configured(app)
    try:
        client = TestClient(app)
        client.get("/probe", headers={"traceparent": TRACEPARENT})
        client.get("/health")  # excluded — must not produce a span

        provider = tracing.trace.get_tracer_provider()
        provider.force_flush()
        spans = exporter.get_finished_spans()

        assert spans, "instrumented route produced no span"
        assert all(f"{s.context.trace_id:032x}" == TRACE_ID for s in spans)
        assert not any("/health" in (s.name or "") for s in spans)
    finally:
        tracing.HTTPXClientInstrumentor().uninstrument()
        tracing.FastAPIInstrumentor.uninstrument_app(app)
