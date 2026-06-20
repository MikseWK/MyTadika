"""
test_fastapi.py
───────────────────────────────────────────────────────────────
Standalone test suite for the MyTadika FastAPI AI microservice.

Run with:
    python test_fastapi.py

Requires:
    pip install requests
    FastAPI server must be running on http://localhost:8001

Tests covered:
  T1  Health-check endpoint
  T2  Predict — normal child
  T3  Predict — moderate malnutrition
  T4  Predict — severe malnutrition + SAM flag
  T5  Predict — gender field handling
  T6  Predict — without optional fields (muac_cm, bmi, gender)
  T7  Predict — boundary values (min age, min weight)
  T8  Validation — out-of-range age rejected
  T9  Validation — out-of-range weight rejected
  T10 Validation — missing required field rejected
"""

import sys
import json
import traceback

try:
    import requests
except ImportError:
    print("ERROR: 'requests' library not found.  Run:  pip install requests")
    sys.exit(1)

BASE_URL = "http://localhost:8001"
PREDICT  = f"{BASE_URL}/api/predict"
HEALTH   = f"{BASE_URL}/health"

PASS = "\033[92m  [PASS]\033[0m"
FAIL = "\033[91m  [FAIL]\033[0m"
INFO = "\033[94m  [INFO]\033[0m"

passed = 0
failed = 0


def run(label: str, fn):
    global passed, failed
    print(f"\n{'─'*60}")
    print(f"  {label}")
    print(f"{'─'*60}")
    try:
        fn()
        print(f"{PASS}  {label}")
        passed += 1
    except AssertionError as e:
        print(f"{FAIL}  Assertion failed: {e}")
        failed += 1
    except Exception as e:
        print(f"{FAIL}  Unexpected exception: {e}")
        traceback.print_exc()
        failed += 1


def pprint(d: dict):
    print(json.dumps(d, indent=2, default=str))


# ─────────────────────────────────────────────────────────────────
#  T1 — Health Check
# ─────────────────────────────────────────────────────────────────
def t1_health_check():
    r = requests.get(HEALTH, timeout=5)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    body = r.json()
    assert body["status"] == "healthy",      f"Expected 'healthy', got {body['status']!r}"
    assert body["model_loaded"] is True,      "model_loaded should be True"
    assert isinstance(body["features"], list), "features should be a list"
    assert len(body["features"]) > 0,          "features list should not be empty"
    print(f"{INFO}  Model version  : {body['model_version']}")
    print(f"{INFO}  Features loaded: {body['features']}")


