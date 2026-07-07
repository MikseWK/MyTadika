# MyTadika — AI Health Advice Feature Plan
## Health & Nutrition Module

> **Scope**: End-to-end plan for building an AI-powered health advice system for
> pre-school children (ages 1–6) within the MyTadika platform.
>
> **Core Idea**: A hybrid system that combines a **trained ML classification model**
> (for nutrition-status prediction) with a **deterministic rules-engine**
> (`adviceTemplates.js`) to deliver personalised, guideline-backed health advice
> to parents and tadika staff.

---

## Table of Contents

1. [Task 1 — Data Collection & Audit](#task-1--data-collection--audit)
2. [Task 2 — Data Cleaning & Preprocessing](#task-2--data-cleaning--preprocessing)
3. [Task 3 — Exploratory Data Analysis (EDA)](#task-3--exploratory-data-analysis-eda)
4. [Task 4 — Feature Engineering](#task-4--feature-engineering)
5. [Task 5 — Model Selection, Training & Tuning](#task-5--model-selection-training--tuning)
6. [Task 6 — Model Evaluation & Validation](#task-6--model-evaluation--validation)
7. [Task 7 — Rules Engine & Advice Generation](#task-7--rules-engine--advice-generation)
8. [Task 8 — Backend API Integration](#task-8--backend-api-integration)
9. [Task 9 — Frontend Integration & UX](#task-9--frontend-integration--ux)
10. [Task 10 — End-to-End Testing & QA](#task-10--end-to-end-testing--qa)
11. [Task 11 — Deployment & Monitoring](#task-11--deployment--monitoring)
12. [Task 12 — Documentation & Handover](#task-12--documentation--handover)

---

## Task 1 — Data Collection & Audit

### 1.1 Inventory of Available Data

| Dataset | File | Rows | Size | Key Columns |
|---------|------|------|------|-------------|
| Children Malnutrition | `malnutrition_data (1).csv` | 5,000 | ~400 KB | `age_months`, `weight_kg`, `height_cm`, `muac_cm`, `bmi`, `nutrition_status` |
| Childhood Allergies | `food-allergy-analysis-Zenodo.csv` | Large | ~88 MB | `SUBJECT_ID`, `GENDER_FACTOR`, `RACE_FACTOR`, demographics, allergy start/end for 15+ allergen types |
| Guideline PDFs (×5) | See `dataset/` folder | — | ~15 MB | Malaysian Dietary Guidelines, USDA 2025, WHO Sugar/Activity/Sleep guidelines, CDC Allergy Guidelines |

### 1.2 Data Quality Audit Checklist

- [ ] **Schema validation** — confirm column names, expected types (numeric, categorical)
- [ ] **Completeness check** — count missing/null values per column, calculate % missing
- [ ] **Range validation** — flag physiologically impossible values:
  - `age_months`: must be ≥ 0 and ≤ 72 (0–6 years)
  - `weight_kg`: plausible range ~2.5–30 kg for children 0–6 yrs
  - `height_cm`: plausible range ~45–130 cm
  - `bmi`: plausible range ~10–25 for children
  - `muac_cm`: plausible range ~10–22 cm
- [ ] **Duplicate detection** — identify exact-duplicate and near-duplicate rows
- [ ] **Label audit** — verify `nutrition_status` values are consistent (currently: `normal`, `moderate`, `severe`)
- [ ] **Class distribution analysis** — document imbalance:
  - `normal`: 3,550 (71.0%)
  - `moderate`: 1,100 (22.0%)
  - `severe`: 350 (7.0%) ← **severe underrepresentation**
- [ ] **Allergy dataset audit** — assess relevance to target age group (filter for ages 0–6), check `NA` density in allergen columns
- [ ] **Cross-reference guideline PDFs** — ensure all sources cited in `adviceTemplates.js` match available PDFs

### 1.3 Data Gap Analysis

- [ ] Identify whether additional external datasets are needed (e.g., WHO Child Growth Standards z-score tables, Malaysian national survey data)
- [ ] Assess if allergy dataset demographics align with the Malaysian tadika context
- [ ] Determine whether synthetic data generation is needed to supplement the `severe` class

---

## Task 2 — Data Cleaning & Preprocessing

### 2.1 Missing Value Treatment

| Strategy | When to Apply |
|----------|--------------|
| **Median imputation** | Numeric columns (`weight_kg`, `height_cm`, `muac_cm`) with < 10% missing |
| **KNN imputation** (k=5) | When missingness correlates with other features (e.g., `muac_cm` correlates with `weight_kg`) |
| **Mode imputation** | Categorical columns (e.g., `nutrition_status` if any blanks) |
| **Row removal** | Rows with > 50% columns missing, or where the target label is missing |

### 2.2 Outlier Detection & Handling

- **Statistical method**: Flag rows where any numeric feature falls beyond 3× IQR from Q1/Q3
- **Domain-based method**: Use WHO Child Growth Standards reference ranges as physiological bounds:
  - Weight-for-age z-score: flag if |z| > 5
  - Height-for-age z-score: flag if |z| > 5
  - BMI-for-age z-score: flag if |z| > 5
- **Handling strategy**:
  - Investigate flagged outliers manually (sample review)
  - Cap/Winsorise values at physiological bounds rather than deleting, to preserve sample size
  - Document all outlier decisions in a cleaning log

### 2.3 Data Type Corrections

- [ ] Convert `age_months` from float to integer (round to nearest month)
- [ ] Ensure all numeric columns are `float64`
- [ ] Encode `nutrition_status` as ordered categorical: `normal` → 0, `moderate` → 1, `severe` → 2
- [ ] Standardise column names (lowercase, underscores, no spaces)

### 2.4 Label Harmonisation

> **Issue**: The current labels are `normal`, `moderate`, `severe` — but `adviceTemplates.js`
> uses keys `0` (Underweight), `1` (Normal), `2` (Overweight). These schemas don't align.

- [ ] **Decision Required**: Define a clear mapping between the ML model's predicted labels and the advice engine's input keys
- [ ] Option A: Re-label dataset to `underweight` / `normal` / `overweight` if the data supports it (check if `moderate`/`severe` correspond to underweight conditions)
- [ ] Option B: Extend `adviceTemplates.js` to handle `normal` / `moderate` / `severe` malnutrition categories separately
- [ ] Option C: Train a separate model that predicts BMI-for-age z-score categories (underweight / normal / overweight) using WHO reference tables, independent of the dataset labels
- [ ] **Validate chosen mapping** with domain expert or paediatrician

### 2.5 Allergy Data Cleaning

- [ ] Filter allergy dataset to ages 0–6 only (`AGE_START_YEARS` ≤ 6)
- [ ] Extract binary allergy flags from start/end columns (e.g., `has_peanut_allergy = 1 if PEANUT_ALG_START is not NA`)
- [ ] Handle `NA` vs actual missing — `NA` in allergy columns likely means "no allergy" rather than "unknown"
- [ ] Merge/summarise by subject to get one row per child with allergy profile
- [ ] Drop columns with > 95% `NA` (rare allergens with insufficient data)

### 2.6 Data Cleaning Log

- [ ] Create `data_cleaning_report.md` documenting every transformation applied, with before/after row counts and rationale

---

## Task 3 — Exploratory Data Analysis (EDA)

### 3.1 Univariate Analysis

- [ ] **Distribution plots** (histograms + KDE) for each numeric feature: `age_months`, `weight_kg`, `height_cm`, `muac_cm`, `bmi`
- [ ] **Box plots** per feature grouped by `nutrition_status` — visualise separability
- [ ] **Class balance bar chart** — visualise the 71% / 22% / 7% imbalance
- [ ] **Summary statistics table** — mean, median, std, min, max, skewness, kurtosis per feature per class

### 3.2 Bivariate & Multivariate Analysis

- [ ] **Correlation heatmap** — Pearson correlation matrix of all numeric features
- [ ] **Pair plots** — scatterplot matrix coloured by `nutrition_status`
- [ ] **BMI vs Weight/Height scatter** — verify BMI is correctly derived from weight and height (BMI = weight / (height/100)²)
- [ ] **Age-stratified analysis** — do feature distributions change meaningfully across age bands (0–12m, 12–24m, 24–48m, 48–72m)?
- [ ] **MUAC vs nutrition status** — MUAC is a WHO-recognised screening tool; analyse its discriminative power

### 3.3 Feature Importance (Preliminary)

- [ ] Compute ANOVA F-scores for each feature vs. the target
- [ ] Compute mutual information scores
- [ ] Rank features by discriminative power to guide feature engineering

### 3.4 Allergy Data EDA

- [ ] Prevalence analysis: % of children with each allergen type
- [ ] Co-occurrence matrix: which allergies frequently appear together
- [ ] Age-of-onset distributions for common allergens (peanut, milk, egg)
- [ ] Demographic breakdown: allergy prevalence by gender, race/ethnicity

---

## Task 4 — Feature Engineering

### 4.1 Derived Features (Nutrition Model)

| Feature | Formula / Logic | Rationale |
|---------|----------------|-----------|
| `bmi_verified` | `weight_kg / (height_cm / 100)²` | Recompute BMI from raw measurements to eliminate data-entry errors |
| `weight_for_age_z` | WHO LMS z-score lookup by age & sex | Standard paediatric nutritional indicator |
| `height_for_age_z` | WHO LMS z-score lookup by age & sex | Identifies stunting |
| `bmi_for_age_z` | WHO LMS z-score lookup by age & sex | Identifies wasting / overweight |
| `muac_for_age_z` | WHO reference lookup | MUAC z-score is a field-standard malnutrition screen |
| `age_group` | Binned: `infant (0–12m)`, `toddler (12–36m)`, `preschool (36–72m)` | Age-group-specific nutritional needs differ |
| `weight_height_ratio` | `weight_kg / height_cm` | Simple proxy for body proportionality |
| `is_stunted` | `height_for_age_z < -2` | Binary flag for chronic malnutrition |
| `is_wasted` | `weight_for_height_z < -2` | Binary flag for acute malnutrition |

### 4.2 Feature Scaling

- [ ] **StandardScaler** (z-score normalisation) for algorithms sensitive to scale (SVM, KNN, Logistic Regression)
- [ ] **No scaling** needed for tree-based models (Random Forest, XGBoost, LightGBM)
- [ ] Fit scaler on training set only; transform validation/test sets using training statistics

### 4.3 Feature Selection

- [ ] Remove highly correlated feature pairs (|r| > 0.95) — e.g., `bmi` and `bmi_verified` likely redundant
- [ ] Use Recursive Feature Elimination (RFE) with cross-validation to identify optimal feature subset
- [ ] Evaluate with/without derived z-score features to measure their added value
- [ ] Final feature set should balance predictive power with interpretability (parents/staff need to understand inputs)

### 4.4 Class Imbalance Handling

> **Critical**: The `severe` class has only 350 samples (7%) — this will cause the model to underpredict severe malnutrition, which is the most dangerous outcome to miss.

| Technique | Description | When to Use |
|-----------|-------------|-------------|
| **SMOTE** (Synthetic Minority Oversampling) | Generates synthetic samples for minority classes via interpolation | First choice — produces realistic synthetic samples |
| **ADASYN** (Adaptive Synthetic Sampling) | Like SMOTE but focuses on harder-to-learn boundary samples | If SMOTE doesn't improve minority recall |
| **BorderlineSMOTE** | Oversamples only minority samples near the decision boundary | If model has good accuracy but poor boundary discrimination |
| **Class weights** | Assigns higher loss penalty to minority classes during training | Use in combination with sampling methods |
| **Random undersampling** of majority class | Reduce `normal` class to balance ratios | Only if dataset is large enough post-undersampling |
| **Hybrid: SMOTE + Tomek Links** | Oversample minority then clean overlapping majority samples | Best balance of synthetic data + cleaner boundaries |

**Target resampling ratio**: Aim for a balanced or near-balanced distribution (e.g., 1:1:1 or at minimum 2:1:1) **on the training set only**. Never resample the validation or test sets.

---

## Task 5 — Model Selection, Training & Tuning

### 5.1 Candidate Algorithms

| Algorithm | Why Consider It | Key Hyperparameters |
|-----------|----------------|---------------------|
| **Random Forest** | Robust to overfitting, handles class imbalance via `class_weight`, provides feature importance | `n_estimators`, `max_depth`, `min_samples_split`, `class_weight` |
| **XGBoost** | State-of-art gradient boosting, excellent with tabular data, supports `scale_pos_weight` | `learning_rate`, `max_depth`, `n_estimators`, `scale_pos_weight`, `subsample`, `colsample_bytree` |
| **LightGBM** | Faster than XGBoost on larger data, good with imbalanced classes | `num_leaves`, `learning_rate`, `n_estimators`, `is_unbalance`, `min_child_samples` |
| **Support Vector Machine (SVM)** | Strong with small datasets, effective in high-dimensional space | `C`, `kernel` (rbf/poly), `gamma`, `class_weight` |
| **K-Nearest Neighbours (KNN)** | Simple baseline, interpretable | `n_neighbors`, `weights`, `metric` |
| **Logistic Regression** | Interpretable baseline, fast, good for establishing a performance floor | `C`, `penalty`, `class_weight`, `solver` |
| **Multi-Layer Perceptron (MLP)** | Can capture non-linear patterns if simpler models plateau | `hidden_layer_sizes`, `activation`, `learning_rate`, `alpha` |

### 5.2 Training Strategy

- [ ] **Data split**: 70% train / 15% validation / 15% test (stratified by `nutrition_status`)
- [ ] Apply SMOTE / class weighting **only** on the training fold
- [ ] Use **5-fold Stratified Cross-Validation** on the training set for hyperparameter tuning
- [ ] Train all candidate models with default hyperparameters first (baseline comparison)
- [ ] Select top 3 models based on cross-validation **macro F1-score** (not accuracy — accuracy is misleading with imbalanced classes)

### 5.3 Hyperparameter Tuning

- [ ] Use **Optuna** or **RandomizedSearchCV** (more efficient than GridSearchCV for large parameter spaces)
- [ ] Define search spaces for each top-3 model
- [ ] Optimise for **macro F1-score** (balances precision/recall across all classes equally)
- [ ] Run at least 100 trials per model (Optuna) or 200 iterations (RandomizedSearchCV)
- [ ] Apply early stopping for boosted models (XGBoost/LightGBM) to prevent overfitting
- [ ] Log all trial results for reproducibility

### 5.4 Ensemble Methods

- [ ] After tuning individual models, evaluate ensemble approaches:
  - **Soft voting** classifier combining top 2–3 models
  - **Stacking** with a meta-learner (Logistic Regression on top of base model predictions)
- [ ] Compare ensemble performance against best single model
- [ ] Select final model based on best validation macro F1

---

## Task 6 — Model Evaluation & Validation

### 6.1 Primary Metrics (on held-out test set)

| Metric | Why It Matters |
|--------|---------------|
| **Macro F1-Score** | Primary metric — equally weights all classes, critical for imbalanced data |
| **Per-class Precision** | How many predicted `severe` are actually `severe`? (avoid false alarms) |
| **Per-class Recall (Sensitivity)** | What % of actual `severe` cases are detected? **Must be ≥ 85%** — missing severe malnutrition is dangerous |
| **Confusion Matrix** | Visualise misclassification patterns between classes |
| **Cohen's Kappa** | Agreement beyond chance — more informative than raw accuracy for multi-class |
| **ROC-AUC (One-vs-Rest)** | Discrimination ability per class across thresholds |
| **Precision-Recall AUC** | More informative than ROC-AUC when classes are heavily imbalanced |

### 6.2 Safety-Critical Validation

> **Principle**: It is far worse to predict a malnourished child as "normal" than to
> predict a normal child as "moderate". The system must prioritise **high recall for
> `severe` and `moderate` classes** even at the cost of some false positives.

- [ ] **Cost-sensitive analysis**: Assign asymmetric misclassification costs:
  - `severe → normal` prediction: **CRITICAL** (weight = 10×)
  - `moderate → normal` prediction: **HIGH** (weight = 5×)
  - `normal → moderate` prediction: LOW (weight = 1×)
- [ ] Adjust decision thresholds if using probabilistic models to maximise `severe` recall ≥ 85%
- [ ] Verify model doesn't show bias across age groups (infant vs toddler vs preschool)

### 6.3 Validation Checks

- [ ] **Learning curves** — plot train vs validation scores to diagnose overfitting/underfitting
- [ ] **Calibration curves** — are predicted probabilities well-calibrated? Apply Platt scaling or isotonic regression if not
- [ ] **Feature importance analysis** — SHAP values or permutation importance on the final model
- [ ] **Cross-validation stability** — ensure low variance across folds (std of F1 < 0.05)
- [ ] **Edge case testing** — test model with boundary inputs (e.g., child exactly at z-score = -2 border)

### 6.4 Model Interpretability

- [ ] Generate **SHAP summary plots** (beeswarm) to explain global feature contributions
- [ ] Generate **SHAP waterfall plots** for individual predictions (for explainability to parents)
- [ ] Document which features drive predictions for each class
- [ ] Ensure model reasoning aligns with clinical knowledge (e.g., low MUAC → higher severe risk)

---

## Task 7 — Rules Engine & Advice Generation

### 7.1 Architecture Overview

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────────────┐
│  Child Health     │     │  ML Model         │     │  Rules Engine             │
│  Input Data       │────▶│  (Classification) │────▶│  (adviceTemplates.js)     │
│  (age, weight,   │     │                    │     │                            │
│   height, MUAC,  │     │  Output:           │     │  Input: status + activity  │
│   allergies,     │     │  nutrition_status   │     │  + allergies               │
│   activity level)│     │  (0/1/2)           │     │                            │
└──────────────────┘     └──────────────────┘     │  Output: personalised      │
                                                    │  advice cards              │
                                                    └──────────────────────────┘
```

### 7.2 Advice Selection Logic

- [ ] **Dietary advice**: Select from `dietaryAdviceDatabase[predicted_status]`
  - Randomly sample 3–5 advice items per session to avoid repetition
  - Track which advice has been shown recently to rotate recommendations
- [ ] **Activity advice**: Select from `activityAdviceDatabase[activity_level]`
  - Activity level (0=Sedentary, 1=Normal, 2=Highly Active) is input by parent/staff
- [ ] **Allergy guardrails**: If child has recorded allergies, overlay from `allergyGuardrails[allergen]`
  - Include safe substitutions and cross-contact prevention rules
  - **Critical**: Allergy warnings must ALWAYS be shown regardless of nutrition status
  - Allergy advice must appear prominently (top of advice list, highlighted)

### 7.3 Advice Personalisation Enhancements

- [ ] **Age-appropriate filtering**: Filter advice items by child's age group (some advice is specific to certain age ranges)
- [ ] **Severity-based prioritisation**: If predicted status is `severe`, include the "Consult a health care professional" advice as the **first** recommendation
- [ ] **Confidence-based hedging**: If model prediction confidence < 70%, add a caveat: "This assessment has moderate certainty — we recommend consulting a healthcare professional for confirmation"
- [ ] **Temporal advice rotation**: Track advice history per child to avoid showing the same tips repeatedly
- [ ] **Multi-lingual support**: Prepare advice in Bahasa Malaysia and English (important for Malaysian tadika context)

### 7.4 Allergy Guardrail Validation

- [ ] Ensure allergy guardrails cannot be overridden or bypassed by dietary advice
- [ ] Test for conflicts: e.g., dairy-based advice (UW-07: "full-fat milk") must be suppressed if child has milk allergy
- [ ] Add guardrail logic: if `child.allergies.includes("milk")`, filter out any advice mentioning milk/dairy
- [ ] Log every time a guardrail intercepts a dietary recommendation for audit purposes

---

## Task 8 — Backend API Integration

### 8.0 Technology Stack & Architecture

We will implement a hybrid architecture with **Spring Boot** serving as the main backend application and a **Python FastAPI** microservice dedicated to serving the ML model.

#### Architectural Flow:
```
[Frontend (React/Next.js)]
         │
         ▼ (HTTP REST)
[Spring Boot Backend (mytadika-backend)]
  ├─ HealthController.java (REST endpoints)
  ├─ HealthAdviceService.java (Java-ported rules engine)
  └─ AiPredictionClient.java (HTTP client calling FastAPI)
         │
         ▼ (HTTP REST on localhost:8001)
[Python FastAPI Microservice (AI/api)]
  ├─ main.py (FastAPI entrypoint)
  └─ predictor.py (Loads best_model.joblib & runs inference)
```

### 8.1 API Endpoints (Spring Boot)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/health/predict` | Accept child measurements → return nutrition status prediction |
| `GET` | `/api/health/advice/:childId` | Get personalised advice for a specific child |
| `POST` | `/api/health/record` | Record new health measurement for a child |
| `GET` | `/api/health/history/:childId` | Get historical health records and trends |
| `PUT` | `/api/health/allergies/:childId` | Update child's allergy profile |
| `GET` | `/api/health/growth-chart/:childId` | Get growth chart data points for visualisation |

### 8.2 Model Serving Strategy

- [x] **Option A — Python microservice (Selected)**: Deploy the trained XGBoost model (`best_model.joblib`) as a Python FastAPI service in the `AI/api/` directory. The Spring Boot backend will call this microservice via HTTP requests using `AiPredictionClient.java`.
- [ ] **Option B — ONNX runtime**: Export model to ONNX format, load in Spring Boot backend using ONNX Runtime for Java.
- [ ] **Option C — PMML / H2O**: Export model to PMML or use a Java-compatible format for native Java inference.
- [ ] **Decision**: Option A is selected to keep the Python ML ecosystem (scikit-learn/XGBoost) decoupled from the Java backend, simplifying model loading, prediction, and pre-processing/post-processing tasks.

### 8.3 Input Validation & Safety

- [ ] Validate all inputs server-side in Spring Boot controller (type, range, required fields) using JSR 380 bean validation annotations (e.g. `@Min`, `@Max`, `@NotNull`)
- [ ] Sanitise inputs to prevent injection attacks
- [ ] Rate-limit prediction endpoint (prevent abuse)
- [ ] Return structured error responses with actionable messages using a global exception handler (`@ControllerAdvice`)
- [ ] Add medical disclaimer to all advice responses: *"This advice is generated from health guidelines and does not replace professional medical consultation"*

### 8.4 Data Storage (JPA & Relational Database)

- [ ] Design schema and map JPA Entities:
  - `HealthRecord.java` for storing child measurements over time
  - `AllergyProfile.java` for storing child allergy information
- [ ] Implement repositories using Spring Data JPA (e.g. `HealthRecordRepository.java`)
- [ ] Design schema for `advice_history` table (what advice was shown, when, to whom)
- [ ] Implement data retention policies (keep health records for school enrollment duration + 2 years)

### 8.5 Full Project Structure

The project will follow this structure to separate the AI/machine learning components from the core Spring Boot backend application:

```
MyTadika/
├── AI/                          ← Everything we've built (Python ML + JS rules engine)
│   ├── api/                     ← Python FastAPI microservice (Task 8, Python files)
│   │   ├── main.py
│   │   ├── predictor.py
│   │   ├── schemas.py
│   │   └── requirements.txt
│   ├── models/                  ← .joblib files
│   ├── notebooks/               ← Jupyter notebooks
│   └── rules_engine/            ← JS engine (reference / ported to Java)
│
└── mytadika-backend/            ← Spring Boot project (Java, after FYP1 report)
    └── src/main/java/
        ├── controller/
        │   └── HealthController.java     ← REST endpoints
        ├── service/
        │   ├── HealthAdviceService.java  ← ports healthAdviceEngine.js logic
        │   └── AiPredictionClient.java   ← calls Python FastAPI
        ├── model/
        │   ├── HealthRecord.java         ← JPA entity
        │   └── AllergyProfile.java
        └── repository/
            └── HealthRecordRepository.java
```

### 8.6 Rules Engine Java Port

The rules engine logic defined in `AI/rules_engine/healthAdviceEngine.js` will be ported to Java as a Spring Boot service:
- **`HealthAdviceService.java`**: Implements the selection logic, demographic rules, and allergy guardrails.
- **Advice Templates**: The JS-based templates in `adviceTemplates.js` will be externalized into a JSON file (e.g. `src/main/resources/advice_templates.json`) loaded by Spring Boot at application startup.
- **Label Mapping**: Ensure the ML model predictions (`normal`, `moderate`, `severe`) are mapped to the advice engine's keys correctly in Java, preserving the logic from `healthAdviceEngine.js`.

---

## Task 9 — Frontend Integration & UX

### 9.1 Health Dashboard Components

- [ ] **Child Health Summary Card** — displays latest nutrition status with colour-coded indicator (green/amber/red)
- [ ] **Growth Chart** — interactive line chart showing weight, height, BMI over time (plotted against WHO percentile curves)
- [ ] **AI Advice Panel** — displays personalised advice cards with source citations
- [ ] **Allergy Alert Banner** — persistent, prominent banner showing active allergies and key warnings
- [ ] **Measurement Input Form** — for staff to record new height/weight/MUAC readings
- [ ] **History Timeline** — scrollable timeline of past assessments and advice

### 9.2 UX Considerations

- [ ] Advice must be written in **parent-friendly language** (avoid medical jargon)
- [ ] Use traffic-light colour coding: 🟢 Normal, 🟡 Moderate concern, 🔴 Severe concern
- [ ] Include actionable next steps in every advice card
- [ ] Mobile-responsive design (parents primarily access via phone)
- [ ] Accessibility: minimum AA contrast ratios, screen reader support

---

## Task 10 — End-to-End Testing & QA

### 10.1 Unit Tests

- [ ] **Data pipeline tests**: Verify cleaning functions handle edge cases (empty rows, NaN, negative values, extreme values)
- [ ] **Feature engineering tests**: Verify z-score calculations match WHO reference tables (spot-check ≥ 10 known values)
- [ ] **Model prediction tests**: Verify model output shape, label encoding, and probability sums (should sum to 1.0)
- [ ] **Advice selection tests**: Verify correct advice template is returned for each nutrition status
- [ ] **Allergy guardrail tests**: Verify conflicting advice is filtered (e.g., milk advice suppressed for milk-allergic child)
- [ ] **Input validation tests**: Verify API rejects invalid inputs (negative weight, missing fields, out-of-range values)

### 10.2 Integration Tests

- [ ] **End-to-end prediction pipeline**: Raw input → preprocessing → model prediction → advice generation → API response
- [ ] **Database integration**: Verify records are correctly stored and retrieved
- [ ] **Allergy override scenarios**: Verify allergy guardrails intercept conflicting dietary advice in live pipeline
- [ ] **Concurrent request handling**: Verify API handles multiple simultaneous predictions without data leakage between requests

### 10.3 Model-Specific Tests

- [ ] **Regression tests**: Save a "golden" test set of 50 inputs with expected outputs; re-run after any model update to detect regressions
- [ ] **Invariance tests**: Predictions should not change meaningfully if irrelevant features are slightly perturbed
- [ ] **Directional tests**: Increasing weight while holding height/age constant should move prediction towards overweight
- [ ] **Boundary tests**: Test inputs at exact WHO z-score thresholds (-2, -1, +1, +2) to verify correct classification
- [ ] **Stress tests**: Test with extreme but technically valid inputs (e.g., 1-month-old, 72-month-old, very low MUAC)

### 10.4 Safety Tests (Critical)

- [ ] **False negative audit**: Sample all test-set cases where `severe` was predicted as `normal` — these must be near-zero
- [ ] **Allergy safety audit**: For every allergen in the system, verify that no dietary advice recommends foods containing that allergen
- [ ] **Advice source verification**: Spot-check 20% of advice entries to confirm source citations match the actual guideline text
- [ ] **Disclaimer presence check**: Verify medical disclaimer is present in every API response and UI display

### 10.5 User Acceptance Testing (UAT)

- [ ] Conduct UAT with 3–5 tadika staff members
- [ ] Collect feedback on advice clarity, relevance, and actionability
- [ ] Test with real (anonymised) child data if possible
- [ ] Validate that non-technical users can interpret advice correctly

---

## Task 11 — Deployment & Monitoring

### 11.1 Deployment Checklist

- [ ] Model artifact versioned and stored (with training date, dataset hash, hyperparameters)
- [ ] Environment variables configured (model path, API keys, database connection)
- [ ] CORS and authentication configured for API endpoints
- [ ] SSL/TLS enabled for all data in transit
- [ ] Health check endpoint (`/api/health/status`) for uptime monitoring

### 11.2 Monitoring & Alerting

- [ ] **Prediction distribution monitoring**: Track the distribution of predicted classes over time — alert if `severe` predictions suddenly spike or drop to zero (data drift indicator)
- [ ] **Input data drift detection**: Monitor incoming feature distributions; alert if incoming data diverges significantly from training data distribution
- [ ] **Latency monitoring**: Track prediction response times; alert if p95 > 2 seconds
- [ ] **Error rate monitoring**: Track API error rates; alert if > 1% of requests fail
- [ ] **Advice engagement tracking**: Monitor which advice cards are viewed/dismissed by users

### 11.3 Model Retraining Plan

- [ ] **Trigger criteria**: Retrain when (a) new labelled data adds > 20% to dataset, (b) prediction drift detected, or (c) guidelines are updated
- [ ] **Retraining pipeline**: Automated or semi-automated pipeline that re-runs Tasks 2–6 with updated data
- [ ] **Champion/Challenger**: Deploy new model alongside old model, compare predictions before promoting
- [ ] **Version history**: Maintain a log of all model versions with performance metrics

---

## Task 12 — Documentation & Handover

### 12.1 Technical Documentation

- [ ] **Data dictionary**: Full description of every feature, its source, transformations applied, and valid ranges
- [ ] **Model card**: Training data description, algorithm, hyperparameters, performance metrics, known limitations, ethical considerations
- [ ] **API documentation**: OpenAPI/Swagger spec for all endpoints
- [ ] **Architecture diagram**: System-level diagram showing data flow from input to advice display

### 12.2 User-Facing Documentation

- [ ] **Staff guide**: How to enter measurements, interpret advice, handle allergy alerts
- [ ] **Parent guide**: How to read the health dashboard, understanding advice recommendations
- [ ] **FAQ**: Common questions about AI advice accuracy, data privacy, when to consult a doctor

### 12.3 Ethical & Privacy Considerations

- [ ] **Data privacy**: All child health data is PII — ensure PDPA (Malaysia) compliance
- [ ] **Consent**: Parents must provide explicit consent for health data collection and AI analysis
- [ ] **Transparency**: Clearly communicate that advice is AI-generated, not a medical diagnosis
- [ ] **Bias audit**: Verify model performance is equitable across gender and age groups
- [ ] **Right to explanation**: Parents should be able to understand why specific advice was given (SHAP-based explanations)

---

## Risk Register

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Severe malnutrition case missed (false negative) | **Critical** | Medium | High recall target (≥85%), cost-sensitive training, mandatory professional referral advice |
| Allergy-conflicting advice shown | **Critical** | Low | Guardrail filtering, comprehensive integration tests, audit logging |
| Model performance degrades over time (data drift) | High | Medium | Prediction monitoring, automated drift detection, retraining pipeline |
| Class imbalance causes poor minority-class predictions | High | High | SMOTE, class weights, macro F1 optimisation, balanced validation |
| Label mapping mismatch between model and advice engine | High | High | Explicit mapping validation in Task 2.4, integration tests |
| Dataset too small for complex models | Medium | Medium | Start with simpler models (RF, LR), evaluate if ensemble adds value, consider data augmentation |
| Parents misinterpret AI advice as medical diagnosis | High | Medium | Prominent disclaimers, clear UI language, professional referral always included |

---

## Dependencies & Tool Stack

| Component | Recommended Tools |
|-----------|-------------------|
| Data cleaning & EDA | Python (pandas, numpy, matplotlib, seaborn) |
| Feature engineering | Python (scipy for z-scores, scikit-learn) |
| Model training | scikit-learn, XGBoost, LightGBM, Optuna |
| Class imbalance | imbalanced-learn (SMOTE, ADASYN, Tomek Links) |
| Model interpretability | SHAP, scikit-learn (permutation importance) |
| Model export | joblib/pickle (scikit-learn), ONNX (cross-platform) |
| Backend API | Spring Boot (Java) + Python (FastAPI) |
| Rules engine | Java (`HealthAdviceService.java` + JSON templates) |
| Frontend | React / Next.js (existing MyTadika stack) |
| Testing | JUnit/Mockito (Java), pytest (Python), Postman (API) |
| Version control | Git |

---

## Milestones & Timeline

| Phase | Tasks | Estimated Duration |
|-------|-------|--------------------|
| **Phase 1: Data Foundation** | Tasks 1–3 (Audit, Clean, EDA) | 1–2 weeks |
| **Phase 2: Model Development** | Tasks 4–6 (Features, Train, Evaluate) | 2–3 weeks |
| **Phase 3: Integration** | Tasks 7–9 (Rules Engine, API, Frontend) | 2–3 weeks |
| **Phase 4: Quality & Launch** | Tasks 10–12 (Testing, Deploy, Docs) | 1–2 weeks |
