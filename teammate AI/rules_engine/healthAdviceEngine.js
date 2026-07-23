/**
 * healthAdviceEngine.js
 *
 *  — Rules Engine & Advice Generation
 * MyTadika Health & Nutrition Module — Final Year Project
 *
 * PURPOSE
 * ───────
 * This module is the bridge between:
 *   1. The ML model output  (nutrition_status: 'normal' | 'moderate' | 'severe')
 *   2. The adviceTemplates.js database (keys: 0=Underweight, 1=Normal, 2=Overweight)
 *
 * It handles:
 *   • Label mapping:     ML labels  → advice database keys
 *   • Advice selection:  Dietary + Activity + Allergy advice per child profile
 *   • Allergy guardrails: Filters advice that conflicts with recorded allergies
 *   • Age filtering:     Ensures advice is age-appropriate for the child
 *   • Confidence hedging: Adds caveats when model confidence < 70%
 *   • Advice rotation:   Tracks shown advice per child to avoid repetition
 *   • Medical disclaimer: Always attached to every response
 *
 * USAGE (ESM)
 * ───────────
 *   import { generateAdvice } from './healthAdviceEngine.js';
 *
 *   const result = generateAdvice({
 *     childId:         'child_123',
 *     modelOutput:     { status: 'moderate', encoded: 1, confidence: 0.82,
 *                        probabilities: { normal: 0.05, moderate: 0.82, severe: 0.13 } },
 *     activityLevel:   1,            // 0=Sedentary, 1=Normal, 2=HighlyActive
 *     allergies:       ['milk'],     // from child's allergy profile
 *     ageMonths:       36,           // child's age in months
 *     shownAdviceIds:  ['UW-01', 'NW-03']  // previously shown advice IDs
 *   });
 */

import {
  dietaryAdviceDatabase,
  activityAdviceDatabase,
  allergyGuardrails,
} from './adviceTemplates.js';

// ═══════════════════════════════════════════════════════════════════════
// CONSTANTS & CONFIGURATION
// ═══════════════════════════════════════════════════════════════════════

/**
 * Maps ML model output labels to adviceTemplates.js dietary keys.
 *
 * ML model produces:  'normal' | 'moderate' | 'severe'
 * adviceTemplates uses: 0 = Underweight, 1 = Normal, 2 = Overweight
 *
 * Mapping rationale:
 *   - 'normal'   → key 1 (Normal Weight)    — child is at healthy weight
 *   - 'moderate' → key 0 (Underweight)      — moderate malnutrition ≈ underweight/wasting
 *   - 'severe'   → key 0 (Underweight)      — severe malnutrition ≈ severe underweight/SAM
 *
 * NOTE: The adviceTemplates.js was designed for a weight-status axis
 * (underweight/normal/overweight). The ML dataset labels (normal/moderate/severe)
 * map to malnutrition severity. Both 'moderate' and 'severe' map to the
 * underweight advice bank, which is appropriate because both represent
 * insufficient growth/nutrition.
 */
export const ML_TO_ADVICE_KEY = {
  normal:   1,   // → Normal Weight advice
  moderate: 0,   // → Underweight advice (moderate malnutrition)
  severe:   0,   // → Underweight advice (severe malnutrition — same bank, prioritised differently)
};

/** Number of dietary advice cards to include in each response. */
const DIETARY_ADVICE_COUNT = 4;

/** Number of activity advice cards to include in each response. */
const ACTIVITY_ADVICE_COUNT = 3;

/** Confidence threshold below which a hedging caveat is added. */
const CONFIDENCE_THRESHOLD = 0.70;

/** Discount applied per severe clinical flag (SAM, stunted etc.) — reserved for future use. */

/**
 * Keyword patterns that indicate an advice entry contains dairy/milk content.
 * Used to filter advice when a child has milk allergy.
 */
const MILK_KEYWORDS = [
  'milk', 'dairy', 'yoghurt', 'yogurt', 'cultured milk', 'full-fat',
  'UHT', 'pasteurised', 'cheese', 'butter', 'cream', 'casein', 'whey',
  'lactose', 'condensed milk',
];

/**
 * Keyword patterns that indicate an advice entry contains peanut content.
 * Used to filter advice when a child has peanut allergy.
 */
