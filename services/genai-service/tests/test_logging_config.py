import json
import logging

from app.logging_config import EcsJsonFormatter, setup_json_logging_if_configured


def _record(**kwargs) -> logging.LogRecord:
    defaults = dict(
        name="app.test",
        level=logging.WARNING,
        pathname=__file__,
        lineno=1,
        msg="something %s",
        args=("happened",),
        exc_info=None,
    )
    defaults.update(kwargs)
    return logging.LogRecord(**defaults)


def test_formatter_emits_ecs_json_fields():
    line = EcsJsonFormatter().format(_record())
    doc = json.loads(line)
    assert doc["log.level"] == "WARNING"
    assert doc["log.logger"] == "app.test"
    assert doc["message"] == "something happened"
    assert doc["@timestamp"].endswith("+00:00")


def test_formatter_includes_stack_trace_on_exception():
    try:
        raise ValueError("boom")
    except ValueError:
        import sys

        record = _record(exc_info=sys.exc_info())
    doc = json.loads(EcsJsonFormatter().format(record))
    assert "ValueError: boom" in doc["error.stack_trace"]


def test_formatter_omits_trace_keys_without_active_span():
    doc = json.loads(EcsJsonFormatter().format(_record()))
    assert "traceId" not in doc
    assert "spanId" not in doc


def test_formatter_emits_trace_context_inside_span():
    from opentelemetry.sdk.trace import TracerProvider

    tracer = TracerProvider().get_tracer("test")
    with tracer.start_as_current_span("op") as span:
        doc = json.loads(EcsJsonFormatter().format(_record()))
    ctx = span.get_span_context()
    assert doc["traceId"] == format(ctx.trace_id, "032x")
    assert doc["spanId"] == format(ctx.span_id, "016x")


def test_setup_is_noop_without_env(monkeypatch):
    monkeypatch.delenv("LOG_FORMAT", raising=False)
    root = logging.getLogger()
    before = list(root.handlers)
    setup_json_logging_if_configured()
    assert root.handlers == before


def test_setup_installs_json_handler_and_reroutes_uvicorn(monkeypatch):
    monkeypatch.setenv("LOG_FORMAT", "json")
    root = logging.getLogger()
    before = list(root.handlers)
    try:
        setup_json_logging_if_configured()
        assert len(root.handlers) == 1
        assert isinstance(root.handlers[0].formatter, EcsJsonFormatter)
        access = logging.getLogger("uvicorn.access")
        assert access.handlers == []
        assert access.propagate is True
    finally:
        root.handlers = before
