"""
predictor.py

Model loading and inference logic for the MyTadika AI Health Microservice.

Responsibilities
────────────────
1. Load best_model.joblib, scaler.joblib, selected_features.joblib,
   severe_threshold.joblib from AI/models/ at startup (singleton pattern).
2. Run pre-processing identical to the training pipeline (Task 4–5 notebooks).
3. Apply the custom severe-class probability threshold discovered during Task 5
   (threshold stored in severe_threshold.joblib).
4. Return a PredictionResponse-compatible dict to main.py.

Label encoding (matches Task 2.3 in plan.md and the training notebook):
  0 = normal   →  ML output label 'normal'
  1 = moderate  →  ML output label 'moderate'
  2 = severe    →  ML output label 'severe'

Clinical flags (SAM, stunting, wasting) are derived from raw measurements
using WHO threshold rules so they are always available regardless of model output.
"""

from __future__ import annotations

import os
import math
import logging
from pathlib import Path
from functools import lru_cache
from typing import Any

import joblib
import numpy as np
import pandas as pd

logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────
#  Paths  (relative to this file → AI/models/)
# ─────────────────────────────────────────────
_HERE        = Path(__file__).parent          # AI/api/
_MODELS_DIR  = _HERE.parent / "models"        # AI/models/

MODEL_PATH     = _MODELS_DIR / "best_model.joblib"
SCALER_PATH    = _MODELS_DIR / "scaler.joblib"
FEATURES_PATH  = _MODELS_DIR / "selected_features.joblib"
THRESHOLD_PATH = _MODELS_DIR / "severe_threshold.joblib"
NAME_PATH      = _MODELS_DIR / "best_model_name.txt"

# ─────────────────────────────────────────────
#  Label maps
# ─────────────────────────────────────────────
INT_TO_STATUS = {0: "normal", 1: "moderate", 2: "severe"}
STATUS_TO_INT = {v: k for k, v in INT_TO_STATUS.items()}

# ─────────────────────────────────────────────
#  Singleton: loaded model artefacts
# ─────────────────────────────────────────────

class _ModelRegistry:
    """Loads all artefacts exactly once and exposes them as attributes."""

    def __init__(self) -> None:
        logger.info("Loading model artefacts from %s …", _MODELS_DIR)

        if not MODEL_PATH.exists():
            raise FileNotFoundError(
                f"best_model.joblib not found at {MODEL_PATH}. "
                "Run the training notebook (Task 4–6) first."
            )

        self.model             = joblib.load(MODEL_PATH)
        self.scaler            = joblib.load(SCALER_PATH) if SCALER_PATH.exists() else None
        self.selected_features: list[str] = (
            list(joblib.load(FEATURES_PATH)) if FEATURES_PATH.exists() else []
        )
        self.severe_threshold: float = (
            float(joblib.load(THRESHOLD_PATH)) if THRESHOLD_PATH.exists() else 0.05
        )
        self.model_version: str = (
            NAME_PATH.read_text(encoding="utf-8").strip()
            if NAME_PATH.exists()
            else "XGBoost"
        )

        logger.info(
            "Model loaded: %s | features: %d | severe_threshold: %.3f",
            self.model_version,
            len(self.selected_features),
            self.severe_threshold,
        )


@lru_cache(maxsize=1)
def get_registry() -> _ModelRegistry:
    return _ModelRegistry()


# ─────────────────────────────────────────────
#  Feature Engineering  (mirrors Task 4 notebook)
# ─────────────────────────────────────────────

def _compute_bmi(weight_kg: float, height_cm: float) -> float:
    height_m = height_cm / 100.0
    return weight_kg / (height_m ** 2)


def _simple_z_score(value: float, mean: float, sd: float) -> float:
    """Simplified z-score helper (no LMS table lookup — uses population proxies)."""
    if sd == 0:
        return 0.0
    return (value - mean) / sd


def _age_group(age_months: float) -> int:
    """0 = infant (0-12m), 1 = toddler (12-36m), 2 = preschool (36-72m)"""
    if age_months < 12:
        return 0
    elif age_months < 36:
        return 1
    return 2