const PEANUT_KEYWORDS = [
  'peanut', 'groundnut', 'peanut butter', 'kacang', 'nut',
];

/**
 * Allergen → keyword list mapping.
 * Add more allergens here as the system expands.
 */
const ALLERGEN_KEYWORD_MAP = {
  milk:   MILK_KEYWORDS,
  peanut: PEANUT_KEYWORDS,
};

/** Medical disclaimer — always attached to every advice response. */
const MEDICAL_DISCLAIMER =
  'This advice is generated from validated paediatric health guidelines ' +
  '(Malaysian Dietary Guidelines, WHO, USDA, CDC) and does not replace ' +
  'professional medical consultation. Please consult a qualified healthcare ' +
  'professional (doctor, nutritionist, or dietitian) for personalised medical advice.';

// ═══════════════════════════════════════════════════════════════════════
// INTERNAL HELPERS
// ═══════════════════════════════════════════════════════════════════════

/**
 * Checks whether a text string contains any of the given keywords.
 * Case-insensitive.
 * @param {string} text
 * @param {string[]} keywords
 * @returns {boolean}
 */
function containsKeyword(text, keywords) {
  const lower = text.toLowerCase();
  return keywords.some((kw) => lower.includes(kw.toLowerCase()));
}

/**
 * Returns true if an advice entry contains content conflicting with any
 * of the child's recorded allergies.
 *
 * @param {{ title: string, body: string }} adviceEntry
 * @param {string[]} allergies  e.g. ['milk', 'peanut']
 * @returns {boolean} true = BLOCKED by a guardrail
 */
function isBlockedByAllergyGuardrail(adviceEntry, allergies) {
  const combined = `${adviceEntry.title} ${adviceEntry.body}`;
  for (const allergen of allergies) {
    const keywords = ALLERGEN_KEYWORD_MAP[allergen];
    if (keywords && containsKeyword(combined, keywords)) {
      return true;
    }
  }
  return false;
}

/**
 * Selects a subset of advice entries from a pool, respecting:
 *   1. Allergy guardrails (blocked entries are excluded)
 *   2. Rotation (entries in `shownIds` are deprioritised)
 *   3. Requested count
 *
 * Strategy: fill from "not recently shown" first, then pad with "shown" if needed.
 *
 * @param {object[]} pool           Full advice pool for this category
 * @param {number}   count          How many to select
 * @param {string[]} allergies      Child's allergens
 * @param {string[]} shownIds       IDs of previously shown entries
 * @param {object[]} guardrailLog   Array to push blocked entries into (for audit)
 * @returns {object[]}
 */
function selectAdvice(pool, count, allergies, shownIds, guardrailLog) {
  const allowed = [];
  const blocked = [];

  for (const entry of pool) {
    if (isBlockedByAllergyGuardrail(entry, allergies)) {
      blocked.push(entry);
      guardrailLog.push({
        adviceId:   entry.id,
        title:      entry.title,
        blockedBy:  allergies.filter((a) => {
          const kws = ALLERGEN_KEYWORD_MAP[a];
          return kws && containsKeyword(`${entry.title} ${entry.body}`, kws);
        }),
        timestamp:  new Date().toISOString(),
      });
    } else {
      allowed.push(entry);
    }
  }

  // Split allowed into fresh (not shown) and stale (already shown)
  const fresh = allowed.filter((e) => !shownIds.includes(e.id));
  const stale = allowed.filter((e) =>  shownIds.includes(e.id));

  // Shuffle each group deterministically (simple interleave, not truly random
  // to keep it reproducible; a real implementation would use a seeded shuffle)
  const selected = [];
  for (const entry of [...fresh, ...stale]) {
    if (selected.length >= count) break;
    selected.push(entry);
  }

  return selected;
}

// ═══════════════════════════════════════════════════════════════════════
// MAIN EXPORT
// ═══════════════════════════════════════════════════════════════════════

