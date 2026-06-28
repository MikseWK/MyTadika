import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('health');
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();

const params = new URLSearchParams(location.search);
const studentId = params.get('id') || localStorage.getItem('selectedStudentId') || localStorage.getItem('selectedChildId');

const isTeacher = profile.role === 'TEACHER' || profile.role === 'ADMIN';

// ── Helpers ──────────────────────────────────────────────────────────────────

function nutritionColor(status) {
  if (!status) return '#857668';
  const s = status.toLowerCase();
  if (s === 'normal') return '#4CAF50';
  if (s === 'moderate') return '#FF9F43';
  return '#FF6B6B';
}

function nutritionBg(status) {
  if (!status) return '#F7F2E9';
  const s = status.toLowerCase();
  if (s === 'normal') return 'rgba(76,175,80,.15)';
  if (s === 'moderate') return 'rgba(255,159,67,.15)';
  return 'rgba(255,107,107,.15)';
}

function formatDate(dt) {
  if (!dt) return '—';
  return new Date(dt).toLocaleDateString('en-MY', { day: 'numeric', month: 'short', year: 'numeric' });
}

// ── Allergy tag UI helpers ────────────────────────────────────────────────────

let currentAllergies = [];

function renderAllergyTags(containerId, allergies, removable = true) {
  const el = document.getElementById(containerId);
  if (!el) return;
  if (allergies.length === 0) {
    el.innerHTML = '<span class="text-xs text-ink-muted">None</span>';
    return;
  }
  el.innerHTML = allergies.map(a => `
    <span class="flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold text-white"
      style="background:#b02500">
      ${a}
      ${removable ? `<button type="button" class="ml-1 opacity-70 hover:opacity-100 remove-tag" data-val="${a}">×</button>` : ''}
    </span>
  `).join('');
  if (removable) {
    el.querySelectorAll('.remove-tag').forEach(btn => {
      btn.addEventListener('click', () => {
        const val = btn.dataset.val;
        currentAllergies = currentAllergies.filter(x => x !== val);
        renderAllergyTags(containerId, currentAllergies);
      });
    });
  }
}

function setupAllergyInput(inputId, addBtnId, tagsId) {
  const input = document.getElementById(inputId);
  const btn = document.getElementById(addBtnId);
  const addTag = () => {
    const val = input.value.trim();
    if (val && !currentAllergies.includes(val)) {
      currentAllergies = [...currentAllergies, val];
      renderAllergyTags(tagsId, currentAllergies);
    }
    input.value = '';
  };
  input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } });
  btn?.addEventListener('click', addTag);
}

// ── Chart ────────────────────────────────────────────────────────────────────

let growthChart = null;

