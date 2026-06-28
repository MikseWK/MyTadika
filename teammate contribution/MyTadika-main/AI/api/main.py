"""
main.py

FastAPI application entrypoint for the MyTadika AI Health Microservice.

Exposes REST endpoints:
  - GET  /health          : Status check showing model loading state & version.
  - POST /api/predict     : Accepts child anthropometrics, returns prediction and clinical flags.
"""

from __future__ import annotations

import logging
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware

from schemas import PredictionRequest, PredictionResponse, HealthCheckResponse
from predictor import predict, get_registry

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="MyTadika AI Health Service",
    description="Microservice providing nutritional status classification and clinical flags for pre-school children.",
    version="1.0.0"
)

# Configure CORS so Spring Boot backend can access the API
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Adjust in production configuration
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def startup_event() -> None:
    """Load model registry at startup to verify all assets exist."""
    try:
        get_registry()
        logger.info("FastAPI microservice initialized successfully.")
    except Exception as e:
        logger.error("Failed to load model registry: %s", e, exc_info=True)


# ─────────────────────────────────────────────
#  ENDPOINTS
# ─────────────────────────────────────────────

@app.get(
    "/health",
    response_model=HealthCheckResponse,
    status_code=status.HTTP_200_OK,
    summary="Microservice health check"
)
def health_check() -> dict:
    """Returns the load status, model version, and features used for inference."""
    try:
        reg = get_registry()
        return {
            "status": "healthy",
            "model_loaded": reg.model is not None,
            "model_version": reg.model_version,
            "features": reg.selected_features
        }
    except Exception as e:
        logger.error("Health check reported unhealthy state: %s", e)
        return {
            "status": "unhealthy",
            "model_loaded": False,
            "model_version": "unknown",
            "features": []
        }


@app.post(
    "/api/predict",
    response_model=PredictionResponse,
    status_code=status.HTTP_200_OK,
    summary="Predict children nutritional status"
)
def predict_nutrition_status(payload: PredictionRequest) -> dict:
    """
    Classifies child nutrition status and generates WHO indicators.

    Accepts raw anthropometric data and returns:
      - status (normal, moderate, severe)
      - probabilities per class
      - clinical flags (SAM, stunting, wasting)
    """
    logger.info("Received prediction request for child_id: %s", payload.child_id)
    try:
        result = predict(
            child_id=payload.child_id,
            age_months=payload.age_months,
            weight_kg=payload.weight_kg,
            height_cm=payload.height_cm,
            muac_cm=payload.muac_cm,
            bmi=payload.bmi,
            gender=payload.gender
        )
        return result
    except FileNotFoundError as fnf:
        logger.error("Model files missing during prediction request: %s", fnf)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Machine learning model assets are not loaded on server."
        )
    except Exception as e:
        logger.error("Error executing prediction for child_id %s: %s", payload.child_id, e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"An error occurred while computing the prediction: {str(e)}"
        )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8001, reload=True)
