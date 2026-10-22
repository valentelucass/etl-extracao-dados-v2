"""Pure design consumer. No runtime API, physical adapter, authority, worker, or provider.

Budget numbers are abstract model credits, never SQL/HTTP/connection allowances.
All successful and refused results remain MODEL_ONLY_NO_EFFECT_AUTHORITY.
Run only when Root adopts and qualifies this design phase. Importing does no I/O.
"""

import argparse
import hashlib
import json
import pathlib
import re
import sys


KIND = "MODEL_ONLY_NO_EFFECT_AUTHORITY"
MAX_I64 = 9_223_372_036_854_775_807
UNITS = ("MODEL_ANTECEDENT", "MODEL_CAPTURE", "MODEL_CONFIRM", "MODEL_RECOVERY")
MAX_INPUT_BYTES = 262_144
MAX_CASES = 64
MAX_EVENTS = 32


class ContractError(ValueError):
    """Only code-owned categorical messages; never includes an input value."""


def result(accepted, code, details=None):
    return {
        "kind": KIND,
        "authorizesPhysicalEffects": False,
        "acceptedInModel": accepted,
        "code": code,
        "details": {} if details is None else details,
    }


def shape(value, keys, code):
    if type(value) is not dict or set(value) != set(keys):
        raise ContractError(code)


def integer(value, minimum=0):
    if type(value) is not int or value < minimum or value > MAX_I64:
        raise ContractError("MODEL_INTEGER_BOUND")
    return value


def symbol(value):
    if type(value) is not str or re.fullmatch(r"MODEL_[A-Z0-9_]{1,48}", value) is None:
        raise ContractError("MODEL_SYNTHETIC_SYMBOL_REQUIRED")
    return value


def scope(value):
    shape(value, ("source", "tenant", "occurrence"), "MODEL_SCOPE_SHAPE")
    for item in value.values():
        symbol(item)
    return value


def scope_matches(actual, expected):
    if scope(actual) != scope(expected):
        raise ContractError("MODEL_SCOPE_MISMATCH")


def add_exact(*values):
    total = 0
    for value in values:
        integer(value)
        if total > MAX_I64 - value:
            raise ContractError("MODEL_BUDGET_SUM_OVERFLOW")
        total += value
    return total


def intent(value):
    shape(value, ("scope", "expectedScope", "geometry", "terminal"), "MODEL_INTENT_SHAPE")
    scope_matches(value["scope"], value["expectedScope"])
    geometry = value["geometry"]
    shape(geometry, ("physicalRows", "roots", "page", "batches"), "MODEL_GEOMETRY_SHAPE")
    for number in geometry.values():
        integer(number)
    if not 1 <= geometry["physicalRows"] <= 12:
        raise ContractError("MODEL_PARTIAL_ROW_BOUND")
    if (geometry["roots"], geometry["page"], geometry["batches"]) != (1, 1, 1):
        raise ContractError("MODEL_PARTIAL_GEOMETRY_BOUND")
    shape(value["terminal"], ("state", "reason"), "MODEL_TERMINAL_SHAPE")
    if value["terminal"] != {"state": "CANCELLED", "reason": "TEST_PAGE_LIMIT"}:
        raise ContractError("MODEL_PARTIAL_TERMINAL_MISMATCH")
    return result(True, "MODEL_INTENT_CONTRACT_VALID", dict(geometry))


