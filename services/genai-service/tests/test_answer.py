import pytest
from pydantic import ValidationError

from app.api.schemas import (
    AnswerOutput,
    AnswerRequest,
    AnswerResponse,
)


def test_answer_request_parses_camelcase_wire():
    req = AnswerRequest.model_validate(
        {
            "query": "black leather wallet near the lobby",
            "snippets": [
                {
                    "id": "11111111-1111-1111-1111-111111111111",
                    "itemType": "found_item",
                    "category": "wallet",
                    "text": "Black bifold leather wallet handed in at lobby reception",
                }
            ],
        }
    )
    assert req.snippets[0].item_type == "found_item"


def test_answer_request_rejects_empty_query():
    with pytest.raises(ValidationError):
        AnswerRequest.model_validate({"query": "", "snippets": []})


def test_answer_output_requires_grounded_flag():
    out = AnswerOutput.model_validate(
        {"answer": "Likely match [1].", "citations": ["x"], "grounded": True}
    )
    assert out.grounded is True


def test_answer_response_serialises_camelcase():
    resp = AnswerResponse(
        answer="No matching items found.",
        citations=[],
        grounded=False,
        model_info={"provider": "fake", "model": "fake"},
    )
    assert resp.model_dump(by_alias=True)["modelInfo"]["provider"] == "fake"
