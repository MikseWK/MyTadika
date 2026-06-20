"""
test_springboot.py
───────────────────────────────────────────────────────────────
End-to-end test suite for the MyTadika Spring Boot backend
(HealthController endpoints on port 8080).

Run with:
    python test_springboot.py

Requires:
    pip install requests
    Spring Boot must be running on http://localhost:8080
    (FastAPI does NOT need to be running — the fallback kicks in)

Tests covered:
  T1  POST /api/health/predict          — advice without saving
  T2  POST /api/health/predict          — with allergies (milk, peanut)
  T3  POST /api/health/predict          — severe case, urgent referral
  T4  POST /api/health/predict          — confidence caveat (<70%)
  T5  POST /api/health/record           — save a health record
  T6  GET  /api/health/history/{id}     — retrieve history after save
  T7  GET  /api/health/advice/{id}      — latest advice from stored record
  T8  PUT  /api/health/allergies/{id}   — update allergy profile
  T9  Validation — missing childId
  T10 Validation — invalid activityLevel
"""

import sys
import json
import traceback

try:
    import requests
except ImportError:
    print("ERROR: 'requests' library not found.  Run:  pip install requests")
    sys.exit(1)

BASE_URL = "http://localhost:8080"
API      = f"{BASE_URL}/api/health"

PASS = "\033[92m  [PASS]\033[0m"
FAIL = "\033[91m  [FAIL]\033[0m"
INFO = "\033[94m  [INFO]\033[0m"

passed = 0
failed = 0
SAVED_RECORD_STUDENT_ID = "1001"  # used across tests


def run(label: str, fn):
    global passed, failed
    print(f"\n{'─'*62}")
    print(f"  {label}")
    print(f"{'─'*62}")
    try:
        fn()
        print(f"{PASS}  {label}")
        passed += 1
    except AssertionError as e:
        print(f"{FAIL}  Assertion failed: {e}")
        failed += 1
    except requests.exceptions.ConnectionError:
        print(f"{FAIL}  Connection refused — is Spring Boot running on {BASE_URL}?")
        failed += 1
    except Exception as e:
        print(f"{FAIL}  Unexpected exception: {e}")
        traceback.print_exc()
        failed += 1


def pprint(d):
    print(json.dumps(d, indent=2, default=str))


def _predict_payload(child_id="test-001", age_months=36.0,
                     weight_kg=14.0, height_cm=96.0,
                     muac_cm=None, gender=None,
                     activity_level=1, allergies=None,
                     shown_advice_ids=None):
    p = {
        "childId": child_id,
        "ageMonths": age_months,
        "weightKg": weight_kg,
        "heightCm": height_cm,
        "activityLevel": activity_level,
    }
    if muac_cm is not None:      p["muacCm"] = muac_cm
    if gender is not None:       p["gender"] = gender
    if allergies is not None:    p["allergies"] = allergies
    if shown_advice_ids is not None: p["shownAdviceIds"] = shown_advice_ids
    return p


# ─────────────────────────────────────────────────────────────────
#  T1 — Basic predict (no save)
# ─────────────────────────────────────────────────────────────────
def t1_predict_normal():
    payload = _predict_payload(
        child_id="test-001", age_months=36.0,
        weight_kg=14.0, height_cm=96.0, gender="male"
    )
    r = requests.post(f"{API}/predict", json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text[:300]}"
    body = r.json()
    assert "status" in body,           "Response missing 'status'"
    assert "dietaryAdvice" in body,    "Response missing 'dietaryAdvice'"
    assert "activityAdvice" in body,   "Response missing 'activityAdvice'"
    assert "disclaimer" in body,       "Response missing 'disclaimer'"
    assert isinstance(body["dietaryAdvice"], list)
    assert len(body["dietaryAdvice"]) > 0, "dietaryAdvice list should not be empty"
    print(f"{INFO}  Status        : {body['status']}")
    print(f"{INFO}  Dietary cards : {len(body['dietaryAdvice'])}")
    print(f"{INFO}  Activity cards: {len(body['activityAdvice'])}")


