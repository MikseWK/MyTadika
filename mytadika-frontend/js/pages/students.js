import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('students');
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();

let allStudents = [];
let activeClassFilter = '';

function calcAge(dob) {
  if (!dob) return '—';
  return Math.floor((Date.now() - new Date(dob)) / 31_557_600_000) + ' yrs';
}

function renderCards(students) {
  const container = document.getElementById('table-container');
  const emptyState = document.getElementById('empty-state');
  document.getElementById('shown-count').textContent = students.length;

  if (students.length === 0) {
    container.innerHTML = '';
    emptyState.classList.remove('hidden');
    return;
  }
  emptyState.classList.add('hidden');

  container.innerHTML = `
    <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
      ${students.map(s => {
        const initials = s.fullName?.split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase() ?? '?';
        const age = calcAge(s.dateOfBirth);
        const gender = s.gender ? (s.gender.charAt(0) + s.gender.slice(1).toLowerCase()) : null;
        return `
          <div class="bg-surface rounded-2xl p-6 flex flex-col gap-4 shadow-card hover:-translate-y-1 hover:shadow-lg transition-all duration-200 cursor-pointer" data-student-id="${s.id}">
            <div class="flex items-start gap-4">
              <div class="w-14 h-14 rounded-2xl bg-primary/20 flex items-center justify-center font-extrabold text-xl text-ink flex-shrink-0">
                ${initials}
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="font-bold text-ink text-base truncate">${s.fullName}</h3>
                ${s.studentCode ? `<p class="text-xs text-ink-muted mt-0.5">${s.studentCode}</p>` : ''}
                <div class="flex items-center gap-2 mt-1.5 flex-wrap">
                  ${s.className
                    ? `<span class="px-2.5 py-0.5 bg-primary/20 text-ink text-xs font-bold rounded-full">${s.className}</span>`
                    : '<span class="text-xs text-ink-muted">No class</span>'}
                </div>
              </div>
            </div>
            <div class="flex items-center gap-3 text-xs text-ink-muted flex-wrap">
              <span class="flex items-center gap-1">
                <span class="material-symbols-outlined" style="font-size:14px">cake</span>
                ${age}
              </span>
              ${gender ? `<span class="flex items-center gap-1">
                <span class="material-symbols-outlined" style="font-size:14px">person</span>
                ${gender}
              </span>` : ''}
              ${s.parentName ? `<span class="flex items-center gap-1 min-w-0">
                <span class="material-symbols-outlined flex-shrink-0" style="font-size:14px">family_restroom</span>
                <span class="truncate">${s.parentName}</span>
              </span>` : ''}
            </div>
            <div class="flex items-center gap-2 pt-3 border-t border-outline card-actions">
              <button data-go="academic" class="flex-1 flex items-center justify-center gap-1.5 py-2.5 bg-surface-high rounded-xl text-xs font-bold text-ink hover:bg-primary/20 transition-colors">
                <span class="material-symbols-outlined" style="font-size:15px">assessment</span>
                Academic
              </button>
              <button data-go="health" class="flex-1 flex items-center justify-center gap-1.5 py-2.5 bg-surface-high rounded-xl text-xs font-bold text-ink hover:bg-success/15 transition-colors">
                <span class="material-symbols-outlined text-success" style="font-size:15px">medical_services</span>
                Health
              </button>
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;

  container.addEventListener('click', (e) => {
    const card = e.target.closest('[data-student-id]');
    if (!card) return;
    const id = card.dataset.studentId;
    const actionBtn = e.target.closest('[data-go]');
    if (actionBtn) {
      const dest = actionBtn.dataset.go === 'health' ? 'health' : 'academic';
      localStorage.setItem('selectedStudentId', id);
      location.href = `${dest}.html?id=${id}`;
      return;
    }
    if (!e.target.closest('.card-actions')) {
      localStorage.setItem('selectedStudentId', id);
      location.href = `academic.html?id=${id}`;
    }
  });
}

function buildClassroomFilter(students) {
  const classrooms = [...new Set(students.map(s => s.className).filter(Boolean))].sort();
  const filterContainer = document.getElementById('classroom-filter');
  if (!filterContainer || classrooms.length < 2) return;

  const btnBase = 'px-4 py-1.5 rounded-full text-xs font-bold transition-colors';
  const btnActive = `${btnBase} bg-primary text-ink`;
  const btnInactive = `${btnBase} bg-surface-high text-ink-muted hover:bg-primary/20`;

  const renderFilter = () => {
    filterContainer.innerHTML = [
      { label: 'All Classes', value: '' },
      ...classrooms.map(c => ({ label: c, value: c })),
    ].map(f => `
      <button class="${f.value === activeClassFilter ? btnActive : btnInactive}" data-class="${f.value}">
        ${f.label}
      </button>
    `).join('');
    filterContainer.querySelectorAll('button').forEach(btn => {
      btn.addEventListener('click', () => {
        activeClassFilter = btn.dataset.class;
        renderFilter();
        const filtered = activeClassFilter
          ? allStudents.filter(s => s.className === activeClassFilter)
          : allStudents;
        const q = document.getElementById('search-input').value.toLowerCase();
        renderCards(q ? filtered.filter(s =>
          s.fullName?.toLowerCase().includes(q) ||
          s.studentCode?.toLowerCase().includes(q) ||
          s.parentName?.toLowerCase().includes(q)
        ) : filtered);
      });
    });
  };
  renderFilter();
}

// Load
try {
  const { data } = await api.get('/students');
  allStudents = data;
  document.getElementById('total-count').textContent = allStudents.length;
  document.getElementById('shown-count').textContent = allStudents.length;
  document.getElementById('loading-state')?.remove();
  renderCards(allStudents);
  buildClassroomFilter(allStudents);
} catch {
  document.getElementById('table-container').innerHTML = `
    <div class="text-center py-12 font-medium" style="color:#FF6B6B">
      Failed to load students. Check your connection and try again.
    </div>`;
}

// Live search
document.getElementById('search-input').addEventListener('input', (e) => {
  const q = e.target.value.toLowerCase();
  const base = activeClassFilter
    ? allStudents.filter(s => s.className === activeClassFilter)
    : allStudents;
  const filtered = base.filter(s =>
    s.fullName?.toLowerCase().includes(q) ||
    s.studentCode?.toLowerCase().includes(q) ||
    s.parentName?.toLowerCase().includes(q) ||
    s.className?.toLowerCase().includes(q)
  );
  renderCards(filtered);
});
