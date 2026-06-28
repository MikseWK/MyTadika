import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('profile');

// Populate header & card
document.getElementById('header-avatar').textContent = (profile.fullName?.[0] ?? '?').toUpperCase();
document.getElementById('profile-name').textContent = profile.fullName ?? '—';
document.getElementById('profile-email').textContent = profile.email ?? '—';
document.getElementById('profile-phone').textContent = profile.phoneNumber ?? 'Not set';
document.getElementById('role-badge').textContent = profile.role ?? '—';

const avatarEl = document.getElementById('avatar-circle');
avatarEl.textContent = (profile.fullName?.[0] ?? '?').toUpperCase();

// Fetch full profile — login cache only stores { accountId, role, fullName, email }
// Phone number and profile image require a fresh /auth/me call
try {
  const { data: full } = await api.get('/auth/me');
  document.getElementById('profile-phone').textContent = full.phoneNumber ?? 'Not set';
  if (full.profileImageUrl) {
    avatarEl.innerHTML = `<img src="${full.profileImageUrl}" class="w-full h-full object-cover" alt="Profile">`;
  }
  // Enrich the cache so edit-profile.js inherits these fields without another fetch
  const cached = JSON.parse(localStorage.getItem('userProfile') || '{}');
  localStorage.setItem('userProfile', JSON.stringify({ ...cached, ...full }));
} catch { /* keep defaults already rendered above */ }

// Children section — PARENT only
if (profile.role === 'PARENT') {
  document.getElementById('children-section').classList.remove('hidden');
  const grid = document.getElementById('children-grid');
  try {
    const { data: children } = await api.get('/students/my-children');
    if (children.length === 0) {
      grid.innerHTML = '<p class="text-ink-muted text-sm col-span-full">No children registered yet.</p>';
    } else {
      grid.innerHTML = children.map(c => {
        const age = c.dateOfBirth
          ? Math.floor((Date.now() - new Date(c.dateOfBirth)) / 31_557_600_000)
          : null;
        return `<a href="academic.html?id=${c.id}"
          class="bg-surface-low rounded-xl p-5 flex items-center gap-4 hover:bg-primary/10 transition-colors"
          style="box-shadow:0 2px 8px rgba(0,0,0,.08)">
          <div class="w-16 h-16 rounded-xl bg-surface-high flex items-center justify-center flex-shrink-0 font-extrabold text-2xl text-ink">
            ${c.fullName?.[0]?.toUpperCase() ?? '?'}
          </div>
          <div>
            <h4 class="font-bold text-ink">${c.fullName}</h4>
            <p class="text-sm text-ink-muted">${age != null ? `${age} years old` : ''}</p>
            <span class="inline-block mt-1 px-3 py-1 bg-primary/20 text-ink text-xs font-bold rounded-full">
              ${c.className ?? 'No class'}
            </span>
          </div>
        </a>`;
      }).join('');
    }
  } catch {
    grid.innerHTML = '<p class="text-sm col-span-full" style="color:#FF6B6B">Could not load children.</p>';
  }
}