# ─────────────────────────────────────────────────────────────────
#  T2 — Predict with allergies
# ─────────────────────────────────────────────────────────────────
def t2_predict_with_allergies():
    payload = _predict_payload(
        child_id="test-002", age_months=30.0,
        weight_kg=11.0, height_cm=88.0,
        allergies=["milk", "peanut"]
    )
    r = requests.post(f"{API}/predict", json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text[:300]}"
    body = r.json()
    # Allergy warnings should be returned
    assert "allergyWarnings" in body, "Response missing 'allergyWarnings'"
    warnings = body["allergyWarnings"]
    assert len(warnings) > 0, \
        "Expected at least one allergyWarning for milk/peanut allergies"
    allergens = [w["allergen"] for w in warnings]
    print(f"{INFO}  Allergy warnings returned: {allergens}")
    # Guardrail log should show blocked cards
    log = body.get("guardrailLog", [])
    print(f"{INFO}  Guardrail blocks          : {len(log)}")


# ─────────────────────────────────────────────────────────────────
#  T3 — Predict severe child → urgent referral
# ─────────────────────────────────────────────────────────────────
def t3_predict_severe_urgent_referral():
    # Very low weight for age → severe classification via fallback
    payload = _predict_payload(
        child_id="test-003", age_months=18.0,
        weight_kg=5.5, height_cm=72.0,   # BMI ≈ 10.6 → severe fallback
        muac_cm=10.5, activity_level=0
    )
    r = requests.post(f"{API}/predict", json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text[:300]}"
    body = r.json()
    # When status is severe, requiresUrgentReferral must be True
    if body["status"] == "severe":
        assert body["requiresUrgentReferral"] is True, \
            "requiresUrgentReferral must be True for severe status"
    print(f"{INFO}  Status               : {body['status']}")
    print(f"{INFO}  requiresUrgentReferral: {body['requiresUrgentReferral']}")


# ─────────────────────────────────────────────────────────────────
#  T4 — Confidence caveat with shown advice rotation
# ─────────────────────────────────────────────────────────────────
def t4_advice_rotation():
    payload = _predict_payload(
        child_id="test-004", age_months=48.0,
        weight_kg=16.0, height_cm=103.0,
        shown_advice_ids=["N-01", "N-02"]  # simulate previously shown cards
    )
    r = requests.post(f"{API}/predict", json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text[:300]}"
    body = r.json()
    assert "dietaryAdvice" in body
    print(f"{INFO}  Dietary cards (with rotation): {len(body['dietaryAdvice'])}")
    ids = [c.get("id", "?") for c in body["dietaryAdvice"]]
    print(f"{INFO}  Returned card IDs: {ids}")


# ─────────────────────────────────────────────────────────────────
#  T5 — Record measurement (POST /record)
# ─────────────────────────────────────────────────────────────────
def t5_record_measurement():
    payload = _predict_payload(
        child_id=SAVED_RECORD_STUDENT_ID,
        age_months=36.0, weight_kg=13.5,
        height_cm=94.0, gender="female",
        allergies=["milk"], activity_level=1
    )
    payload["recordedBy"] = 999   # teacher/admin ID
    r = requests.post(f"{API}/record", json=payload, timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text[:300]}"
    body = r.json()
    assert "recordId" in body,     "Response missing 'recordId'"
    assert "healthRecord" in body, "Response missing 'healthRecord'"
    assert "advice" in body,       "Response missing 'advice'"
    assert body["recordId"] is not None
    print(f"{INFO}  Saved record ID: {body['recordId']}")
    print(f"{INFO}  BMI stored     : {body['healthRecord'].get('bmi', '?'):.2f}"
          if isinstance(body['healthRecord'].get('bmi'), (int, float)) else
          f"{INFO}  BMI stored     : {body['healthRecord'].get('bmi')}")


# ─────────────────────────────────────────────────────────────────
#  T6 — Retrieve history
# ─────────────────────────────────────────────────────────────────
def t6_get_history():
    r = requests.get(f"{API}/history/{SAVED_RECORD_STUDENT_ID}", timeout=10)
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    records = r.json()
    assert isinstance(records, list), "Expected a list of records"
    assert len(records) >= 1, \
        "Expected at least 1 record after POST /record in T5"
    print(f"{INFO}  Records found for student {SAVED_RECORD_STUDENT_ID}: {len(records)}")


