import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('home');

// Header
const hour = new Date().getHours();
const tod = hour < 12 ? 'Morning' : hour < 17 ? 'Afternoon' : 'Evening';
const firstName = profile.fullName?.split(' ')[0] ?? 'Teacher';
document.getElementById('greeting-text').textContent = `Good ${tod}, Teacher ${firstName}!`;
document.getElementById('header-name').textContent = profile.fullName ?? profile.email;
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();
document.getElementById('header-date').textContent = new Date().toLocaleDateString('en-MY', {
  weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
});

const alertsPanel = document.getElementById('alerts-panel');

try {
  const { data: students } = await api.get('/students');
  document.getElementById('student-count-hero').innerHTML =
    `${students.length} <span class="text-base font-normal text-ink/70">students</span>`;

  // Parallel-fetch allergies for first 5 students to populate Needs Attention panel
  const top5 = students.slice(0, 5);
  const results = await Promise.allSettled(
    top5.map(s =>
      api.get(`/health/allergies/${s.id}`).then(r => ({ student: s, allergies: r.data }))
    )
  );

  const alerts = results
    .filter(r => r.status === 'fulfilled' && r.value.allergies?.length > 0)
    .map(r => r.value);

  if (alerts.length === 0) {
    alertsPanel.innerHTML =
      '<p class="text-sm text-ink-muted text-center py-3">No urgent alerts today. All clear!</p>';
  } else {
    alertsPanel.innerHTML = alerts.map(({ student, allergies }) => `
      <div class="flex gap-3 items-start bg-surface p-3 rounded-xl" style="box-shadow:0 2px 8px rgba(0,0,0,.08)">
        <span class="material-symbols-outlined mt-0.5 flex-shrink-0" style="color:#b02500;font-variation-settings:'FILL' 1">no_meals</span>
        <div class="min-w-0">
          <h4 class="font-bold text-sm text-ink truncate">${student.fullName}</h4>
          <p class="text-xs text-ink-muted mt-0.5 truncate">${allergies.join(' · ')}</p>
          <a href="health.html?id=${student.id}" class="mt-1 text-xs font-bold hover:underline" style="color:#b02500">
            View Health →
          </a>
        </div>
      </div>`
    ).join('');
  }
} catch {
  alertsPanel.innerHTML =
    '<p class="text-sm text-center py-3" style="color:#FF6B6B">Could not load student data.</p>';
}
