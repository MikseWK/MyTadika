import { resetPassword } from '../api/authApi.js';

// Token can come from the URL or be entered manually
let urlToken = new URLSearchParams(location.search).get('token');

// No token in URL — show the manual input field instead of blocking the user
if (!urlToken) {
  document.getElementById('token-field').classList.remove('hidden');
}

// Password visibility toggle
document.getElementById('toggle-pw').addEventListener('click', () => {
  const input = document.getElementById('new-password');
  const icon  = document.getElementById('toggle-pw-icon');
  input.type = input.type === 'password' ? 'text' : 'password';
  icon.textContent = input.type === 'password' ? 'visibility' : 'visibility_off';
});

// Form submit
document.getElementById('reset-form').addEventListener('submit', async (e) => {
  e.preventDefault();

  const token         = urlToken || document.getElementById('token-input')?.value?.trim();
  const newPassword   = document.getElementById('new-password').value;
  const confirmPassword = document.getElementById('confirm-password').value;
  const errorEl       = document.getElementById('error-msg');
  const btn           = document.getElementById('submit-btn');
  const btnText       = document.getElementById('btn-text');

  errorEl.classList.add('hidden');

  if (!token) {
    errorEl.textContent = 'Please paste the reset code from your email.';
    errorEl.classList.remove('hidden');
    return;
  }

  if (newPassword !== confirmPassword) {
    errorEl.textContent = 'Passwords do not match.';
    errorEl.classList.remove('hidden');
    return;
  }

  if (newPassword.length < 8) {
    errorEl.textContent = 'Password must be at least 8 characters.';
    errorEl.classList.remove('hidden');
    return;
  }

  btn.disabled = true;
  btnText.textContent = 'Resetting…';

  try {
    await resetPassword(token, newPassword);
    document.getElementById('form-state').classList.add('hidden');
    document.getElementById('success-state').classList.remove('hidden');
    setTimeout(() => { window.location.href = '/pages/login.html'; }, 2500);
  } catch (err) {
    const msg = err.message ?? '';
    if (msg.includes('expired') || msg.includes('Invalid') || msg.includes('already been used')) {
      document.getElementById('form-state').classList.add('hidden');
      document.getElementById('invalid-state').classList.remove('hidden');
    } else {
      errorEl.textContent = msg || 'Something went wrong. Please request a new reset code.';
      errorEl.classList.remove('hidden');
      btn.disabled = false;
      btnText.textContent = 'Reset Password';
    }
  }
});