# ─────────────────────────────────────────────────────────────────
#  T7 — Latest advice from stored record
# ─────────────────────────────────────────────────────────────────
def t7_get_latest_advice():
    r = requests.get(
        f"{API}/advice/{SAVED_RECORD_STUDENT_ID}",
        params={"activityLevel": 1},
        timeout=10
    )
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text[:300]}"
    body = r.json()
    assert "dietaryAdvice" in body
    assert "disclaimer" in body
    print(f"{INFO}  Advice status       : {body.get('status')}")
    print(f"{INFO}  Dietary cards count : {len(body['dietaryAdvice'])}")


# ─────────────────────────────────────────────────────────────────
#  T8 — Update allergy profile
# ─────────────────────────────────────────────────────────────────
def t8_update_allergies():
    new_allergies = {"allergies": ["peanut", "milk"]}
    r = requests.put(
        f"{API}/allergies/{SAVED_RECORD_STUDENT_ID}",
        json=new_allergies, timeout=10
    )
    pprint(r.json())
    assert r.status_code == 200, f"Expected 200, got {r.status_code}: {r.text[:300]}"
    body = r.json()
    # Verify the update was stored
    stored = body.get("activeAllergies", "")
    print(f"{INFO}  Stored allergies: {stored!r}")
    assert "peanut" in stored.lower() or "milk" in stored.lower(), \
        f"Expected peanut/milk in stored allergies, got: {stored!r}"


# ─────────────────────────────────────────────────────────────────
#  T9 — Validation: missing childId
# ─────────────────────────────────────────────────────────────────
def t9_validation_missing_child_id():
    payload = {
        # childId missing
        "ageMonths": 36.0,
        "weightKg": 14.0,
        "heightCm": 96.0,
        "activityLevel": 1
    }
    r = requests.post(f"{API}/predict", json=payload, timeout=10)
    print(f"{INFO}  HTTP {r.status_code}: {r.text[:300]}")
    assert r.status_code == 400, \
        f"Expected 400 Bad Request for missing childId, got {r.status_code}"
    print(f"{INFO}  Correctly rejected missing childId with {r.status_code}")


# ─────────────────────────────────────────────────────────────────
#  T10 — Validation: invalid activityLevel
# ─────────────────────────────────────────────────────────────────
def t10_validation_invalid_activity_level():
    payload = _predict_payload(
        child_id="test-010", age_months=36.0,
        weight_kg=14.0, height_cm=96.0,
        activity_level=5  # Valid range is 0, 1, 2
    )
    r = requests.post(f"{API}/predict", json=payload, timeout=10)
    print(f"{INFO}  HTTP {r.status_code}: {r.text[:300]}")
    assert r.status_code == 400, \
        f"Expected 400 for activityLevel=5, got {r.status_code}"
    print(f"{INFO}  Correctly rejected activityLevel=5 with {r.status_code}")


# ─────────────────────────────────────────────────────────────────
#  Run all tests
# ─────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("\n" + "═"*62)
    print("  MyTadika — Spring Boot Backend End-to-End Test Suite")
    print(f"  Target: {BASE_URL}")
    print("═"*62)

    run("T1  POST /predict — normal child",          t1_predict_normal)
    run("T2  POST /predict — with allergies",         t2_predict_with_allergies)
    run("T3  POST /predict — severe + urgent referral", t3_predict_severe_urgent_referral)
    run("T4  POST /predict — advice card rotation",   t4_advice_rotation)
    run("T5  POST /record  — save measurement",       t5_record_measurement)
    run("T6  GET  /history — retrieve saved records", t6_get_history)
    run("T7  GET  /advice  — latest from DB",         t7_get_latest_advice)
    run("T8  PUT  /allergies — update profile",       t8_update_allergies)
    run("T9  Validation — missing childId",           t9_validation_missing_child_id)
    run("T10 Validation — invalid activityLevel",     t10_validation_invalid_activity_level)

    print("\n" + "═"*62)
    total = passed + failed
    colour = "\033[92m" if failed == 0 else "\033[91m"
    print(f"  {colour}Results: {passed}/{total} passed  |  {failed} failed\033[0m")
    print("═"*62 + "\n")

    sys.exit(0 if failed == 0 else 1)