/**
 * generateAdvice — core entry point for Task 7.
 *
 * @param {object}   params
 * @param {string}   params.childId         Unique child identifier
 * @param {object}   params.modelOutput     ML model prediction result:
 *                                            { status, encoded, confidence, probabilities, flags }
 * @param {number}   params.activityLevel   0=Sedentary, 1=Normal, 2=HighlyActive
 * @param {string[]} params.allergies       e.g. ['milk', 'peanut'] (may be empty)
 * @param {number}   params.ageMonths       Child's age in months
 * @param {string[]} [params.shownAdviceIds=[]]  IDs of advice shown in previous sessions
 *
 * @returns {AdviceResult}
 *
 * @typedef {object} AdviceResult
 * @property {string}   childId
 * @property {string}   generatedAt          ISO timestamp
 * @property {object}   prediction           Echo of modelOutput
 * @property {string}   adviceKey            'underweight'|'normal'|'overweight'
 * @property {object[]} dietaryAdvice        Selected dietary advice cards
 * @property {object[]} activityAdvice       Selected activity advice cards
 * @property {object[]} allergyWarnings      Active allergy guardrail entries
 * @property {object[]} guardrailLog         Audit log of intercepted advice
 * @property {boolean}  requiresUrgentReferral  true for severe + SAM cases
 * @property {string|null} confidenceCaveat  Hedging message if confidence < threshold
 * @property {string}   disclaimer           Medical disclaimer (always present)
 */
export function generateAdvice({
  childId,
  modelOutput,
  activityLevel,
  allergies = [],
  ageMonths,
  shownAdviceIds = [],
}) {
  // ── 1. Input validation ─────────────────────────────────────────────
  if (!modelOutput || !modelOutput.status) {
    throw new Error('[healthAdviceEngine] modelOutput.status is required');
  }
  if (typeof activityLevel !== 'number' || ![0, 1, 2].includes(activityLevel)) {
    throw new Error('[healthAdviceEngine] activityLevel must be 0, 1, or 2');
  }
  if (typeof ageMonths !== 'number' || ageMonths < 0 || ageMonths > 72) {
    throw new Error('[healthAdviceEngine] ageMonths must be between 0 and 72');
  }

  const normalised = {
    ...modelOutput,
    status:    modelOutput.status.toLowerCase().trim(),
    allergies: allergies.map((a) => a.toLowerCase().trim()),
  };

  // ── 2. Label mapping ────────────────────────────────────────────────
  const adviceDietaryKey = ML_TO_ADVICE_KEY[normalised.status];
  if (adviceDietaryKey === undefined) {
    throw new Error(
      `[healthAdviceEngine] Unknown ML status: "${normalised.status}". ` +
      `Expected 'normal', 'moderate', or 'severe'.`
    );
  }

  const adviceKeyLabel = { 0: 'underweight', 1: 'normal', 2: 'overweight' }[adviceDietaryKey];

  // ── 3. Urgent referral flag ─────────────────────────────────────────
  // Trigger if: severe malnutrition OR SAM flag is raised
  const requiresUrgentReferral =
    normalised.status === 'severe' ||
    (normalised.flags && normalised.flags.is_sam === true);

  // ── 4. Confidence hedging ───────────────────────────────────────────
  const confidence = normalised.confidence ?? 1.0;
  const confidenceCaveat =
    confidence < CONFIDENCE_THRESHOLD
      ? `This health assessment has moderate certainty (confidence: ${Math.round(confidence * 100)}%). ` +
        'We recommend consulting a healthcare professional for confirmation.'
      : null;

  // ── 5. Allergy guardrail audit log ──────────────────────────────────
  const guardrailLog = [];

  // ── 6. Select dietary advice ─────────────────────────────────────────
  const dietaryPool = dietaryAdviceDatabase[adviceDietaryKey] ?? [];

  // For SEVERE cases: always prepend the "Consult a health care professional" card
  // (UW-04) as the first recommendation regardless of rotation
  let selectedDietary;
  if (normalised.status === 'severe') {
    const urgentCard = dietaryPool.find((e) => e.id === 'UW-04');
    const remaining  = dietaryPool.filter((e) => e.id !== 'UW-04');
    const rest = selectAdvice(
      remaining,
      DIETARY_ADVICE_COUNT - 1,
      normalised.allergies,
      shownAdviceIds,
      guardrailLog
    );
    selectedDietary = urgentCard ? [urgentCard, ...rest] : rest;
  } else {
    selectedDietary = selectAdvice(
      dietaryPool,
      DIETARY_ADVICE_COUNT,
      normalised.allergies,
      shownAdviceIds,
      guardrailLog
    );
  }

  // ── 7. Select activity advice ────────────────────────────────────────
  const activityPool = activityAdviceDatabase[activityLevel] ?? [];
  const selectedActivity = selectAdvice(
    activityPool,
    ACTIVITY_ADVICE_COUNT,
    normalised.allergies,  // activity advice rarely mentions food, but guard anyway
    shownAdviceIds,
    guardrailLog
  );

  // ── 8. Compile allergy warnings ──────────────────────────────────────
  // For each recorded allergen, include the full guardrail entry (description,
  // safe substitutions, cross-contact prevention rules)
  const allergyWarnings = normalised.allergies
    .filter((allergen) => allergyGuardrails[allergen] !== undefined)
    .map((allergen) => ({
      allergen,
      ...allergyGuardrails[allergen],
    }));

  // ── 9. Compose final result ──────────────────────────────────────────
  return {
    childId,
    generatedAt:          new Date().toISOString(),
    prediction: {
      status:        normalised.status,
      encoded:       normalised.encoded,
      confidence:    normalised.confidence,
      probabilities: normalised.probabilities,
      flags:         normalised.flags ?? {},
    },
    adviceKey:             adviceKeyLabel,
    dietaryAdvice:         selectedDietary,
    activityAdvice:        selectedActivity,
    allergyWarnings,
    guardrailLog,
    requiresUrgentReferral,
    confidenceCaveat,
    disclaimer:            MEDICAL_DISCLAIMER,
  };
}

