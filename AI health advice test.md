# AI Health Advice — Local Testing Guide

This guide walks you through starting **every component** of the MyTadika AI Health Advice system and running the provided test scripts to confirm everything works end-to-end.

> **Components involved**
> | Layer | Technology | Port |
> |-------|-----------|------|
> | AI Microservice | Python FastAPI + joblib | `8001` |
> | Backend API | Spring Boot (Java 17) | `8080` |
> | Database | H2 in-memory (auto-created) | embedded |

---

## Prerequisites — Install Once

### Python (for FastAPI)
Open **PowerShell** and run:
```powershell
# Install Python dependencies (inside the AI/api folder)
cd "c:\Users\Wai Kit Liew\Desktop\FYP\MyTadika\AI\api"
pip install -r requirements.txt

# Also install 'requests' for the test scripts
pip install requests
```

### Java & Maven (for Spring Boot)
Make sure **Java 17+** and **Maven** are installed:
```powershell
java -version     # should show 17 or higher
mvn -version      # should show Apache Maven
```
> If Maven is not installed, download it from https://maven.apache.org/download.cgi  
> or use `mvn` via IntelliJ IDEA's built-in Maven.

---

## Step 1 — Verify Model Files Exist

The FastAPI service needs pre-trained model artefacts. Confirm they are present:

```powershell
ls "c:\Users\Wai Kit Liew\Desktop\FYP\MyTadika\AI\models"
```

You should see **all of these files**:

| File | Purpose |
|------|---------|
| `best_model.joblib` | Trained XGBoost / ensemble classifier |
| `scaler.joblib` | StandardScaler fitted on training data |
| `selected_features.joblib` | Feature names used during training |
| `severe_threshold.joblib` | Custom threshold for severe-class recall |
| `best_model_name.txt` | Text file with the model's name |

> **If any file is missing**, open and run all cells in  
> `AI/notebooks/Model_Training.ipynb` first (Tasks 4–6 cells).

---

## Step 2 — Start the FastAPI AI Microservice

Open a **new PowerShell window** (keep it open during testing):

```powershell
cd "c:\Users\Wai Kit Liew\Desktop\FYP\MyTadika\AI\api"
python main.py
```

**Expected output:**
```
INFO:     Started server process [...]
INFO:     Waiting for application startup.
INFO     service: Loading model artefacts from ...\AI\models ...
INFO     service: Model loaded: XGBoost | features: 8 | severe_threshold: 0.XXX
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8001
```

✅ **Verify it's up** — open your browser and go to:  
→ http://localhost:8001/health  
→ http://localhost:8001/docs (interactive Swagger UI)

---

## Step 3 — Start the Spring Boot Backend

Open a **second new PowerShell window** (keep it open during testing):

```powershell
cd "c:\Users\Wai Kit Liew\Desktop\FYP\MyTadika\mytadika-backend"
mvn spring-boot:run OR .\mvnw.cmd spring-boot:run

```

**Expected output (last lines):**
```
INFO  service.HealthAdviceService  : Loading health advice templates from JSON resource...
INFO  service.HealthAdviceService  : Successfully loaded health advice templates database.
INFO  o.s.b.w.e.tomcat.TomcatWebServer : Tomcat started on port(s): 8080
INFO  MyTadikaApplication          : Started MyTadikaApplication in X.XXX seconds
```

✅ **Verify it's up** — open your browser and go to:  
→ http://localhost:8080/h2-console  
(JDBC URL: `jdbc:h2:mem:mytadikadb`, Username: `sa`, Password: *leave blank*)

> **Note:** If FastAPI (Step 2) is also running, Spring Boot will use the real ML model.  
> If FastAPI is **not** running, Spring Boot automatically falls back to its built-in  
> rule-based classifier (BMI heuristics) — this is by design.

---

## Step 4 — Run the FastAPI Tests

Open a **third PowerShell window**:

