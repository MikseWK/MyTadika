import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('students');
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();

let allStudents = [];

function calcAge(dob) {
  if (!dob) return '—';
  return Math.floor((Date.now() - new Date(dob)) / 31_557_600_000) + ' yrs';
}

function renderTable(students) {
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
    <table class="w-full">
      <thead class="bg-surface-high">
        <tr class="text-left text-xs font-bold text-ink-muted uppercase tracking-wider">
          <th class="px-6 py-4">Student</th>
          <th class="px-6 py-4">Age</th>
          <th class="px-6 py-4">Gender</th>
          <th class="px-6 py-4">Parent</th>
          <th class="px-6 py-4">Class</th>
          <th class="px-6 py-4">Actions</th>
        </tr>
      </thead>
      <tbody class="divide-y divide-outline">
        ${students.map(s => `
          <tr class="hover:bg-surface-low transition-colors cursor-pointer" data-student-id="${s.id}">
            <td class="px-6 py-4">
              <div class="flex items-center gap-3">
                <div class="w-9 h-9 rounded-full bg-primary/20 flex items-center justify-center font-bold text-ink text-sm flex-shrink-0">
                  ${s.fullName?.[0]?.toUpperCase() ?? '?'}
                </div>
                <div>
                  <p class="font-bold text-ink text-sm">${s.fullName}</p>
                  ${s.studentCode ? `<p class="text-xs text-ink-muted">${s.studentCode}</p>` : ''}
                </div>
              </div>
            </td>
            <td class="px-6 py-4 text-sm text-ink-muted">${calcAge(s.dateOfBirth)}</td>
            <td class="px-6 py-4 text-sm text-ink-muted">${s.gender ?? '—'}</td>
            <td class="px-6 py-4 text-sm text-ink-muted">${s.parentName ?? '—'}</td>
            <td class="px-6 py-4">
              ${s.className
                ? `<span class="px-3 py-1 bg-primary/20 text-ink text-xs font-bold rounded-full">${s.className}</span>`
                : '<span class="text-xs text-ink-muted">No class</span>'}
            </td>
            <td class="px-6 py-4 js-action-cell">
              <div class="flex items-center gap-1">
                <button data-go="academic" class="p-2 rounded-full hover:bg-primary/20 transition-colors" title="Academic report">
                  <span class="material-symbols-outlined text-ink" style="font-size:18px">assessment</span>
                </button>
                <button data-go="health" class="p-2 rounded-full hover:bg-success/15 transition-colors" title="Health record">
                  <span class="material-symbols-outlined text-success" style="font-size:18px">medical_services</span>
                </button>
              </div>
            </td>
          </tr>
        `).join('')}
      </tbody>
    </table>
  `;

  container.addEventListener('click', (e) => {
    const row = e.target.closest('tr[data-student-id]');
    if (!row) return;
    const id = row.dataset.studentId;
    const actionBtn = e.target.closest('[data-go]');
    if (actionBtn) {
      const dest = actionBtn.dataset.go === 'health' ? 'health' : 'academic';
      console.log(`[students] ${dest} button clicked — id:`, id);
      localStorage.setItem('selectedStudentId', id);
      location.href = `${dest}.html?id=${id}`;
      return;
    }
    if (!e.target.closest('.js-action-cell')) {
      console.log('[students] row clicked — id:', id);
      localStorage.setItem('selectedStudentId', id);
      location.href = `academic.html?id=${id}`;
    }
  });
}

// Load
try {
  const { data } = await api.get('/students');
  allStudents = data;
  console.log('[students] loaded:', allStudents.length, 'first item:', allStudents[0]);
  document.getElementById('total-count').textContent = allStudents.length;
  document.getElementById('shown-count').textContent = allStudents.length;
  document.getElementById('table-container').innerHTML = '';
  renderTable(allStudents);
} catch {
  document.getElementById('table-container').innerHTML = `
    <div class="text-center py-12 font-medium" style="color:#FF6B6B">
      Failed to load students. Check your connection and try again.
    </div>`;
}

// Live search
document.getElementById('search-input').addEventListener('input', (e) => {
  const q = e.target.value.toLowerCase();
  const filtered = allStudents.filter(s =>
    s.fullName?.toLowerCase().includes(q) ||
    s.studentCode?.toLowerCase().includes(q) ||
    s.parentName?.toLowerCase().includes(q) ||
    s.className?.toLowerCase().includes(q)
  );
  renderTable(filtered);
});