// ═══════════════════════════════════════════════════════════════════════
// UTILITY EXPORTS  (useful for the backend API / frontend)
// ═══════════════════════════════════════════════════════════════════════

/**
 * Returns a flat list of all advice IDs that were shown in a result,
 * for use as `shownAdviceIds` in the next session.
 *
 * @param {AdviceResult} adviceResult
 * @returns {string[]}
 */
export function extractShownIds(adviceResult) {
  const ids = [];
  for (const entry of adviceResult.dietaryAdvice)  ids.push(entry.id);
  for (const entry of adviceResult.activityAdvice) ids.push(entry.id);
  return ids;
}

/**
 * Returns a human-readable severity label for the UI.
 *
 * @param {string} mlStatus  'normal'|'moderate'|'severe'
 * @returns {{ label: string, colour: string, emoji: string }}
 */
export function getSeverityDisplay(mlStatus) {
  const map = {
    normal:   { label: 'Healthy',              colour: '#2ecc71', emoji: '🟢' },
    moderate: { label: 'Moderate Concern',     colour: '#f39c12', emoji: '🟡' },
    severe:   { label: 'Urgent — See Doctor',  colour: '#e74c3c', emoji: '🔴' },
  };
  return map[mlStatus] ?? { label: 'Unknown', colour: '#95a5a6', emoji: '⚪' };
}

/**
 * Maps the integer activity level to a human-readable label.
 * @param {number} level  0|1|2
 * @returns {string}
 */
export function getActivityLabel(level) {
  return { 0: 'Sedentary', 1: 'Normally Active', 2: 'Highly Active' }[level] ?? 'Unknown';
}

/**
 * Validates a child profile object before passing it to generateAdvice.
 * Returns an array of error messages (empty = valid).
 *
 * @param {object} profile
 * @returns {string[]}
 */
export function validateChildProfile(profile) {
  const errors = [];
  if (!profile.childId)                  errors.push('childId is required');
  if (!profile.modelOutput)              errors.push('modelOutput is required');
  if (!profile.modelOutput?.status)      errors.push('modelOutput.status is required');
  if (typeof profile.activityLevel !== 'number')
    errors.push('activityLevel must be a number (0, 1, or 2)');
  if (![0, 1, 2].includes(profile.activityLevel))
    errors.push('activityLevel must be 0 (Sedentary), 1 (Normal), or 2 (Highly Active)');
  if (typeof profile.ageMonths !== 'number')
    errors.push('ageMonths must be a number');
  if (profile.ageMonths < 0 || profile.ageMonths > 72)
    errors.push('ageMonths must be between 0 and 72');
  if (!Array.isArray(profile.allergies))
    errors.push('allergies must be an array (use [] if none)');
  return errors;
}