function renderGrowthChart(records) {
  const chartEl = document.getElementById('chart-container');
  const emptyEl = document.getElementById('chart-empty');
  if (records.length === 0) {
    emptyEl.classList.remove('hidden');
    return;
  }
  chartEl.classList.remove('hidden');
  const sorted = [...records].reverse(); // oldest first
  const labels = sorted.map(r => formatDate(r.recordedAt));
  if (growthChart) growthChart.destroy();
  const ctx = document.getElementById('growth-chart').getContext('2d');
  growthChart = new Chart(ctx, {
    type: 'line',
    data: {
      labels,
      datasets: [
        {
          label: 'BMI',
          data: sorted.map(r => parseFloat(r.bmi?.toFixed(1))),
          borderColor: '#E6C200',
          backgroundColor: 'rgba(255,215,0,0.12)',
          borderWidth: 2.5,
          tension: 0.4,
          pointBackgroundColor: '#4A3F35',
          pointRadius: 4,
          fill: true,
          yAxisID: 'y',
        },
        {
          label: 'Weight (kg)',
          data: sorted.map(r => r.weightKg),
          borderColor: '#FF8C42',
          backgroundColor: 'rgba(255,140,66,0.08)',
          borderWidth: 2,
          tension: 0.4,
          pointBackgroundColor: '#FF8C42',
          pointRadius: 4,
          fill: false,
          yAxisID: 'y1',
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      scales: {
        y: {
          type: 'linear',
          position: 'left',
          title: { display: true, text: 'BMI', font: { family: 'Plus Jakarta Sans', size: 10 } },
          ticks: { font: { family: 'Plus Jakarta Sans', size: 10 } },
          grid: { color: 'rgba(0,0,0,.05)' },
        },
        y1: {
          type: 'linear',
          position: 'right',
          title: { display: true, text: 'Weight (kg)', font: { family: 'Plus Jakarta Sans', size: 10 } },
          ticks: { font: { family: 'Plus Jakarta Sans', size: 10 } },
          grid: { drawOnChartArea: false },
        },
        x: { ticks: { font: { family: 'Plus Jakarta Sans', size: 10 } }, grid: { color: 'rgba(0,0,0,.04)' } },
      },
      plugins: { legend: { display: false } },
    },
  });
}

// ── Advice rendering ─────────────────────────────────────────────────────────

function renderAdvice(advice) {
  document.getElementById('advice-loading').classList.add('hidden');
  document.getElementById('advice-empty').classList.add('hidden');

  if (!advice) {
    document.getElementById('advice-empty').classList.remove('hidden');
    return;
  }

  document.getElementById('advice-content').classList.remove('hidden');

  // Status badge
  const badge = document.getElementById('advice-status-badge');
  const status = advice.status ?? 'unknown';
  badge.textContent = status.charAt(0).toUpperCase() + status.slice(1);
  badge.style.background = nutritionBg(status);
  badge.style.color = nutritionColor(status);

  // Urgent referral
  if (advice.requiresUrgentReferral) {
    document.getElementById('urgent-banner').classList.remove('hidden');
  }

  // Allergy warnings
  const allergyWarnings = advice.allergyWarnings ?? [];
  if (allergyWarnings.length > 0) {
    const section = document.getElementById('advice-allergy-section');
    const list = document.getElementById('advice-allergy-list');
    section.classList.remove('hidden');
    list.innerHTML = allergyWarnings.map(w => `
      <div class="rounded-xl p-3 text-xs" style="background:#fff5f5;border:1px solid #fce4ec">
        <p class="font-bold" style="color:#b02500">${w.allergen?.toUpperCase()} ALLERGY</p>
        <p class="text-ink-muted mt-1">${w.description ?? ''}</p>
      </div>
    `).join('');
  }

  // Dietary advice
  const dietary = advice.dietaryAdvice ?? [];
  if (dietary.length > 0) {
    document.getElementById('advice-dietary-section').classList.remove('hidden');
    document.getElementById('advice-dietary-list').innerHTML = dietary.map(c => `
      <div class="rounded-xl p-3 bg-surface-high border border-outline">
        <p class="font-bold text-xs text-ink">${c.title}</p>
        <p class="text-xs text-ink-muted mt-1 leading-relaxed">${c.body}</p>
      </div>
    `).join('');
  }

  // Activity advice
  const activity = advice.activityAdvice ?? [];
  if (activity.length > 0) {
    document.getElementById('advice-activity-section').classList.remove('hidden');
    document.getElementById('advice-activity-list').innerHTML = activity.map(c => `
      <div class="rounded-xl p-3 border border-outline" style="background:rgba(76,175,80,.05)">
        <p class="font-bold text-xs text-ink">${c.title}</p>
        <p class="text-xs text-ink-muted mt-1 leading-relaxed">${c.body}</p>
      </div>
    `).join('');
  }

  // Caveat
  if (advice.confidenceCaveat) {
    const caveat = document.getElementById('advice-caveat');
    caveat.textContent = advice.confidenceCaveat;
    caveat.classList.remove('hidden');
  }

  // Disclaimer
  if (advice.disclaimer) {
    document.getElementById('advice-disclaimer').textContent = advice.disclaimer;
  }
}

// ── History timeline ──────────────────────────────────────────────────────────

function renderHistory(records) {
  const container = document.getElementById('history-container');
  if (records.length === 0) {
    container.innerHTML = '<div class="text-center py-6 text-ink-muted text-sm">No measurements logged yet.</div>';
    return;
  }
  container.innerHTML = `
    <div class="relative border-l-2 border-outline ml-3 space-y-6 pb-2">
      ${records.map((r, i) => {
        const isLatest = i === 0;
        const color = nutritionColor(r.nutritionStatus);
        return `<div class="relative pl-8">
          <div class="absolute -left-[9px] top-1 w-4 h-4 rounded-full border-2 border-surface"
            style="background:${isLatest ? '#FFD700' : '#E8E2D9'}"></div>
          <p class="text-xs font-bold mb-1" style="color:${isLatest ? '#E6C200' : '#857668'}">${formatDate(r.recordedAt)}</p>
          <div class="flex flex-wrap gap-3 text-sm text-ink">
            <span><strong>${r.weightKg} kg</strong> · <strong>${r.heightCm} cm</strong> · BMI <strong>${r.bmi?.toFixed(1)}</strong></span>
            <span class="px-2 py-0.5 rounded-full text-xs font-bold" style="background:${nutritionBg(r.nutritionStatus)};color:${color}">
              ${r.nutritionStatus ?? 'Unknown'}
            </span>
          </div>
        </div>`;
      }).join('')}
    </div>
  `;
}

// ── Main load ─────────────────────────────────────────────────────────────────

if (!studentId) {
  const backHref = profile.role === 'PARENT' ? 'dashboard-parent.html' : 'students.html';
  document.getElementById('loading-state').innerHTML =
    `<p class="text-danger font-medium">No student selected.
    <a href="${backHref}" class="underline font-bold">${profile.role === 'PARENT' ? 'Go to Dashboard' : 'Go to Students'}</a>
    and click a student to view their health record.</p>`;
} else {
  try {
    const [studentRes, allergyRes, historyRes] = await Promise.all([
      api.get(`/students/${studentId}`),
      api.get(`/health/allergies/${studentId}`),
      api.get(`/health/history/${studentId}`),
    ]);
    const student = studentRes.data;
    const allergies = allergyRes.data ?? [];
    const history = historyRes.data ?? [];

    currentAllergies = [...allergies];

    document.getElementById('loading-state').classList.add('hidden');
    document.getElementById('page-content').classList.remove('hidden');

    // Parent: show child switcher when they have more than one child
    if (profile.role === 'PARENT') {
      try {
        const { data: children } = await api.get('/students/my-children');
        if (children.length > 1) {
          const switcher = document.getElementById('child-switcher');
          children.forEach(c => {
            const btn = document.createElement('button');
            const isActive = String(c.id) === String(studentId);
            btn.textContent = c.fullName;
            btn.className = `px-4 py-1.5 rounded-full text-sm font-bold transition-colors ${
              isActive ? 'bg-primary text-ink' : 'bg-surface-high text-ink-muted hover:bg-primary/20'
            }`;
            btn.addEventListener('click', () => {
              localStorage.setItem('selectedChildId', c.id);
              location.href = `health.html?id=${c.id}`;
            });
            switcher.appendChild(btn);
          });
          switcher.classList.remove('hidden');
          switcher.classList.add('flex');
        }
      } catch { /* switcher is a convenience, not critical */ }
    }

    // Student header
    const initials = student.fullName?.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase() ?? '?';
    document.getElementById('student-avatar').textContent = initials;
    document.getElementById('student-name').textContent = student.fullName;
    const age = student.dateOfBirth
      ? Math.floor((Date.now() - new Date(student.dateOfBirth)) / 31_557_600_000)
      : null;
    document.getElementById('student-meta').textContent =
      `${student.className ?? 'No class'} · ${student.gender ?? '—'}${age != null ? ` · ${age} yrs` : ''}`;

    // Allergy banner
    if (allergies.length > 0) {
      document.getElementById('allergy-banner').classList.remove('hidden');
      document.getElementById('allergy-banner-text').textContent =
        `This student has known allergies: ${allergies.join(', ')}. Please ensure safe food handling.`;
    }

    // Latest record stats
    if (history.length > 0) {
      const latest = history[0];
      document.getElementById('stat-bmi').textContent = latest.bmi?.toFixed(1) ?? '—';
      document.getElementById('stat-weight').textContent = `${latest.weightKg} kg`;
      document.getElementById('stat-height').textContent = `${latest.heightCm} cm`;
      const badge = document.getElementById('nutrition-badge');
      badge.textContent = latest.nutritionStatus ?? 'Unknown';
      badge.style.background = nutritionBg(latest.nutritionStatus);
      badge.style.color = nutritionColor(latest.nutritionStatus);
      badge.classList.remove('hidden');
    }

    // Growth chart
    renderGrowthChart(history);

    // History timeline
    renderHistory(history);

    // Advice
    if (history.length > 0) {
      try {
        const adviceRes = await api.get(`/health/advice/${studentId}`);
        renderAdvice(adviceRes.data);
      } catch {
        document.getElementById('advice-loading').classList.add('hidden');
        document.getElementById('advice-empty').classList.remove('hidden');
        document.getElementById('advice-empty').textContent = 'Could not load advice.';
      }
    } else {
      document.getElementById('advice-loading').classList.add('hidden');
      document.getElementById('advice-empty').classList.remove('hidden');
    }

    // TEACHER-only: show log form + manage allergies + generate button
    if (isTeacher) {
      document.getElementById('log-section').classList.remove('hidden');
      document.getElementById('allergy-manage-section').classList.remove('hidden');
      document.getElementById('generate-advice-btn').classList.remove('hidden');

      // Sync form allergy tags with existing allergies
      renderAllergyTags('allergy-tags', currentAllergies);
      setupAllergyInput('allergy-input', 'add-allergy-btn', 'allergy-tags');

      // Manage allergy panel (separate copy of current allergies for the side panel)
      let manageAllergies = [...allergies];
      const renderManageTags = () => {
        const el = document.getElementById('manage-allergy-tags');
        if (!el) return;
        if (manageAllergies.length === 0) {
          el.innerHTML = '<span class="text-xs text-ink-muted">No allergies on file</span>';
          return;
        }
        el.innerHTML = manageAllergies.map(a => `
          <span class="flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold text-white"
            style="background:#b02500">
            ${a}
            <button type="button" class="ml-1 opacity-70 hover:opacity-100 rm-manage" data-val="${a}">×</button>
          </span>
        `).join('');
        el.querySelectorAll('.rm-manage').forEach(btn => {
          btn.addEventListener('click', () => {
            manageAllergies = manageAllergies.filter(x => x !== btn.dataset.val);
            renderManageTags();
          });
        });
      };
      renderManageTags();

      const manageInput = document.getElementById('manage-allergy-input');
      const addManage = () => {
        const v = manageInput.value.trim();
        if (v && !manageAllergies.includes(v)) { manageAllergies.push(v); renderManageTags(); }
        manageInput.value = '';
      };
      manageInput.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); addManage(); } });
      document.getElementById('manage-add-btn').addEventListener('click', addManage);
      document.getElementById('save-allergies-btn').addEventListener('click', async () => {
        try {
          await api.put(`/health/allergies/${studentId}`, { allergies: manageAllergies });
          const ok = document.getElementById('allergy-save-ok');
          ok.textContent = 'Allergy profile saved.';
          ok.classList.remove('hidden');
          setTimeout(() => ok.classList.add('hidden'), 3000);
          // Refresh banner
          currentAllergies = [...manageAllergies];
          if (manageAllergies.length > 0) {
            document.getElementById('allergy-banner').classList.remove('hidden');
            document.getElementById('allergy-banner-text').textContent =
              `This student has known allergies: ${manageAllergies.join(', ')}.`;
          } else {
            document.getElementById('allergy-banner').classList.add('hidden');
          }
        } catch {
          const ok = document.getElementById('allergy-save-ok');
          ok.className = 'error-banner mt-2';
          ok.textContent = 'Failed to save allergies.';
          ok.classList.remove('hidden');
        }
      });

      // Generate advice button
      document.getElementById('generate-advice-btn').addEventListener('click', async () => {
        const btn = document.getElementById('generate-advice-btn');
        btn.disabled = true;
        btn.innerHTML = '<span class="material-symbols-outlined text-base animate-spin">progress_activity</span> Thinking…';
        document.getElementById('advice-content').classList.add('hidden');
        document.getElementById('advice-loading').classList.remove('hidden');
        try {
          const res = await api.get(`/health/advice/${studentId}`);
          renderAdvice(res.data);
        } catch {
          document.getElementById('advice-loading').classList.add('hidden');
          document.getElementById('advice-empty').classList.remove('hidden');
          document.getElementById('advice-empty').textContent = 'Could not generate advice.';
        }
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined text-base">auto_awesome</span> Generate';
      });

      // Log form submit
      document.getElementById('log-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const errEl = document.getElementById('log-error');
        const okEl = document.getElementById('log-success');
        const btn = document.getElementById('log-btn');

        errEl.classList.add('hidden');
        okEl.classList.add('hidden');
        btn.disabled = true;
        btn.innerHTML = '<span class="material-symbols-outlined animate-spin">progress_activity</span> Saving…';

        const heightCm = parseFloat(document.getElementById('input-height').value);
        const weightKg = parseFloat(document.getElementById('input-weight').value);
        const muacRaw = document.getElementById('input-muac').value;
        const muacCm = muacRaw ? parseFloat(muacRaw) : null;
        const activityLevel = parseInt(document.getElementById('input-activity').value, 10);
        const ageMonths = student.dateOfBirth
          ? Math.floor((Date.now() - new Date(student.dateOfBirth)) / (1000 * 60 * 60 * 24 * 30.44))
          : 0;

        if (!heightCm || !weightKg) {
          errEl.textContent = 'Please enter valid height and weight values.';
          errEl.classList.remove('hidden');
          btn.disabled = false;
          btn.innerHTML = '<span class="material-symbols-outlined text-base">save</span> Save &amp; Get AI Advice';
          return;
        }

        try {
          const body = {
            childId: String(studentId),
            ageMonths,
            weightKg,
            heightCm,
            muacCm,
            gender: student.gender ?? 'MALE',
            activityLevel,
            allergies: currentAllergies,
            shownAdviceIds: [],
          };
          const { data } = await api.post('/health/record', body);

          // Update stats
          const rec = data.healthRecord;
          document.getElementById('stat-bmi').textContent = rec.bmi?.toFixed(1) ?? '—';
          document.getElementById('stat-weight').textContent = `${rec.weightKg} kg`;
          document.getElementById('stat-height').textContent = `${rec.heightCm} cm`;
          const badge = document.getElementById('nutrition-badge');
          badge.textContent = rec.nutritionStatus ?? 'Unknown';
          badge.style.background = nutritionBg(rec.nutritionStatus);
          badge.style.color = nutritionColor(rec.nutritionStatus);
          badge.classList.remove('hidden');

          // Refresh history
          const newHistory = [rec, ...history];
          renderHistory(newHistory);
          renderGrowthChart(newHistory);

          // Show advice
          document.getElementById('advice-content').classList.add('hidden');
          document.getElementById('advice-empty').classList.add('hidden');
          document.getElementById('advice-loading').classList.remove('hidden');
          renderAdvice(data.advice);

          okEl.textContent = `Measurement saved! BMI: ${rec.bmi?.toFixed(1)} · Status: ${rec.nutritionStatus}`;
          okEl.classList.remove('hidden');
          document.getElementById('log-form').reset();
          currentAllergies = manageAllergies.length > 0 ? [...manageAllergies] : [...currentAllergies];
          renderAllergyTags('allergy-tags', currentAllergies);
        } catch (err) {
          errEl.textContent = err.response?.data?.message ?? 'Failed to save measurement. Please check your values.';
          errEl.classList.remove('hidden');
        }
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined text-base">save</span> Save &amp; Get AI Advice';
      });
    }

  } catch (err) {
    document.getElementById('loading-state').innerHTML = `
      <p class="font-medium" style="color:#FF6B6B">
        Failed to load health data. ${err.response?.status === 403 ? 'Access denied.' : 'Please try again.'}
      </p>`;
  }
}
