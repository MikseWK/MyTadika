"""
schemas.py

Pydantic v2 request / response schemas for the MyTadika AI Academic Interest/
Strength-Area Microservice.

Domain taxonomy (mirrors the 6-subject taxonomy already used by
AcademicScoreItem.subjectName / Assignment.topic on the Spring Boot side —
see SUBJECT_TO_DOMAIN below for the exact grouping Phase 4 must replicate
in Java when building the feature payload):

  language_literacy    <- Bahasa Melayu, English
  stem_logic           <- Math, Science
  creative_arts        <- Creative Arts
  physical_movement    <- Physical Education
"""

from __future__ import annotations

from typing import Optional
from pydantic import BaseModel, Field, field_validator

# ─────────────────────────────────────────────
#  Domain taxonomy (shared reference for Phase 4)
# ─────────────────────────────────────────────

DOMAINS: list[str] = ["language_literacy", "stem_logic", "creative_arts", "physical_movement"]

DOMAIN_DISPLAY_NAMES: dict[str, str] = {
    "language_literacy": "Language & Literacy",
    "stem_logic": "STEM & Logic",
    "creative_arts": "Creative Arts",
    "physical_movement": "Physical & Movement",
}

# Exact subjectName / Assignment.topic strings (from AcademicController.SUBJECTS)
# grouped into the 4 domains above. "General" (an Assignment.topic value with no
# academic-score equivalent) intentionally has no domain and should be excluded
# by Phase 4 when aggregating classwork completion/overdue counts.
SUBJECT_TO_DOMAIN: dict[str, str] = {
    "Bahasa Melayu": "language_literacy",
    "English": "language_literacy",
    "Math": "stem_logic",
    "Science": "stem_logic",
    "Creative Arts": "creative_arts",
    "Physical Education": "physical_movement",
}

TRENDS: list[str] = ["improving", "stable", "declining"]

LEVELS: list[str] = ["Strength", "Developing", "Emerging"]


# ─────────────────────────────────────────────
#  REQUEST
# ─────────────────────────────────────────────

class DomainFeatures(BaseModel):
    """One domain-row of features for one student, as computed by Phase 4's
    StudentAnalysisService from real academic_score_items / classwork data."""

    domain: str = Field(..., description=f"One of: {', '.join(DOMAINS)}")
    domain_avg_score: float = Field(..., ge=0, le=100, description="Mean subject score(s) in this domain, most recent term")
    domain_trend: str = Field(..., description=f"One of: {', '.join(TRENDS)}")
    domain_completion_rate: float = Field(..., ge=0, le=1, description="Completed / total classwork tagged with this domain's subject(s)")
    domain_overdue_count: int = Field(..., ge=0, description="Overdue assignments in this domain")
    ipsative_zscore: float = Field(..., description="(domain_avg - child's own cross-domain mean) / child's own cross-domain std")

    @field_validator("domain")
    @classmethod
    def domain_must_be_known(cls, v: str) -> str:
        if v not in DOMAINS:
            raise ValueError(f"domain must be one of {DOMAINS}")
        return v

    @field_validator("domain_trend")
    @classmethod
    def trend_must_be_known(cls, v: str) -> str:
        if v not in TRENDS:
            raise ValueError(f"domain_trend must be one of {TRENDS}")
        return v


class PredictionRequest(BaseModel):
    """A student's per-domain feature rows, sent by Spring Boot's
    StudentAnalysisService. Normally exactly 4 rows (one per domain), but
    fewer are accepted so a student missing data in one domain (e.g. no
    Physical Education scores recorded yet) can still get partial results."""

    student_id: str = Field(..., description="Unique student identifier")
    domains: list[DomainFeatures] = Field(..., min_length=1, max_length=4)


# ─────────────────────────────────────────────
#  RESPONSE
# ─────────────────────────────────────────────

class DomainPrediction(BaseModel):
    domain: str
    domain_display_name: str
    level: str = Field(..., description="'Strength' | 'Developing' | 'Emerging'")
    confidence: float = Field(..., ge=0.0, le=1.0)


class PredictionResponse(BaseModel):
    student_id: str
    domains: list[DomainPrediction]
    model_version: str
    disclaimer: str = (
        "This is a data-informed signal comparing your child against their own "
        "profile across domains, not a comparison against other children and not "
        "an academic diagnosis. It is trained on synthetic bootstrap data pending "
        "enough real historical records, and should be treated as a conversation "
        "starter, not a definitive assessment."
    )


# ─────────────────────────────────────────────
#  HEALTH CHECK
# ─────────────────────────────────────────────

class HealthCheckResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: str
    features: list[str]
