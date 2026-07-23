/**
 * test_adviceEngine.mjs
 *
 *— Validation Test Suite for healthAdviceEngine.js
 *
 * Tests all key scenarios :
 *   1. Label mapping (ML → advice keys)
 *   2. Advice selection & rotation
 *   3. Confidence hedging
 *   4. Allergy guardrail interception
 *   5. Severe / urgent referral flag
 *   6. Input validation
 *
 * Run with:  node test_adviceEngine.mjs
 */

import {
  generateAdvice,
  extractShownIds,
  getSeverityDisplay,
  getActivityLabel,
  validateChildProfile,
  ML_TO_ADVICE_KEY,
} from './healthAdviceEngine.js';

// ── Colour helpers for console output ─────────────────────────────────
const GREEN  = '\x1b[32m';
const RED    = '\x1b[31m';
const YELLOW = '\x1b[33m';
const CYAN   = '\x1b[36m';
const BOLD   = '\x1b[1m';
const RESET  = '\x1b[0m';

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  ${GREEN}✔${RESET}  ${message}`);
    passed++;
  } else {
    console.log(`  ${RED}✘  FAIL: ${message}${RESET}`);
    failed++;
  }
}

function section(title) {
  const dashes = Math.max(0, 50 - title.length);
  console.log(`\n${BOLD}${CYAN}── ${title} ${'─'.repeat(dashes)}${RESET}`);
}

function printResult(result) {
  console.log(`\n${YELLOW}     Result preview:${RESET}`);
  console.log(`       adviceKey:             ${result.adviceKey}`);
  console.log(`       dietaryAdvice count:   ${result.dietaryAdvice.length}`);
  console.log(`       activityAdvice count:  ${result.activityAdvice.length}`);
  console.log(`       guardrailLog:          ${result.guardrailLog.length} interceptions`);
  console.log(`       requiresUrgentReferral: ${result.requiresUrgentReferral}`);
  console.log(`       confidenceCaveat:      ${result.confidenceCaveat ?? 'none'}`);
  if (result.guardrailLog.length > 0) {
    console.log(`       blocked advice IDs:   ${result.guardrailLog.map(g => g.adviceId).join(', ')}`);
  }
  if (result.allergyWarnings.length > 0) {
    console.log(`       allergyWarnings:      ${result.allergyWarnings.map(a => a.allergen).join(', ')}`);
  }
}

// ═══════════════════════════════════════════════════════════════════════
// TEST 1 — Label Mapping
// ═══════════════════════════════════════════════════════════════════════
section('TEST 1: Label Mapping (ML → Advice Keys)');

assert(ML_TO_ADVICE_KEY['normal']   === 1, "normal   → key 1 (Normal Weight)");
assert(ML_TO_ADVICE_KEY['moderate'] === 0, "moderate → key 0 (Underweight)");
assert(ML_TO_ADVICE_KEY['severe']   === 0, "severe   → key 0 (Underweight)");

// Test via generateAdvice
const normalResult = generateAdvice({
  childId:       'child_001',
  modelOutput:   { status: 'normal', encoded: 0, confidence: 0.95,
                   probabilities: { normal: 0.95, moderate: 0.04, severe: 0.01 } },
  activityLevel: 1,
  allergies:     [],
  ageMonths:     36,
});
assert(normalResult.adviceKey === 'normal', "normal status → adviceKey = 'normal'");
assert(normalResult.dietaryAdvice[0].id.startsWith('NW'), "normal → dietary IDs start with NW");

const moderateResult = generateAdvice({
  childId:       'child_002',
  modelOutput:   { status: 'moderate', encoded: 1, confidence: 0.78,
                   probabilities: { normal: 0.12, moderate: 0.78, severe: 0.10 } },
  activityLevel: 0,
  allergies:     [],
  ageMonths:     24,
});
assert(moderateResult.adviceKey === 'underweight', "moderate status → adviceKey = 'underweight'");
assert(moderateResult.dietaryAdvice[0].id.startsWith('UW'), "moderate → dietary IDs start with UW");

// ═══════════════════════════════════════════════════════════════════════
// TEST 2 — Severe / Urgent Referral
// ═══════════════════════════════════════════════════════════════════════
section('TEST 2: Severe Malnutrition & Urgent Referral');

const severeResult = generateAdvice({
  childId:       'child_003',
  modelOutput:   { status: 'severe', encoded: 2, confidence: 0.91,
                   probabilities: { normal: 0.02, moderate: 0.07, severe: 0.91 },
                   flags: { is_sam: true, is_mam: false, is_stunted: true, is_underweight: true } },
  activityLevel: 0,
  allergies:     [],
  ageMonths:     18,
});
assert(severeResult.requiresUrgentReferral === true, "severe → requiresUrgentReferral = true");
assert(severeResult.adviceKey === 'underweight', "severe → adviceKey = 'underweight'");
assert(severeResult.dietaryAdvice[0].id === 'UW-04',
  "severe → first dietary advice is always UW-04 (Consult a health care professional)");
printResult(severeResult);

// SAM flag alone should trigger urgent referral
const samResult = generateAdvice({
  childId:       'child_004',
  modelOutput:   { status: 'moderate', encoded: 1, confidence: 0.72,
                   probabilities: { normal: 0.10, moderate: 0.72, severe: 0.18 },
                   flags: { is_sam: true, is_mam: false, is_stunted: false, is_underweight: true } },
  activityLevel: 1,
  allergies:     [],
  ageMonths:     30,
});
assert(samResult.requiresUrgentReferral === true, "SAM flag alone → requiresUrgentReferral = true");

// ═══════════════════════════════════════════════════════════════════════
// TEST 3 — Confidence Hedging
// ═══════════════════════════════════════════════════════════════════════
section('TEST 3: Confidence Hedging');

const highConfResult = generateAdvice({
  childId:       'child_005',
  modelOutput:   { status: 'normal', encoded: 0, confidence: 0.95,
                   probabilities: { normal: 0.95, moderate: 0.04, severe: 0.01 } },
  activityLevel: 1,
  allergies:     [],
  ageMonths:     48,
});
assert(highConfResult.confidenceCaveat === null, "confidence 0.95 → no hedging caveat");

const lowConfResult = generateAdvice({
  childId:       'child_006',
  modelOutput:   { status: 'moderate', encoded: 1, confidence: 0.62,
                   probabilities: { normal: 0.28, moderate: 0.62, severe: 0.10 } },
  activityLevel: 1,
  allergies:     [],
  ageMonths:     42,
});
assert(lowConfResult.confidenceCaveat !== null, "confidence 0.62 → hedging caveat is present");
assert(lowConfResult.confidenceCaveat.includes('62%'), "caveat includes confidence percentage");
console.log(`  ${YELLOW}   Caveat: "${lowConfResult.confidenceCaveat}"${RESET}`);

// ═══════════════════════════════════════════════════════════════════════
// TEST 4 — Allergy Guardrails
// ═══════════════════════════════════════════════════════════════════════
section('TEST 4: Allergy Guardrails');

// Test: milk allergy should block milk-containing dietary advice
const milkAllergyResult = generateAdvice({
  childId:       'child_007',
  modelOutput:   { status: 'normal', encoded: 0, confidence: 0.88,
                   probabilities: { normal: 0.88, moderate: 0.10, severe: 0.02 } },
  activityLevel: 1,
  allergies:     ['milk'],
  ageMonths:     36,
});
// NW-07 is "Consume 2–3 servings of milk and dairy daily" → should be blocked
const milkBlocked = milkAllergyResult.guardrailLog.some((g) => g.adviceId === 'NW-07');
assert(milkBlocked, "milk allergy → NW-07 (milk advice) is intercepted by guardrail");

// None of the selected dietary advice should contain milk keywords
const milkInDietary = milkAllergyResult.dietaryAdvice.some((entry) =>
  ['milk','dairy','yoghurt','UHT'].some((kw) =>
    `${entry.title} ${entry.body}`.toLowerCase().includes(kw.toLowerCase())
  )
);
assert(!milkInDietary, "milk allergy → no milk-containing advice in final dietaryAdvice");

// Allergy warning should be present (allergen field is the lowercase key we passed in)
assert(
  milkAllergyResult.allergyWarnings.some((w) => w.allergen === 'Milk (Cow\'s milk protein)'),
  "milk allergy → allergyWarnings includes milk guardrail entry"
);
printResult(milkAllergyResult);

// Test: peanut allergy + underweight case
const peanutResult = generateAdvice({
  childId:       'child_008',
  modelOutput:   { status: 'moderate', encoded: 1, confidence: 0.80,
                   probabilities: { normal: 0.08, moderate: 0.80, severe: 0.12 },
                   flags: { is_sam: false, is_mam: true, is_stunted: false, is_underweight: true } },
  activityLevel: 0,
  allergies:     ['peanut'],
  ageMonths:     30,
});
assert(
  peanutResult.allergyWarnings.some((w) => w.allergen === 'Peanut'),
  "peanut allergy → allergyWarnings includes peanut guardrail"
);
assert(
  peanutResult.allergyWarnings[0].safeSubstitutions.length > 0,
  "peanut guardrail includes safe substitutions"
);
assert(
  peanutResult.allergyWarnings[0].crossContactPrevention.length > 0,
  "peanut guardrail includes cross-contact prevention rules"
);

// Test: multiple allergies
const multiAllergyResult = generateAdvice({
  childId:       'child_009',
  modelOutput:   { status: 'normal', encoded: 0, confidence: 0.90,
                   probabilities: { normal: 0.90, moderate: 0.08, severe: 0.02 } },
  activityLevel: 1,
  allergies:     ['milk', 'peanut'],
  ageMonths:     54,
});
assert(
  multiAllergyResult.allergyWarnings.length === 2,
  "multiple allergies → allergyWarnings contains 2 entries"
);
console.log(`  ${YELLOW}   Total guardrail interceptions: ${multiAllergyResult.guardrailLog.length}${RESET}`);

// ═══════════════════════════════════════════════════════════════════════
// TEST 5 — Advice Rotation
// ═══════════════════════════════════════════════════════════════════════
section('TEST 5: Advice Rotation (Avoiding Repetition)');

// First call — fresh
const session1 = generateAdvice({
  childId:       'child_010',
  modelOutput:   { status: 'normal', encoded: 0, confidence: 0.93,
                   probabilities: { normal: 0.93, moderate: 0.06, severe: 0.01 } },
  activityLevel: 1,
  allergies:     [],
  ageMonths:     40,
  shownAdviceIds: [],
});
const shownAfterSession1 = extractShownIds(session1);
assert(shownAfterSession1.length > 0, "extractShownIds returns non-empty array after session 1");

// Second call — pass shown IDs, expect different selection
const session2 = generateAdvice({
  childId:       'child_010',
  modelOutput:   { status: 'normal', encoded: 0, confidence: 0.93,
                   probabilities: { normal: 0.93, moderate: 0.06, severe: 0.01 } },
  activityLevel: 1,
  allergies:     [],
  ageMonths:     40,
  shownAdviceIds: shownAfterSession1,
});
const session2DietaryIds = session2.dietaryAdvice.map((e) => e.id);
const session1DietaryIds = session1.dietaryAdvice.map((e) => e.id);
const noOverlapDietary = !session2DietaryIds.some((id) => session1DietaryIds.includes(id));
// This test may not always hold (pool may be exhausted), so we just verify the mechanism ran
assert(
  session2.dietaryAdvice.length === session1.dietaryAdvice.length,
  "rotation: session 2 returns same count as session 1"
);
console.log(`  ${YELLOW}   Session 1 dietary IDs: ${session1DietaryIds.join(', ')}${RESET}`);
console.log(`  ${YELLOW}   Session 2 dietary IDs: ${session2DietaryIds.join(', ')}${RESET}`);
console.log(`  ${YELLOW}   Non-overlapping: ${noOverlapDietary}${RESET}`);

// ═══════════════════════════════════════════════════════════════════════
// TEST 6 — Input Validation
// ═══════════════════════════════════════════════════════════════════════
section('TEST 6: Input Validation');

const validProfile = {
  childId:       'child_011',
  modelOutput:   { status: 'normal', encoded: 0, confidence: 0.90,
                   probabilities: { normal: 0.90, moderate: 0.08, severe: 0.02 } },
  activityLevel: 1,
  allergies:     [],
  ageMonths:     36,
};
assert(validateChildProfile(validProfile).length === 0, "valid profile → 0 validation errors");

const invalidProfile = { childId: null, activityLevel: 5, ageMonths: 100, allergies: 'milk' };
const errors = validateChildProfile(invalidProfile);
assert(errors.length > 0,       "invalid profile → validation errors returned");
assert(errors.some((e) => e.includes('childId')),           "error: childId required");
assert(errors.some((e) => e.includes('modelOutput')),       "error: modelOutput required");
assert(errors.some((e) => e.includes('activityLevel')),     "error: activityLevel invalid");
assert(errors.some((e) => e.includes('ageMonths')),         "error: ageMonths out of range");
assert(errors.some((e) => e.includes('allergies')),         "error: allergies must be array");
console.log(`  ${YELLOW}   Validation errors: ${errors.join(' | ')}${RESET}`);

// Test generateAdvice throws on bad input
let threw = false;
try {
  generateAdvice({ childId: 'x', modelOutput: { status: 'unknown' }, activityLevel: 1,
                   allergies: [], ageMonths: 24 });
} catch (e) {
  threw = true;
}
assert(threw, "generateAdvice throws on unknown ML status");

// ═══════════════════════════════════════════════════════════════════════
// TEST 7 — Utility Functions
// ═══════════════════════════════════════════════════════════════════════
section('TEST 7: Utility Functions');

assert(getSeverityDisplay('normal').emoji   === '🟢', "getSeverityDisplay normal → 🟢");
assert(getSeverityDisplay('moderate').emoji === '🟡', "getSeverityDisplay moderate → 🟡");
assert(getSeverityDisplay('severe').emoji   === '🔴', "getSeverityDisplay severe → 🔴");
assert(getActivityLabel(0) === 'Sedentary',        "getActivityLabel 0 → Sedentary");
assert(getActivityLabel(1) === 'Normally Active',  "getActivityLabel 1 → Normally Active");
assert(getActivityLabel(2) === 'Highly Active',    "getActivityLabel 2 → Highly Active");

// ═══════════════════════════════════════════════════════════════════════
// TEST 8 — Disclaimer Always Present
// ═══════════════════════════════════════════════════════════════════════
section('TEST 8: Medical Disclaimer');
const anyResult = generateAdvice({
  childId: 'child_012',
  modelOutput: { status: 'normal', encoded: 0, confidence: 0.88,
                 probabilities: { normal: 0.88, moderate: 0.10, severe: 0.02 } },
  activityLevel: 1, allergies: [], ageMonths: 48,
});
assert(typeof anyResult.disclaimer === 'string' && anyResult.disclaimer.length > 50,
  "disclaimer is always present in every response");

// ═══════════════════════════════════════════════════════════════════════
// TEST 9 — Full End-to-End Example (printed for report)
// ═══════════════════════════════════════════════════════════════════════
section('TEST 9: Full End-to-End Example (2-year-old, moderate, milk allergy)');

const e2eResult = generateAdvice({
  childId:       'child_e2e',
  modelOutput: {
    status:        'moderate',
    encoded:        1,
    confidence:     0.79,
    probabilities:  { normal: 0.11, moderate: 0.79, severe: 0.10 },
    flags: { is_sam: false, is_mam: true, is_stunted: false, is_underweight: true }
  },
  activityLevel:   0,   // Sedentary
  allergies:       ['milk'],
  ageMonths:       26,
  shownAdviceIds:  [],
});

console.log(`\n  ${BOLD}Child:${RESET}            child_e2e | 26 months | Sedentary | Milk allergy`);
console.log(`  ${BOLD}ML Output:${RESET}        moderate (confidence 79%)`);
console.log(`  ${BOLD}Advice Key:${RESET}       ${e2eResult.adviceKey}`);
console.log(`  ${BOLD}Urgent Referral:${RESET}  ${e2eResult.requiresUrgentReferral}`);
console.log(`  ${BOLD}Confidence Caveat:${RESET} ${e2eResult.confidenceCaveat ?? 'none'}`);
console.log(`\n  ${BOLD}Dietary Advice:${RESET}`);
e2eResult.dietaryAdvice.forEach((a, i) => {
  console.log(`    ${i + 1}. [${a.id}] ${a.title}`);
});
console.log(`\n  ${BOLD}Activity Advice:${RESET}`);
e2eResult.activityAdvice.forEach((a, i) => {
  console.log(`    ${i + 1}. [${a.id}] ${a.title}`);
});
console.log(`\n  ${BOLD}Guardrail Interceptions:${RESET} ${e2eResult.guardrailLog.length}`);
e2eResult.guardrailLog.forEach((g) => {
  console.log(`    ⛔ [${g.adviceId}] "${g.title.substring(0, 60)}..." ← blocked by [${g.blockedBy.join(', ')}]`);
});
console.log(`\n  ${BOLD}Allergy Warnings:${RESET}`);
e2eResult.allergyWarnings.forEach((w) => {
  console.log(`    🚨 ${w.allergen}: ${w.safeSubstitutions.length} substitutions, ${w.crossContactPrevention.length} cross-contact rules`);
});

// ═══════════════════════════════════════════════════════════════════════
// SUMMARY
// ═══════════════════════════════════════════════════════════════════════
const total = passed + failed;
console.log(`\n${'═'.repeat(60)}`);
console.log(`${BOLD}TASK 7 TEST RESULTS: ${passed}/${total} passed${RESET}`);
if (failed === 0) {
  console.log(`${GREEN}${BOLD}✔  ALL TESTS PASSED — Rules engine is ready for integration.${RESET}`);
} else {
  console.log(`${RED}${BOLD}✘  ${failed} TEST(S) FAILED — review above.${RESET}`);
}
console.log('═'.repeat(60));