```powershell
cd "c:\Users\Wai Kit Liew\Desktop\FYP\MyTadika\AI\api"
python test_fastapi.py
```

This runs **10 tests** directly against the FastAPI microservice:

| Test | What it checks |
|------|---------------|
| T1 | `/health` returns `healthy` + model loaded |
| T2 | Normal child → valid response structure |
| T3 | Low-weight child → moderate/severe classification |
| T4 | MUAC < 11.5 cm → `is_sam = true` flag |
| T5 | Mixed-case gender inputs are normalised |
| T6 | Works fine without optional fields |
| T7 | `age_months = 0` (newborn) is accepted |
| T8 | `age_months = 100` is rejected with HTTP 422 |
| T9 | `weight_kg = 1.0` is rejected with HTTP 422 |
| T10 | Missing required field rejected with HTTP 422 |

**Expected output:**
```
══════════════════════════════════════════════════════════════
  MyTadika — FastAPI AI Microservice Test Suite
  Target: http://localhost:8001
══════════════════════════════════════════════════════════════
  [PASS]  T1  Health check endpoint
  [PASS]  T2  Predict — normal child
  ...
  Results: 10/10 passed  |  0 failed
══════════════════════════════════════════════════════════════
```

---

## Step 5 — Run the Spring Boot End-to-End Tests

In the same (or another) PowerShell window:

```powershell
cd "c:\Users\Wai Kit Liew\Desktop\FYP\MyTadika\AI\api"
python test_springboot.py
```

This runs **10 tests** through the full stack (Spring Boot → FastAPI → Rules Engine → Response):

| Test | Endpoint | What it checks |
|------|----------|---------------|
| T1 | `POST /api/health/predict` | Normal child → dietary + activity advice cards returned |
| T2 | `POST /api/health/predict` | Allergies → guardrails fire, allergyWarnings populated |
| T3 | `POST /api/health/predict` | Severe child → `requiresUrgentReferral = true` |
| T4 | `POST /api/health/predict` | Shown advice IDs → rotation works |
| T5 | `POST /api/health/record` | Saves record to H2 DB, returns recordId |
| T6 | `GET  /api/health/history/{id}` | Returns the record saved in T5 |
| T7 | `GET  /api/health/advice/{id}` | Generates advice from stored DB record |
| T8 | `PUT  /api/health/allergies/{id}` | Updates allergy profile, persisted to DB |
| T9 | `POST /api/health/predict` | Missing `childId` → HTTP 400 |
| T10 | `POST /api/health/predict` | `activityLevel = 5` → HTTP 400 |

**Expected output:**
```
══════════════════════════════════════════════════════════════════
  MyTadika — Spring Boot Backend End-to-End Test Suite
  Target: http://localhost:8080
══════════════════════════════════════════════════════════════════
  [PASS]  T1  POST /predict — normal child
  [PASS]  T2  POST /predict — with allergies
  ...
  Results: 10/10 passed  |  0 failed
══════════════════════════════════════════════════════════════════
```

---

## Step 6 — Manual Curl / Postman Testing (Optional)

You can also test manually. Here are ready-to-use `curl` commands:

### FastAPI — Direct predict
```powershell
curl -X POST http://localhost:8001/api/predict `
  -H "Content-Type: application/json" `
  -d '{
    "child_id": "manual-01",
    "age_months": 36,
    "weight_kg": 14.0,
    "height_cm": 96.0,
    "muac_cm": 16.0,
    "gender": "male"
  }'
```

### Spring Boot — Predict + advice (with allergies)
```powershell
curl -X POST http://localhost:8080/api/health/predict `
  -H "Content-Type: application/json" `
  -d '{
    "childId": "manual-02",
    "ageMonths": 30,
    "weightKg": 11.0,
    "heightCm": 88.0,
    "activityLevel": 1,
    "allergies": ["milk", "peanut"]
  }'
```

