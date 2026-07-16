"""
generate_dataset.py

Synthesizes a bootstrap training set for the Interest/Strength-Area model.

IMPORTANT CAVEAT (documented, not hidden): MyTadika does not yet have enough
real historical academic_score_items / classwork_completions data to train on.
This script generates *synthetic* student profiles with realistic, correlated
distributions, and derives labels via an explicit, documented rule (see
`assign_label` below) rather than any real ground truth. This is the same
bootstrap-then-upgrade approach used for the malnutrition model before a real
dataset was available — treat this model's output as a starting heuristic,
to be retrained on real data once enough terms of real usage accumulate.

Each simulated student gets one row per domain (4 domains), for
~900 students -> ~3600 rows, written to ../dataset/synthetic_academic_data.csv
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from pathlib import Path

from schemas import DOMAINS, TRENDS

RNG_SEED = 42
N_STUDENTS = 900

_HERE = Path(__file__).parent
_DATASET_DIR = _HERE.parent / "dataset"
_DATASET_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_PATH = _DATASET_DIR / "synthetic_academic_data.csv"

# Bootstrap labeling thresholds (documented here since there is no real ground
# truth to calibrate against yet):
#   Strength : ipsative z > 0.5, completion_rate healthy, and not declining
#   Emerging : ipsative z < -0.5, OR declining trend, OR completion_rate low
#   Developing: everything else
Z_STRENGTH = 0.5
Z_EMERGING = -0.5
COMPLETION_HEALTHY = 0.75
COMPLETION_LOW = 0.5


def simulate_term_history(baseline: float, domain_offset: float, rng: np.random.Generator) -> list[float]:
    """Simulate 3 terms of scores for one domain, with a per-domain trajectory
    bias (declining / stable / improving), so domain_trend can be *derived*
    from realistic term-over-term movement rather than assigned independently."""
    trajectory = rng.choice([-1, 0, 1], p=[0.25, 0.5, 0.25])
    start = baseline + domain_offset + rng.normal(0, 4)
    history = [start]
    for _ in range(2):
        step = trajectory * rng.uniform(1, 4) + rng.normal(0, 3)
        history.append(history[-1] + step)
    return [float(np.clip(s, 0, 100)) for s in history]


def derive_trend(history: list[float]) -> str:
    diff = history[-1] - np.mean(history[:-1])
    if diff > 3:
        return "improving"
    if diff < -3:
        return "declining"
    return "stable"


def simulate_completion(domain_avg_score: float, rng: np.random.Generator) -> float:
    """Completion rate is correlated with score (kids doing well tend to also
    turn in more classwork) but with enough independent noise that a low
    scorer can still have solid completion, and vice versa."""
    p = 0.30 + 0.007 * domain_avg_score + rng.normal(0, 0.10)
    return float(np.clip(p, 0.0, 1.0))


def simulate_overdue(completion_rate: float, rng: np.random.Generator) -> int:
    lam = max(0.15, (1 - completion_rate) * 6)
    return int(np.clip(rng.poisson(lam), 0, 10))


def assign_label(z: float, trend: str, completion_rate: float) -> str:
    if z > Z_STRENGTH and completion_rate >= COMPLETION_HEALTHY and trend != "declining":
        return "Strength"
    if z < Z_EMERGING or trend == "declining" or completion_rate < COMPLETION_LOW:
        return "Emerging"
    return "Developing"


def generate() -> pd.DataFrame:
    rng = np.random.default_rng(RNG_SEED)
    rows: list[dict] = []

    for student_idx in range(N_STUDENTS):
        student_id = f"SYN-{student_idx:04d}"
        baseline = float(np.clip(rng.normal(70, 12), 35, 98))
        # How spread out this student's domains are around their own baseline —
        # some kids are "flat" (similar across domains), others "spiky" (clear
        # relative strengths/weaknesses). Sampling this per-student is what makes
        # the ipsative z-score meaningful rather than just re-deriving raw score.
        spikiness = rng.uniform(3, 18)

        domain_scores: dict[str, float] = {}
        domain_trends: dict[str, str] = {}
        domain_completions: dict[str, float] = {}
        domain_overdues: dict[str, int] = {}

        for domain in DOMAINS:
            offset = rng.normal(0, spikiness)
            history = simulate_term_history(baseline, offset, rng)
            avg_score = history[-1]
            trend = derive_trend(history)
            completion_rate = simulate_completion(avg_score, rng)
            overdue_count = simulate_overdue(completion_rate, rng)

            domain_scores[domain] = avg_score
            domain_trends[domain] = trend
            domain_completions[domain] = completion_rate
            domain_overdues[domain] = overdue_count

        scores_arr = np.array(list(domain_scores.values()))
        cross_mean = float(scores_arr.mean())
        cross_std = float(scores_arr.std())
        if cross_std < 1e-6:
            cross_std = 1e-6

        for domain in DOMAINS:
            z = (domain_scores[domain] - cross_mean) / cross_std
            label = assign_label(z, domain_trends[domain], domain_completions[domain])
            rows.append({
                "student_id": student_id,
                "domain": domain,
                "domain_avg_score": round(domain_scores[domain], 2),
                "domain_trend": domain_trends[domain],
                "domain_completion_rate": round(domain_completions[domain], 3),
                "domain_overdue_count": domain_overdues[domain],
                "ipsative_zscore": round(z, 4),
                "label": label,
            })

    return pd.DataFrame(rows)


# ─────────────────────────────────────────────
#  Stratified supplement
# ─────────────────────────────────────────────
# The main generator above samples z / trend / completion through a single
# correlated process (score drives both trend and completion), which is
# realistic but means some rule-relevant corners are naturally very rare —
# e.g. "improving" trend correlates with higher scores, which correlates
# with higher completion, so "improving + completion < 0.5" ends up ~0.4%
# of rows. A model trained only on that data under-learns the rule in that
# corner (tested: ~85-88% rule agreement instead of the ~100% the rule
# itself guarantees). This supplement grid-samples every (trend x z-bucket x
# completion-bucket) combination directly at the rule's own thresholds, so
# every combination the rule actually branches on gets meaningful, roughly
# equal training exposure. It deliberately does NOT preserve the "natural"
# correlation — there's no real dataset yet to be faithful to, and the
# actual goal at this bootstrap stage is for the model to learn the
# documented rule accurately everywhere, not just in the common cases.
N_PER_STRATUM = 40
Z_BUCKETS = [(-2.5, -0.5), (-0.5, 0.5), (0.5, 2.5)]
COMPLETION_BUCKETS = [(0.0, 0.5), (0.5, 0.75), (0.75, 1.0)]


def generate_stratified_supplement(rng: np.random.Generator) -> pd.DataFrame:
    rows: list[dict] = []
    idx = 0
    for trend in TRENDS:
        for z_lo, z_hi in Z_BUCKETS:
            for c_lo, c_hi in COMPLETION_BUCKETS:
                for _ in range(N_PER_STRATUM):
                    z = float(rng.uniform(z_lo, z_hi))
                    completion = float(rng.uniform(c_lo, c_hi))
                    overdue = int(np.clip(rng.poisson(max(0.15, (1 - completion) * 6)), 0, 10))
                    avg_score = float(rng.uniform(30, 100))
                    domain = rng.choice(DOMAINS)
                    label = assign_label(z, trend, completion)
                    rows.append({
                        "student_id": f"STRAT-{idx:05d}",
                        "domain": domain,
                        "domain_avg_score": round(avg_score, 2),
                        "domain_trend": trend,
                        "domain_completion_rate": round(completion, 3),
                        "domain_overdue_count": overdue,
                        "ipsative_zscore": round(z, 4),
                        "label": label,
                    })
                    idx += 1
    return pd.DataFrame(rows)


if __name__ == "__main__":
    rng = np.random.default_rng(RNG_SEED + 1)
    df = generate()
    supplement = generate_stratified_supplement(rng)
    df = pd.concat([df, supplement], ignore_index=True)
    df.to_csv(OUTPUT_PATH, index=False)
    print(f"Wrote {len(df)} rows ({df['student_id'].nunique()} students x {len(DOMAINS)} domains) to {OUTPUT_PATH}")
    print("\nLabel distribution:")
    print(df["label"].value_counts())
    print("\nLabel distribution by domain:")
    print(df.groupby("domain")["label"].value_counts())
