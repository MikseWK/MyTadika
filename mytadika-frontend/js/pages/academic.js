import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('academic');
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();

const params = new URLSearchParams(location.search);
const studentId = params.get('id') || localStorage.getItem('selectedStudentId') || localStorage.getItem('selectedChildId');

const loadingEl = document.getElementById('loading-state');
const reportEl = document.getElementById('report-section');
const noRecordsEl = document.getElementById('no-records');
const termSelect = document.getElementById('term-select');

let allRecords = [];
let radarChart = null;



// Grade colour helper
function gradeColor(grade) {
  if (!grade) return '#857668';
  if (grade === 'A') return '#4CAF50';
  if (grade === 'B') return '#8BC34A';
  if (grade === 'C') return '#FF9F43';
  if (grade === 'D' || grade === 'E') return '#FF8C42';
  return '#FF6B6B'; // F
}

function renderRecord(record) {
  if (!record) return;

  // Summary badges
  document.getElementById('term-badge').textContent = record.academicTerm;
  document.getElementById('term-badge').classList.remove('hidden');
  document.getElementById('grade-badge').textContent = `${record.finalGrade} — ${record.gradeLabel}`;
  document.getElementById('grade-badge').classList.remove('hidden');
  document.getElementById('grade-badge').style.color = gradeColor(record.finalGrade);

  // Stats
  document.getElementById('avg-mark').textContent = record.averageMark?.toFixed(1) ?? '—';
  document.getElementById('final-grade').textContent = record.finalGrade ?? '—';
  document.getElementById('grade-label').textContent = record.gradeLabel ?? '';
  document.getElementById('final-grade').style.color = gradeColor(record.finalGrade);

  const scores = record.scores ?? [];

  // Radar chart
  const labels = scores.map(s => s.subjectName);
  const values = scores.map(s => s.score ?? 0);

  if (radarChart) radarChart.destroy();
  const ctx = document.getElementById('radar-chart').getContext('2d');
  radarChart = new Chart(ctx, {
    type: 'radar',
    data: {
      labels,
      datasets: [{
        label: 'Score',
        data: values,
        backgroundColor: 'rgba(255,215,0,0.25)',
        borderColor: '#E6C200',
        borderWidth: 2.5,
        pointBackgroundColor: '#4A3F35',
        pointRadius: 5,
        pointHoverRadius: 7,
      }],
    },
    options: {
      animation: {
        onComplete: (ev) => {
          const printImg = document.getElementById('radar-chart-print');
          if (printImg) printImg.src = ev.chart.toBase64Image('image/png', 1);
        },
      },
      scales: {
        r: {
          beginAtZero: true,
          max: 100,
          ticks: { stepSize: 25, font: { family: 'Plus Jakarta Sans', size: 10 } },
          grid: { color: 'rgba(0,0,0,.06)' },
          pointLabels: { font: { family: 'Plus Jakarta Sans', size: 11, weight: '700' }, color: '#4A3F35' },
        },
      },
      plugins: { legend: { display: false }, tooltip: { callbacks: {
        label: (ctx) => ` ${ctx.raw}/100`
      }}},
    },
  });

  // Score breakdown table
  document.getElementById('scores-table').innerHTML = scores.map(s => {
    const pct = Math.round(s.score ?? 0);
    const barColor = pct >= 80 ? '#4CAF50' : pct >= 60 ? '#FF9F43' : '#FF6B6B';
    return `<div class="flex items-center gap-3">
      <span class="text-xs font-bold text-ink w-28 flex-shrink-0 truncate">${s.subjectName}</span>
      <div class="flex-1 h-2 bg-surface-high rounded-full overflow-hidden">
        <div class="h-full rounded-full" style="width:${pct}%;background:${barColor}"></div>
      </div>
      <span class="text-xs font-bold text-ink w-8 text-right">${pct}</span>
    </div>`;
  }).join('');

  reportEl.classList.remove('hidden');
}

// Build subject row for submit form
function addSubjectRow(name = '', score = '') {
  const row = document.createElement('div');
  row.className = 'flex gap-2 items-center';
  row.innerHTML = `
    <input type="text" placeholder="Subject name" value="${name}"
      class="flex-1 bg-surface-high border-0 rounded-xl px-3 py-2 text-sm font-medium text-ink outline-none focus:ring-2 focus:ring-primary subject-name">
    <input type="number" min="0" max="100" step="0.1" placeholder="Score" value="${score}"
      class="w-24 bg-surface-high border-0 rounded-xl px-3 py-2 text-sm font-medium text-ink outline-none focus:ring-2 focus:ring-primary subject-score">
    <button type="button" class="p-1 rounded-full hover:bg-danger/10 transition-colors remove-row"
      style="color:#FF6B6B">
      <span class="material-symbols-outlined" style="font-size:18px">remove_circle</span>
    </button>
  `;
  row.querySelector('.remove-row').addEventListener('click', () => row.remove());
  document.getElementById('score-rows').appendChild(row);
}

