"""
predictor.py

Model loading and inference logic for the MyTadika AI Academic
Interest/Strength-Area Microservice. Mirrors the loading pattern used by
AI/api/predictor.py (the malnutrition service): a lazily-initialized,
cached singleton registry loaded once at startup.
"""

from __future__ import annotations

import logging
from pathlib import Path
from functools import lru_cache
from typing import Any

import joblib
import pandas as pd

from schemas import DOMAINS, TRENDS, DOMAIN_DISPLAY_NAMES
from train_model import NUMERIC_FEATURES, build_features

logger = logging.getLogger(__name__)

# ─────────────────────────────────────────────
#  Paths  (relative to this file → AI/academic/models/)
# ─────────────────────────────────────────────
_HERE = Path(__file__).parent
_MODELS_DIR = _HERE.parent / "models"

MODEL_PATH = _MODELS_DIR / "model.joblib"
FEATURES_PATH = _MODELS_DIR / "feature_columns.joblib"
VERSION_PATH = _MODELS_DIR / "model_version.txt"


class _ModelRegistry:
    """Loads the model + expected feature-column order exactly once."""

    def __init__(self) -> None:
        logger.info("Loading academic model artefacts from %s …", _MODELS_DIR)

        if not MODEL_PATH.exists():
            raise FileNotFoundError(
                f"model.joblib not found at {MODEL_PATH}. "
                "Run generate_dataset.py then train_model.py first."
            )

        self.model = joblib.load(MODEL_PATH)
        self.feature_columns: list[str] = (
            list(joblib.load(FEATURES_PATH)) if FEATURES_PATH.exists() else []
        )
        self.model_version: str = (
            VERSION_PATH.read_text(encoding="utf-8").strip()
            if VERSION_PATH.exists()
            else "unknown"
        )

        logger.info(
            "Model loaded: %s | features: %d",
            self.model_version,
            len(self.feature_columns),
        )


@lru_cache(maxsize=1)
def get_registry() -> _ModelRegistry:
    return _ModelRegistry()


def predict_domains(student_id: str, domain_rows: list[dict[str, Any]]) -> dict[str, Any]:
    """
    domain_rows: list of dicts matching schemas.DomainFeatures fields
    (domain, domain_avg_score, domain_trend, domain_completion_rate,
    domain_overdue_count, ipsative_zscore).

    Returns a dict matching schemas.PredictionResponse.
    """
    reg = get_registry()

    df = pd.DataFrame(domain_rows)
    X = build_features(df)

    # Reindex to the exact training-time column order; any domain/trend value
    # not present in this request's rows becomes an all-zero one-hot column,
    # which is exactly what we want (e.g. a request with only 2 domains still
    # encodes cleanly, it just won't have those other domains' indicator set).
    X = X.reindex(columns=reg.feature_columns, fill_value=0)

    proba = reg.model.predict_proba(X)
    classes = list(reg.model.classes_)

    results = []
    for i, row in enumerate(domain_rows):
        row_proba = {cls: float(p) for cls, p in zip(classes, proba[i])}
        best_level = max(row_proba, key=row_proba.get)
        results.append({
            "domain": row["domain"],
            "domain_display_name": DOMAIN_DISPLAY_NAMES.get(row["domain"], row["domain"]),
            "level": best_level,
            "confidence": round(row_proba[best_level], 4),
        })

    return {
        "student_id": student_id,
        "domains": results,
        "model_version": reg.model_version,
    }