def budget(value):
    selected = None
    used = {name: 0 for name in UNITS}
    attempts = 0
    finalized = False
    limits = {}
    reserve = {}

    def snapshot():
        if selected not in limits or selected not in reserve:
            return {}
        return {
            "selectedUnit": selected,
            "usedCredits": used[selected],
            "remainingCredits": limits[selected] - used[selected],
            "reservedSuffixCredits": reserve[selected],
            "commitAttemptsInModel": attempts,
            "finalizedInModel": finalized,
            "otherUnitsConsumedCredits": sum(used[name] for name in UNITS if name != selected),
        }

    try:
        shape(value, ("creditSemantics", "selectedUnit", "units", "events"), "MODEL_BUDGET_SHAPE")
        if value["creditSemantics"] != "ABSTRACT_MODEL_CREDIT_NOT_SQL_OR_HTTP":
            raise ContractError("MODEL_ABSTRACT_CREDIT_SEMANTICS_REQUIRED")
        candidate = value["selectedUnit"]
        if type(candidate) is not str or candidate not in UNITS:
            raise ContractError("MODEL_BUDGET_UNIT_MISMATCH")
        selected = candidate
        shape(value["units"], UNITS, "MODEL_SEPARATE_BUDGETS_REQUIRED")
        for name in UNITS:
            definition = value["units"][name]
            shape(definition, ("limitCredits", "reservedSuffixCredits"), "MODEL_BUDGET_DEFINITION")
            limits[name] = integer(definition["limitCredits"])
            reserve[name] = integer(definition["reservedSuffixCredits"])
            if reserve[name] > limits[name]:
                raise ContractError("MODEL_SUFFIX_EXCEEDS_CAP")
        if limits[selected] < 1 or reserve[selected] < 1:
            raise ContractError("MODEL_EXPLICIT_FINALIZATION_SPACE_REQUIRED")
        events = value["events"]
        if type(events) is not list or not 1 <= len(events) <= MAX_EVENTS:
            raise ContractError("MODEL_EVENT_BOUND")
        for event in events:
            if type(event) is not dict:
                raise ContractError("MODEL_EVENT_SHAPE")
            if event.get("unit") != selected:
                raise ContractError("MODEL_BUDGET_UNIT_MISMATCH")
            action = event.get("event")
            if finalized:
                raise ContractError("MODEL_FINALIZED_UNIT_CANNOT_REOPEN")
            if action == "CHARGE":
                shape(event, ("event", "unit", "costCredits", "proofCredits"), "MODEL_EVENT_SHAPE")
                if attempts != 0:
                    raise ContractError("MODEL_CAPTURE_WORK_AFTER_COMMIT_ATTEMPT_REFUSED")
                cost = integer(event["costCredits"], 1)
                proof = integer(event["proofCredits"])
                required = add_exact(used[selected], cost, proof, reserve[selected])
                if required > limits[selected]:
                    raise ContractError("MODEL_BUDGET_INSUFFICIENT_FOR_PROOF_AND_SUFFIX")
                # Reservation/attempt consumption is monotonic; a later failure never refunds it.
                used[selected] = add_exact(used[selected], cost, proof)
            elif action == "FAILURE_OBSERVED":
                shape(event, ("event", "unit"), "MODEL_EVENT_SHAPE")
            elif action == "COMMIT_ATTEMPT":
                shape(event, ("event", "unit", "costCredits"), "MODEL_EVENT_SHAPE")
                if selected != "MODEL_CAPTURE":
                    raise ContractError("MODEL_COMMIT_OUTSIDE_CAPTURE_REFUSED")
                if attempts != 0:
                    raise ContractError("MODEL_COMMIT_RETRY_REFUSED")
                cost = integer(event["costCredits"], 1)
                if add_exact(used[selected], cost, reserve[selected]) > limits[selected]:
                    raise ContractError("MODEL_COMMIT_BUDGET_INSUFFICIENT_FOR_SUFFIX")
                # Charge the explicit attempt cost before recording the attempt. The reserved
                # finalization suffix is separate and cannot finance or refund this cost.
                used[selected] = add_exact(used[selected], cost)
                attempts = 1
            elif action == "FINALIZE":
                shape(event, ("event", "unit", "costCredits"), "MODEL_EVENT_SHAPE")
                cost = integer(event["costCredits"], 1)
                if cost > reserve[selected]:
                    raise ContractError("MODEL_FINALIZATION_RESERVE_EXCEEDED")
                if add_exact(used[selected], cost) > limits[selected]:
                    raise ContractError("MODEL_FINALIZATION_CAP_EXCEEDED")
                used[selected] += cost
                finalized = True
            elif action == "REFUND":
                shape(event, ("event", "unit", "credits"), "MODEL_EVENT_SHAPE")
                integer(event["credits"], 1)
                raise ContractError("MODEL_RECREDIT_REFUSED")
            else:
                raise ContractError("MODEL_EVENT_NOT_ALLOWED")
        return result(True, "MODEL_BUDGET_SEQUENCE_VALID", snapshot())
    except ContractError as failure:
        return result(False, str(failure), snapshot())