// Load student info + records
console.log('[academic] studentId:', studentId, '| role:', profile.role);
if (!studentId) {
  const backHref = profile.role === 'PARENT' ? 'dashboard-parent.html' : 'students.html';
  loadingEl.innerHTML = `<p class="text-danger font-medium">No student selected.
    <a href="${backHref}" class="underline font-bold">${profile.role === 'PARENT' ? 'Go to Dashboard' : 'Go to Students'}</a>
    and click a student to view their report.</p>`;
} else {
  try {
    console.log('[academic] fetching student + records...');
    const [studentRes, recordsRes] = await Promise.all([
      api.get(`/students/${studentId}`),
      api.get(`/academic/students/${studentId}/records`),
    ]);
    console.log('[academic] student:', studentRes.data, '| records:', recordsRes.data);
    const student = studentRes.data;
    allRecords = recordsRes.data ?? [];

    // Student header
    const initials = student.fullName?.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase() ?? '?';
    document.getElementById('student-avatar').textContent = initials;
    document.getElementById('student-name').textContent = `${student.fullName}'s Progress`;
    const age = student.dateOfBirth
      ? Math.floor((Date.now() - new Date(student.dateOfBirth)) / 31_557_600_000)
      : null;
    document.getElementById('student-meta').textContent =
      `${student.className ?? 'No class'} · ${student.gender ?? '—'}${age != null ? ` · ${age} yrs` : ''}`;

    loadingEl.classList.add('hidden');

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
              location.href = `academic.html?id=${c.id}`;
            });
            switcher.appendChild(btn);
          });
          switcher.classList.remove('hidden');
          switcher.classList.add('flex');
        }
      } catch { /* silently skip — switcher is a convenience, not critical */ }
    }

    if (allRecords.length === 0) {
      noRecordsEl.classList.remove('hidden');
      if (profile.role === 'TEACHER' || profile.role === 'ADMIN') {
        document.getElementById('no-records-sub').textContent =
          'Use the form below to submit the first academic record.';
        document.getElementById('submit-section').classList.remove('hidden');
        addSubjectRow();
        document.getElementById('add-subject-btn').addEventListener('click', () => addSubjectRow());
      }
    } else {
      // Populate term selector
      termSelect.innerHTML = allRecords.map((r, i) =>
        `<option value="${i}">${r.academicTerm}</option>`
      ).join('');
      renderRecord(allRecords[0]);
    }

    // Teacher-only: show submit form
    if (profile.role === 'TEACHER' || profile.role === 'ADMIN') {
      document.getElementById('submit-section').classList.remove('hidden');
      if (allRecords.length > 0) addSubjectRow(); // start with one blank row
      document.getElementById('add-subject-btn').addEventListener('click', () => addSubjectRow());
    }

    // Term change handler
    termSelect.addEventListener('change', () => {
      const idx = parseInt(termSelect.value, 10);
      renderRecord(allRecords[idx]);
    });

    // Submit form
    document.getElementById('submit-form')?.addEventListener('submit', async (e) => {
      e.preventDefault();
      const errEl = document.getElementById('submit-error');
      const okEl = document.getElementById('submit-success');
      const btn = document.getElementById('submit-btn');

      errEl.classList.add('hidden');
      okEl.classList.add('hidden');
      btn.disabled = true;
      btn.innerHTML = '<span class="material-symbols-outlined animate-spin">progress_activity</span> Submitting…';

      const term = document.getElementById('input-term').value.trim();
      const rows = document.querySelectorAll('#score-rows > div');
      const scores = [];
      for (const row of rows) {
        const name = row.querySelector('.subject-name')?.value.trim();
        const score = parseFloat(row.querySelector('.subject-score')?.value);
        if (name && !isNaN(score)) scores.push({ subjectName: name, score });
      }

      if (!term) { errEl.textContent = 'Please enter an academic term.'; errEl.classList.remove('hidden'); btn.disabled = false; btn.innerHTML = '<span class="material-symbols-outlined">check_circle</span> Submit &amp; Calculate Grade'; return; }
      if (scores.length === 0) { errEl.textContent = 'Please add at least one subject score.'; errEl.classList.remove('hidden'); btn.disabled = false; btn.innerHTML = '<span class="material-symbols-outlined">check_circle</span> Submit &amp; Calculate Grade'; return; }

      try {
        const { data: newRecord } = await api.post(
          `/academic/students/${studentId}/records`,
          { academicTerm: term, scores }
        );
        allRecords.unshift(newRecord);
        termSelect.innerHTML = allRecords.map((r, i) =>
          `<option value="${i}">${r.academicTerm}</option>`
        ).join('');
        renderRecord(newRecord);
        noRecordsEl.classList.add('hidden');
        okEl.textContent = `Record submitted! Grade: ${newRecord.finalGrade} (${newRecord.gradeLabel}) — Avg: ${newRecord.averageMark?.toFixed(1)}`;
        okEl.classList.remove('hidden');
        document.getElementById('input-term').value = '';
        document.getElementById('score-rows').innerHTML = '';
        addSubjectRow();
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">check_circle</span> Submit &amp; Calculate Grade';
      } catch (err) {
        errEl.textContent = err.response?.data?.message ?? 'Submission failed. Please try again.';
        errEl.classList.remove('hidden');
        btn.disabled = false;
        btn.innerHTML = '<span class="material-symbols-outlined">check_circle</span> Submit &amp; Calculate Grade';
      }
    });

  } catch (err) {
    loadingEl.innerHTML = `<p class="font-medium" style="color:#FF6B6B">
      Failed to load report. ${err.response?.status === 403 ? 'Access denied.' : 'Please try again.'}
    </p>`;
  }
}