def _build_feature_row(
    age_months: float,
    weight_kg: float,
    height_cm: float,
    muac_cm: float | None,
    bmi: float | None,
    gender: str | None,
) -> dict[str, float]:
    """
    Construct feature dict matching the training pipeline.
    Uses the same column names as selected_features.joblib.
    """
    bmi_verified = _compute_bmi(weight_kg, height_cm)

    # Simplified z-score proxies (WHO LMS table lookup would be more accurate;
    # these proxies are sufficient given the training data used the same approach)
    weight_for_age_z   = _simple_z_score(weight_kg,  mean=14.0, sd=3.5)
    height_for_age_z   = _simple_z_score(height_cm,  mean=95.0, sd=12.0)
    bmi_for_age_z      = _simple_z_score(bmi_verified, mean=15.5, sd=2.0)
    muac_for_age_z     = _simple_z_score(muac_cm or 15.0, mean=15.0, sd=1.5)

    return {
        "age_months":        age_months,
        "weight_kg":         weight_kg,
        "height_cm":         height_cm,
        "muac_cm":           muac_cm if muac_cm is not None else 15.0,
        "bmi":               bmi if bmi is not None else bmi_verified,
        "bmi_verified":      bmi_verified,
        "weight_for_age_z":  weight_for_age_z,
        "height_for_age_z":  height_for_age_z,
        "bmi_for_age_z":     bmi_for_age_z,
        "muac_for_age_z":    muac_for_age_z,
        "age_group":         _age_group(age_months),
        "weight_height_ratio": weight_kg / height_cm,
        "is_stunted":        int(height_for_age_z < -2),
        "is_wasted":         int(weight_for_age_z < -2),
    }


# ─────────────────────────────────────────────
#  Clinical flags  
# ─────────────────────────────────────────────

def _compute_flags(
    muac_cm: float | None,
    height_for_age_z: float,
    weight_for_age_z: float,
) -> dict[str, bool]:
    """
    WHO-based clinical flags.
    SAM: MUAC < 11.5 cm  OR  weight-for-height z < -3
    """
    is_sam     = (muac_cm is not None and muac_cm < 11.5) or weight_for_age_z < -3
    is_stunted = height_for_age_z < -2
    is_wasted  = weight_for_age_z < -2
    return {"is_sam": is_sam, "is_stunted": is_stunted, "is_wasted": is_wasted}


# ─────────────────────────────────────────────
#  Main Inference Function
# ─────────────────────────────────────────────

def predict(
    child_id:   str,
    age_months: float,
    weight_kg:  float,
    height_cm:  float,
    muac_cm:    float | None = None,
    bmi:        float | None = None,
    gender:     str | None   = None,
) -> dict[str, Any]:
    """
    Run ML inference and return a dict matching PredictionResponse schema.

    Steps
    ─────
    1. Build feature dict → DataFrame
    2. Select features matching training pipeline
    3. Scale if scaler was saved (tree models don't need it, but kept for generality)
    4. Predict class probabilities
    5. Apply custom severe threshold (lower than default 0.5 to maximise recall)
    6. Compute clinical flags
    7. Return structured response dict
    """
    reg = get_registry()

    # ── 1. Feature engineering ───────────────────────────────────────────
    feat_dict = _build_feature_row(age_months, weight_kg, height_cm, muac_cm, bmi, gender)
    df = pd.DataFrame([feat_dict])

    # ── 2. Select features ───────────────────────────────────────────────
    if reg.selected_features:
        available = [f for f in reg.selected_features if f in df.columns]
        df = df[available]

    # ── 3. Scale (only if scaler present — not needed for XGBoost) ────────
    X = df.values
    if reg.scaler is not None:
        X = reg.scaler.transform(X)

    # ── 4. Predict probabilities ─────────────────────────────────────────
    proba = reg.model.predict_proba(X)[0]   # shape: (n_classes,)

    # Map to named dict using class ordering from the model
    classes = list(reg.model.classes_)       # e.g. [0, 1, 2]
    prob_by_encoded = {int(c): float(p) for c, p in zip(classes, proba)}

    prob_normal   = prob_by_encoded.get(0, 0.0)
    prob_moderate = prob_by_encoded.get(1, 0.0)
    prob_severe   = prob_by_encoded.get(2, 0.0)

    # ── 5. Apply custom severe threshold (plan §6.2 cost-sensitive) ───────
    # Default argmax would under-predict severe. We override:
    if prob_severe >= reg.severe_threshold:
        predicted_encoded = 2   # severe
    elif prob_moderate >= 0.40:
        predicted_encoded = 1   # moderate
    else:
        predicted_encoded = 0   # normal

    predicted_status = INT_TO_STATUS[predicted_encoded]
    confidence       = float(prob_by_encoded.get(predicted_encoded, 0.0))

    # ── 6. Clinical flags ─────────────────────────────────────────────────
    feat_full = _build_feature_row(age_months, weight_kg, height_cm, muac_cm, bmi, gender)
    flags = _compute_flags(
        muac_cm,
        feat_full["height_for_age_z"],
        feat_full["weight_for_age_z"],
    )

    # ── 7. Compose response ───────────────────────────────────────────────
    return {
        "child_id":   child_id,
        "status":     predicted_status,
        "encoded":    predicted_encoded,
        "confidence": confidence,
        "probabilities": {
            "normal":   round(prob_normal,   4),
            "moderate": round(prob_moderate, 4),
            "severe":   round(prob_severe,   4),
        },
        "flags": flags,
        "model_version": reg.model_version,
    }
