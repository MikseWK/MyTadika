import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('home');

// Header
const hour = new Date().getHours();
const tod = hour < 12 ? 'Morning' : hour < 17 ? 'Afternoon' : 'Evening';
const firstName = profile.fullName?.split(' ')[0] ?? 'there';
document.getElementById('greeting-text').textContent = `Good ${tod}, ${firstName}!`;
document.getElementById('header-name').textContent = profile.fullName ?? profile.email;
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();
document.getElementById('header-date').textContent = new Date().toLocaleDateString('en-MY', {
  weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
});

// Load children
try {
  const { data: children } = await api.get('/students/my-children');
  if (children.length > 0) {
    const child = children[0];
    const age = child.dateOfBirth
      ? Math.floor((Date.now() - new Date(child.dateOfBirth)) / 31_557_600_000)
      : null;
    document.getElementById('child-info').innerHTML = `
      <h3 class="text-lg font-bold text-ink">${child.fullName}</h3>
      <p class="text-sm text-ink-muted mt-1">${child.className ?? 'No class assigned'}${age != null ? ` · ${age} yrs` : ''}</p>
    `;
    document.getElementById('greeting-sub').textContent =
      `${child.fullName} is ready for a great day of learning!`;

    // Prefer previously selected child if parent has multiple
    const selectedId = localStorage.getItem('selectedChildId') ?? child.id;
    localStorage.setItem('selectedChildId', selectedId);
    document.getElementById('link-health').href = `health.html?id=${selectedId}`;
    document.getElementById('link-academic').href = `academic.html?id=${selectedId}`;
    const btn = document.getElementById('view-report-btn');
    btn.href = `academic.html?id=${selectedId}`;
    btn.classList.remove('hidden');
  } else {
    document.getElementById('child-info').innerHTML =
      '<p class="text-ink-muted text-sm">No children registered yet.</p>';
    document.getElementById('greeting-sub').textContent = 'Welcome to MyTadika!';
  }
} catch {
  document.getElementById('child-info').innerHTML =
    '<p class="text-sm font-medium" style="color:#FF6B6B">Could not load child data.</p>';
}