# ─────────────────────────────────────────────────────────────────
#  T2 — Normal Child
# ─────────────────────────────────────────────────────────────────
def t2_normal_child():
    payload = {
        "child_id": "test-child-01",
        "age_months": 36.0,
        "weight_kg": 14.0,
        "height_cm": 96.0,
        "muac_cm": 16.0,
        "gender": "male"
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    body = r.json()
    assert body["status"] in ("normal", "moderate", "severe"), f"Unexpected status: {body['status']}"
    assert 0.0 <= body["confidence"] <= 1.0, "confidence out of range"
    assert "probabilities" in body
    assert "flags" in body
    print(f"{INFO}  Status     : {body['status']}")
    print(f"{INFO}  Confidence : {body['confidence']:.2%}")
    print(f"{INFO}  SAM flag   : {body['flags']['is_sam']}")


# ─────────────────────────────────────────────────────────────────
#  T3 — Moderate Malnutrition
# ─────────────────────────────────────────────────────────────────
def t3_moderate_child():
    payload = {
        "child_id": "test-child-02",
        "age_months": 24.0,
        "weight_kg": 9.0,
        "height_cm": 82.0,
        "muac_cm": 13.5,
        "gender": "female"
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    body = r.json()
    assert body["status"] in ("moderate", "severe", "normal"), f"Unexpected status: {body['status']}"
    print(f"{INFO}  Status     : {body['status']}")
    print(f"{INFO}  Confidence : {body['confidence']:.2%}")
    print(f"{INFO}  is_wasted  : {body['flags']['is_wasted']}")
    print(f"{INFO}  is_stunted : {body['flags']['is_stunted']}")


# ─────────────────────────────────────────────────────────────────
#  T4 — Severe Malnutrition + SAM
# ─────────────────────────────────────────────────────────────────
def t4_severe_child_sam():
    payload = {
        "child_id": "test-child-03",
        "age_months": 18.0,
        "weight_kg": 6.5,
        "height_cm": 75.0,
        "muac_cm": 10.8,   # <11.5 cm → SAM
        "gender": "male"
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    body = r.json()
    # MUAC <11.5 MUST trigger is_sam regardless of model output
    assert body["flags"]["is_sam"] is True, "SAM flag should be True when muac_cm < 11.5"
    print(f"{INFO}  Status     : {body['status']}")
    print(f"{INFO}  SAM flag   : {body['flags']['is_sam']}  ← must be True")


# ─────────────────────────────────────────────────────────────────
#  T5 — Gender Normalisation
# ─────────────────────────────────────────────────────────────────
def t5_gender_normalisation():
    for gender_input in ("Male", "FEMALE", "female", "male"):
        payload = {
            "child_id": "test-child-04",
            "age_months": 30.0,
            "weight_kg": 12.0,
            "height_cm": 90.0,
            "gender": gender_input
        }
        r = requests.post(PREDICT, json=payload, timeout=10)
        assert r.status_code == 200, \
            f"gender={gender_input!r} caused status {r.status_code}: {r.text}"
        print(f"{INFO}  gender={gender_input!r:10s} → status {r.json()['status']}")


# ─────────────────────────────────────────────────────────────────
#  T6 — Optional Fields Omitted
# ─────────────────────────────────────────────────────────────────
def t6_optional_fields_omitted():
    payload = {
        "child_id": "test-child-05",
        "age_months": 48.0,
        "weight_kg": 16.0,
        "height_cm": 103.0
        # muac_cm, bmi, gender all omitted
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200 with optional fields omitted, got {r.status_code}"
    body = r.json()
    assert "status" in body
    assert "probabilities" in body
    print(f"{INFO}  Response OK without optional fields. Status: {body['status']}")


# ─────────────────────────────────────────────────────────────────
#  T7 — Boundary Values (min valid age)
# ─────────────────────────────────────────────────────────────────
def t7_boundary_values():
    payload = {
        "child_id": "test-child-06",
        "age_months": 0.0,    # minimum
        "weight_kg": 3.0,     # newborn weight
        "height_cm": 50.0,    # newborn height
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200 at min boundary, got {r.status_code}: {r.text}"
    print(f"{INFO}  Boundary (age=0) Status: {r.json()['status']}")


# ─────────────────────────────────────────────────────────────────
#  T8 — Validation: Age Out of Range
# ─────────────────────────────────────────────────────────────────
def t8_validation_age_out_of_range():
    payload = {
        "child_id": "test-child-07",
        "age_months": 100.0,  # > 72 — should be rejected
        "weight_kg": 20.0,
        "height_cm": 110.0
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    print(f"{INFO}  HTTP {r.status_code}: {r.text[:200]}")
    assert r.status_code == 422, \
        f"Expected 422 Unprocessable Entity for age_months=100, got {r.status_code}"
    print(f"{INFO}  Correctly rejected age_months=100 with 422")


# ─────────────────────────────────────────────────────────────────
#  T9 — Validation: Weight Out of Range
# ─────────────────────────────────────────────────────────────────
def t9_validation_weight_out_of_range():
    payload = {
        "child_id": "test-child-08",
        "age_months": 36.0,
        "weight_kg": 1.0,     # < 2.5 — should be rejected
        "height_cm": 90.0
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    print(f"{INFO}  HTTP {r.status_code}: {r.text[:200]}")
    assert r.status_code == 422, \
        f"Expected 422 Unprocessable Entity for weight_kg=1.0, got {r.status_code}"
    print(f"{INFO}  Correctly rejected weight_kg=1.0 with 422")


# ─────────────────────────────────────────────────────────────────
#  T10 — Validation: Missing Required Field
# ─────────────────────────────────────────────────────────────────
def t10_missing_required_field():
    payload = {
        "child_id": "test-child-09",
        # age_months missing!
        "weight_kg": 14.0,
        "height_cm": 96.0
    }
    r = requests.post(PREDICT, json=payload, timeout=10)
    print(f"{INFO}  HTTP {r.status_code}: {r.text[:200]}")
    assert r.status_code == 422, \
        f"Expected 422 Unprocessable Entity for missing age_months, got {r.status_code}"
    print(f"{INFO}  Correctly rejected missing age_months with 422")


# ─────────────────────────────────────────────────────────────────
#  Run all tests
# ─────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("\n" + "═"*60)
    print("  MyTadika — FastAPI AI Microservice Test Suite")
    print(f"  Target: {BASE_URL}")
    print("═"*60)

    run("T1  Health check endpoint",          t1_health_check)
    run("T2  Predict — normal child",         t2_normal_child)
    run("T3  Predict — moderate malnutrition",t3_moderate_child)
    run("T4  Predict — severe + SAM flag",    t4_severe_child_sam)
    run("T5  Gender normalisation",           t5_gender_normalisation)
    run("T6  Optional fields omitted",        t6_optional_fields_omitted)
    run("T7  Boundary values (age=0)",        t7_boundary_values)
    run("T8  Validation — age out of range",  t8_validation_age_out_of_range)
    run("T9  Validation — weight too low",    t9_validation_weight_out_of_range)
    run("T10 Validation — missing field",     t10_missing_required_field)

    print("\n" + "═"*60)
    total = passed + failed
    colour = "\033[92m" if failed == 0 else "\033[91m"
    print(f"  {colour}Results: {passed}/{total} passed  |  {failed} failed\033[0m")
    print("═"*60 + "\n")

    sys.exit(0 if failed == 0 else 1)
