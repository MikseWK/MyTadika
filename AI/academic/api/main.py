"""
main.py

FastAPI application entrypoint for the MyTadika AI Academic
Interest/Strength-Area Microservice.

Exposes REST endpoints:
  - GET  /health       : Status check showing model loading state & version.
  - POST /api/predict  : Accepts a student's per-domain features, returns a
                         per-domain {level, confidence}.
"""

from __future__ import annotations

import logging
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from schemas import PredictionRequest, PredictionResponse, HealthCheckResponse
from predictor import predict_domains, get_registry

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="MyTadika AI Academic Interest/Strength-Area Service",
    description="Microservice classifying a child's relative domain strengths "
                "(Language & Literacy, STEM & Logic, Creative Arts, Physical & "
                "Movement) against their own profile, not against other children.",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def startup_event() -> None:
    try:
        get_registry()
        logger.info("FastAPI microservice initialized successfully.")
    except Exception as e:
        logger.error("Failed to load model registry: %s", e, exc_info=True)


@app.get(
    "/health",
    response_model=HealthCheckResponse,
    status_code=status.HTTP_200_OK,
    summary="Microservice health check"
)
def health_check() -> dict:
    try:
        reg = get_registry()
        return {
            "status": "healthy",
            "model_loaded": reg.model is not None,
            "model_version": reg.model_version,
            "features": reg.feature_columns,
        }
    except Exception as e:
        logger.error("Health check reported unhealthy state: %s", e)
        return {
            "status": "unhealthy",
            "model_loaded": False,
            "model_version": "unknown",
            "features": [],
        }


@app.post(
    "/api/predict",
    response_model=PredictionResponse,
    status_code=status.HTTP_200_OK,
    summary="Predict a student's per-domain interest/strength level"
)
def predict(payload: PredictionRequest) -> dict:
    logger.info("Received prediction request for student_id: %s (%d domain rows)",
                payload.student_id, len(payload.domains))
    try:
        domain_rows = [d.model_dump() for d in payload.domains]
        result = predict_domains(payload.student_id, domain_rows)
        return result
    except FileNotFoundError as fnf:
        logger.error("Model files missing during prediction request: %s", fnf)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Machine learning model assets are not loaded on server."
        )
    except Exception as e:
        logger.error("Error executing prediction for student_id %s: %s", payload.student_id, e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"An error occurred while computing the prediction: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8002, reload=True)
