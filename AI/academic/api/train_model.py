"""
train_model.py

Trains a single RandomForestClassifier on the synthetic domain-row dataset
(domain is a categorical feature, not 4 separate per-domain models — this way
the model can learn patterns that generalize across domains, e.g. "declining
trend + low completion" looks similar whether it's happening in STEM or Arts).

Saves to ../models/:
  model.joblib            - the trained classifier
  feature_columns.joblib  - exact column order/names the model expects (needed
                             so predictor.py can reproduce identical encoding
                             at inference time)
  model_version.txt        - version string for the API's /health + response
"""

from __future__ import annotations

from pathlib import Path
from datetime import datetime, timezone

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score

from schemas import DOMAINS, TRENDS

_HERE = Path(__file__).parent
_DATASET_PATH = _HERE.parent / "dataset" / "synthetic_academic_data.csv"
_MODELS_DIR = _HERE.parent / "models"
_MODELS_DIR.mkdir(parents=True, exist_ok=True)

MODEL_PATH = _MODELS_DIR / "model.joblib"
FEATURES_PATH = _MODELS_DIR / "feature_columns.joblib"
VERSION_PATH = _MODELS_DIR / "model_version.txt"

NUMERIC_FEATURES = [
    "domain_avg_score",
    "domain_completion_rate",
    "domain_overdue_count",
    "ipsative_zscore",
]


def build_features(df: pd.DataFrame) -> pd.DataFrame:
    """One-hot encode domain + trend, keep numeric features as-is. Uses fixed
    category lists (not pd.get_dummies' auto-discovery) so the column set is
    identical between training and single-row inference."""
    X = df[NUMERIC_FEATURES].copy()
    for d in DOMAINS:
        X[f"domain_{d}"] = (df["domain"] == d).astype(int)
    for t in TRENDS:
        X[f"trend_{t}"] = (df["domain_trend"] == t).astype(int)
    return X


def main() -> None:
    if not _DATASET_PATH.exists():
        raise FileNotFoundError(
            f"{_DATASET_PATH} not found. Run generate_dataset.py first."
        )
    df = pd.read_csv(_DATASET_PATH)

    X = build_features(df)
    y = df["label"]

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    # min_samples_leaf/max_depth deliberately left low: labels here are 100%
    # deterministic from features (bootstrap rule, not real noisy outcomes), so
    # there is nothing to "regularize against" yet. Heavier regularization was
    # found to blind the model to rare-but-real minority combinations (e.g.
    # "improving" trend + completion_rate < 0.5, ~0.4% of rows) by letting the
    # much larger "improving -> usually fine" signal dominate those few leaves.
    # Revisit this once the model is retrained on real (noisy) outcome data.
    clf = RandomForestClassifier(
        n_estimators=300,
        max_depth=None,
        min_samples_leaf=1,
        class_weight="balanced",
        random_state=42,
        n_jobs=-1,
    )
    clf.fit(X_train, y_train)

    y_pred = clf.predict(X_test)
    print(f"Accuracy: {accuracy_score(y_test, y_pred):.4f}")
    print("\nClassification report:")
    print(classification_report(y_test, y_pred))

    print("Feature importances:")
    for name, importance in sorted(zip(X.columns, clf.feature_importances_), key=lambda x: -x[1]):
        print(f"  {name:30s} {importance:.4f}")

    joblib.dump(clf, MODEL_PATH)
    joblib.dump(list(X.columns), FEATURES_PATH)
    version = f"academic-rf-{datetime.now(timezone.utc).strftime('%Y%m%d')}"
    VERSION_PATH.write_text(version, encoding="utf-8")

    print(f"\nSaved model to {MODEL_PATH}")
    print(f"Saved feature columns to {FEATURES_PATH}")
    print(f"Model version: {version}")


if __name__ == "__main__":
    main()