### Spring Boot — Save record
```powershell
curl -X POST http://localhost:8080/api/health/record `
  -H "Content-Type: application/json" `
  -d '{
    "childId": "1001",
    "ageMonths": 36,
    "weightKg": 13.5,
    "heightCm": 94.0,
    "activityLevel": 1,
    "gender": "female",
    "allergies": ["milk"],
    "recordedBy": 999
  }'
```

### Spring Boot — Get history
```powershell
curl http://localhost:8080/api/health/history/1001
```

### Spring Boot — Update allergies
```powershell
curl -X PUT http://localhost:8080/api/health/allergies/1001 `
  -H "Content-Type: application/json" `
  -d '{ "allergies": ["peanut", "milk"] }'
```

---

## Troubleshooting

### FastAPI won't start — `FileNotFoundError: best_model.joblib not found`
→ Run the training notebook: `AI/notebooks/Model_Training.ipynb` (all cells in Tasks 4–6)

### Spring Boot won't start — `Unable to load advice_templates.json`
→ Ensure `advice_templates.json` exists at:  
`mytadika-backend/src/main/resources/advice_templates.json`

### Spring Boot won't start — `Bean not found` or component scan error
→ Check `MyTadikaApplication.java`. The `@SpringBootApplication` must have:
```java
@SpringBootApplication(scanBasePackages = {"controller", "service"})
@EntityScan(basePackages = {"model"})
@EnableJpaRepositories(basePackages = {"repository"})
```

### Test T9 gets 500 instead of 400 (missing childId)
→ Spring Boot Bean Validation requires `spring-boot-starter-validation` in `pom.xml` —  
confirm it is present (it already is in the generated `pom.xml`).

### Spring Boot test `T3 severe` passes but `requiresUrgentReferral` is false
→ The fallback heuristic triggers severe at `BMI < 12`. Try `weight_kg=5.5, height_cm=72`  
(BMI ≈ 10.6). If FastAPI is running, the ML model decides — status may differ.

### `requests` module not found when running test scripts
```powershell
pip install requests
```

---

## Component Summary

```
[Terminal 1]  python AI/api/main.py          → FastAPI on :8001
[Terminal 2]  mvn spring-boot:run            → Spring Boot on :8080
[Terminal 3]  python AI/api/test_fastapi.py  → 10 FastAPI tests
[Terminal 3]  python AI/api/test_springboot.py → 10 E2E tests
```

---

## Key Files Reference

| File | Purpose |
|------|---------|
| [`AI/api/main.py`](AI/api/main.py) | FastAPI entrypoint |
| [`AI/api/predictor.py`](AI/api/predictor.py) | ML inference + feature engineering |
| [`AI/api/schemas.py`](AI/api/schemas.py) | Pydantic request/response schemas |
| [`AI/api/requirements.txt`](AI/api/requirements.txt) | Python dependencies |
| [`AI/api/test_fastapi.py`](AI/api/test_fastapi.py) | FastAPI test suite (10 tests) |
| [`AI/api/test_springboot.py`](AI/api/test_springboot.py) | Spring Boot E2E test suite (10 tests) |
| [`mytadika-backend/src/main/java/controller/HealthController.java`](mytadika-backend/src/main/java/controller/HealthController.java) | Spring Boot REST controller |
| [`mytadika-backend/src/main/java/service/HealthAdviceService.java`](mytadika-backend/src/main/java/service/HealthAdviceService.java) | Rules engine + advice generation |
| [`mytadika-backend/src/main/java/service/AiPredictionClient.java`](mytadika-backend/src/main/java/service/AiPredictionClient.java) | FastAPI HTTP client + fallback |
| [`mytadika-backend/src/main/resources/application.properties`](mytadika-backend/src/main/resources/application.properties) | H2 DB + server config |
| [`mytadika-backend/src/main/resources/advice_templates.json`](mytadika-backend/src/main/resources/advice_templates.json) | Advice card database |