def observation(value):
    shape(
        value,
        ("scope", "expectedScope", "owner", "captureConnection", "commit", "readback", "close", "rollback", "confirmation"),
        "MODEL_OBSERVATION_SHAPE",
    )
    scope_matches(value["scope"], value["expectedScope"])
    owner = symbol(value["owner"])
    capture_connection = symbol(value["captureConnection"])
    commit, readback, close, rollback = (value[name] for name in ("commit", "readback", "close", "rollback"))
    if commit not in ("NOT_ATTEMPTED", "ACK_KNOWN", "UNKNOWN"):
        raise ContractError("MODEL_COMMIT_DIMENSION")
    if readback not in ("NOT_OBSERVED", "MATCHED", "ABSENT", "UNAVAILABLE", "INCONSISTENT"):
        raise ContractError("MODEL_READBACK_DIMENSION")
    if close not in ("NOT_OBSERVED", "CLOSED", "UNKNOWN"):
        raise ContractError("MODEL_CLOSE_DIMENSION")
    if rollback not in ("NOT_ATTEMPTED", "CONFIRMED", "UNKNOWN", "NOT_APPLICABLE"):
        raise ContractError("MODEL_ROLLBACK_DIMENSION")
    if commit != "NOT_ATTEMPTED" and rollback in ("CONFIRMED", "UNKNOWN"):
        raise ContractError("MODEL_COMPENSATION_AFTER_POSSIBLE_COMMIT_REFUSED")
    confirmation = value["confirmation"]
    if readback != "NOT_OBSERVED":
        shape(confirmation, ("scope", "connection", "unit", "verifier"), "MODEL_CONFIRMATION_SHAPE")
        scope_matches(confirmation["scope"], value["expectedScope"])
        if (
            symbol(confirmation["connection"]) == capture_connection
            or confirmation["unit"] != "MODEL_CONFIRM"
            or symbol(confirmation["verifier"]) == owner
        ):
            raise ContractError("MODEL_INDEPENDENT_CONFIRMATION_REQUIRED")
    elif confirmation is not None:
        raise ContractError("MODEL_UNOBSERVED_CONFIRMATION_MUST_BE_ABSENT")
    if readback == "MATCHED" and commit == "NOT_ATTEMPTED":
        raise ContractError("MODEL_OWN_RETENTION_WITHOUT_ATTEMPT_INCONSISTENT")
    if readback == "MATCHED":
        code = "MODEL_CURRENT_RETENTION_OBSERVED_ACK_UNKNOWN" if commit == "UNKNOWN" else (
            "MODEL_ACK_KNOWN_RETENTION_MATCHED_CLOSE_UNKNOWN" if close == "UNKNOWN" else (
                "MODEL_DESIGN_RETENTION_CONFIRMED_STATE" if close == "CLOSED" else "MODEL_ACK_KNOWN_RETENTION_MATCHED_CLOSE_PENDING"
            )
        )
    elif readback == "ABSENT":
        code = {
            "UNKNOWN": "MODEL_CURRENT_ABSENT_COMMIT_UNKNOWN",
            "ACK_KNOWN": "MODEL_CURRENT_ABSENT_AFTER_KNOWN_ACK",
            "NOT_ATTEMPTED": "MODEL_CURRENT_ABSENT_NO_COMMIT_INFERENCE",
        }[commit]
    elif readback == "UNAVAILABLE":
        code = "MODEL_ACK_KNOWN_OBSERVATION_UNAVAILABLE" if commit == "ACK_KNOWN" else "MODEL_COMMIT_NOT_RESOLVED_OBSERVATION_UNAVAILABLE"
    elif readback == "INCONSISTENT":
        code = "MODEL_CURRENT_STATE_INCONSISTENT"
    else:
        code = "MODEL_OBSERVATION_PENDING"
    return result(True, code, {
        "commitKnowledge": commit,
        "readbackState": readback,
        "closeState": close,
        "rollbackState": rollback,
        "candidateConfirmedInModel": commit == "ACK_KNOWN" and readback == "MATCHED" and close == "CLOSED",
        "historicalAckReconstructed": False,
        "historicalRollbackInferredFromAbsence": False,
    })


def evaluate(case):
    try:
        shape(case, ("kind", "authorizesPhysicalEffects", "id", "contract", "input", "expected", "expectedRationale"), "MODEL_VECTOR_SHAPE")
        if case["kind"] != KIND or case["authorizesPhysicalEffects"] is not False:
            raise ContractError("MODEL_ONLY_ENVELOPE_REQUIRED")
        symbol(case["id"])
        validators = {"INTENT": intent, "BUDGET": budget, "OBSERVATION": observation}
        if type(case["contract"]) is not str or case["contract"] not in validators:
            raise ContractError("MODEL_CONTRACT_NOT_ALLOWED")
        return validators[case["contract"]](case["input"])
    except ContractError as failure:
        return result(False, str(failure))


