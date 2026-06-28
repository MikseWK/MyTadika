import { profile } from '../auth/authGuard.js';
import { initSidebar } from '../components/sidebar.js';
import api from '../api/axiosClient.js';

initSidebar('profile');

// Pre-populate form from cache (fullName, email always present)
const avatarEl = document.getElementById('avatar-preview');
avatarEl.textContent = (profile.fullName?.[0] ?? '?').toUpperCase();
document.getElementById('input-name').value = profile.fullName ?? '';
document.getElementById('input-email').value = profile.email ?? '';

// Fetch full profile to populate phone/address/photo — login cache omits these fields
try {
  const { data: full } = await api.get('/auth/me');
  document.getElementById('input-phone').value = full.phoneNumber ?? '';
  document.getElementById('input-address').value = full.address ?? '';
  if (full.profileImageUrl) {
    avatarEl.innerHTML = `<img src="${full.profileImageUrl}" class="w-full h-full object-cover" alt="">`;
  }
} catch { /* leave fields blank — user can fill them in */ }

// Photo preview on select
document.getElementById('photo-input').addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (!file) return;
  avatarEl.innerHTML = `<img src="${URL.createObjectURL(file)}" class="w-full h-full object-cover" alt="">`;
});

// Submit
document.getElementById('edit-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorEl = document.getElementById('error-msg');
  const successEl = document.getElementById('success-msg');
  const btn = document.getElementById('save-btn');

  errorEl.classList.add('hidden');
  successEl.classList.add('hidden');
  btn.disabled = true;
  btn.innerHTML = '<span class="material-symbols-outlined animate-spin">progress_activity</span> Saving…';

  try {
    const body = {
      fullName: document.getElementById('input-name').value.trim(),
      phoneNumber: document.getElementById('input-phone').value.trim() || null,
      address: document.getElementById('input-address').value.trim() || null,
    };
    await api.put('/accounts/me', body);

    // Upload photo if selected
    const photoFile = document.getElementById('photo-input').files[0];
    if (photoFile) {
      const form = new FormData();
      form.append('file', photoFile);
      await api.post('/accounts/me/profile-image', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
    }

    // Update localStorage cache
    const cached = JSON.parse(localStorage.getItem('userProfile') || '{}');
    Object.assign(cached, body);
    localStorage.setItem('userProfile', JSON.stringify(cached));

    btn.innerHTML = '<span class="material-symbols-outlined">check</span> Saved!';
    successEl.textContent = 'Profile updated successfully.';
    successEl.classList.remove('hidden');
    setTimeout(() => { window.location.href = 'profile.html'; }, 1200);
  } catch (err) {
    errorEl.textContent = err.response?.data?.message ?? 'Failed to save. Please try again.';
    errorEl.classList.remove('hidden');
    btn.disabled = false;
    btn.innerHTML = '<span class="material-symbols-outlined">check_circle</span> Save Changes';
  }
});