def no_duplicate_fields(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise ContractError("MODEL_DUPLICATE_JSON_FIELD")
        value[key] = item
    return value


def reject_json_constant(_value):
    raise ContractError("MODEL_NONFINITE_JSON_NUMBER")


def same_json(actual, expected):
    """JSON booleans are not interchangeable with numeric 0/1 in an expected result."""
    if type(actual) is not type(expected):
        return False
    if type(actual) is dict:
        return actual.keys() == expected.keys() and all(same_json(actual[key], expected[key]) for key in actual)
    if type(actual) is list:
        return len(actual) == len(expected) and all(same_json(left, right) for left, right in zip(actual, expected))
    return actual == expected


class ModelArguments(argparse.ArgumentParser):
    def error(self, _message):
        raise ContractError("MODEL_ARGUMENTS_REFUSED")


def main():
    parser = ModelArguments(description="Design vectors only; no physical effect authority")
    parser.add_argument("--output", required=True, help="new sibling model-result-NN.json only")
    arguments = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parent
    output = pathlib.Path(arguments.output).absolute()
    if output.parent != root or re.fullmatch(r"model-result-[0-9]{2,6}\.json", output.name) is None:
        raise ContractError("MODEL_NEW_SIBLING_OUTPUT_REQUIRED")
    if output.exists() or output.is_symlink():
        raise ContractError("MODEL_OUTPUT_MUST_BE_NEW")
    vector_path = root / "contract-vectors.json"
    if vector_path.is_symlink():
        raise ContractError("MODEL_VECTOR_LINK_REFUSED")
    with vector_path.open("rb") as stream:
        data = stream.read(MAX_INPUT_BYTES + 1)
    if len(data) > MAX_INPUT_BYTES:
        raise ContractError("MODEL_VECTOR_BYTES_BOUND")
    document = json.loads(data.decode("utf-8"), object_pairs_hook=no_duplicate_fields, parse_constant=reject_json_constant)
    shape(document, ("kind", "authorizesPhysicalEffects", "schema", "creditSemantics", "expectedSource", "cases"), "MODEL_DOCUMENT_SHAPE")
    if (
        document["kind"] != KIND
        or document["authorizesPhysicalEffects"] is not False
        or document["schema"] != "C78_RETENTION_PURE_DESIGN_VECTORS_V1"
        or document["creditSemantics"] != "ABSTRACT_MODEL_CREDIT_NOT_SQL_OR_HTTP"
        or document["expectedSource"] != "HAND_AUTHORED_CONTRACT_EXPECTATIONS_NOT_DERIVED_FROM_MODEL_OUTPUT"
    ):
        raise ContractError("MODEL_DOCUMENT_ENVELOPE")
    cases = document["cases"]
    if type(cases) is not list or not 1 <= len(cases) <= MAX_CASES:
        raise ContractError("MODEL_CASE_BOUND")
    seen = set()
    outcomes = []
    for case in cases:
        if type(case) is not dict or type(case.get("id")) is not str:
            raise ContractError("MODEL_VECTOR_SHAPE")
        if case["id"] in seen:
            raise ContractError("MODEL_DUPLICATE_VECTOR_ID")
        seen.add(case["id"])
        actual = evaluate(case)
        expected = case.get("expected")
        shape(expected, ("kind", "authorizesPhysicalEffects", "acceptedInModel", "code", "details"), "MODEL_EXPECTED_SHAPE")
        if expected["kind"] != KIND or expected["authorizesPhysicalEffects"] is not False:
            raise ContractError("MODEL_EXPECTED_ONLY_ENVELOPE_REQUIRED")
        if type(expected["acceptedInModel"]) is not bool or type(expected["details"]) is not dict:
            raise ContractError("MODEL_EXPECTED_VALUE_TYPES")
        outcomes.append({"kind": KIND, "authorizesPhysicalEffects": False, "id": case["id"], "matchesLiteralExpected": same_json(actual, expected), "actual": actual, "expected": expected})
    matches = sum(item["matchesLiteralExpected"] for item in outcomes)
    receipt = {
        "kind": KIND,
        "authorizesPhysicalEffects": False,
        "schema": "C78_RETENTION_PURE_DESIGN_RESULT_V1",
        "vectorSHA256": hashlib.sha256(data).hexdigest(),
        "creditSemantics": "ABSTRACT_MODEL_CREDIT_NOT_SQL_OR_HTTP",
        "cases": len(outcomes),
        "matched": matches,
        "mismatched": len(outcomes) - matches,
        "outcomes": outcomes,
    }
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(receipt, stream, ensure_ascii=False, indent=2, allow_nan=False)
        stream.write("\n")
    print(json.dumps({"kind": KIND, "authorizesPhysicalEffects": False, "cases": len(outcomes), "matched": matches, "mismatched": len(outcomes) - matches}))
    return 0 if matches == len(outcomes) else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ContractError, OSError, UnicodeError, json.JSONDecodeError):
        print(json.dumps(result(False, "MODEL_RUN_INPUT_OR_OUTPUT_REFUSED")))
        sys.exit(2)
