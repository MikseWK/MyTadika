import { login } from '../api/authApi.js';

// Redirect already-logged-in users
const existingToken = localStorage.getItem('authToken');
if (existingToken) {
  try {
    const [, b64] = existingToken.split('.');
    const { exp, role } = JSON.parse(atob(b64));
    if (Date.now() / 1000 < exp) {
      window.location.replace(role === 'PARENT' ? '/pages/dashboard-parent.html' : '/pages/dashboard-teacher.html');
    }
  } catch { /* invalid token — fall through to login */ }
}

// Role selector (visual feedback only)
let selectedRole = null;
document.querySelectorAll('.role-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.role-btn').forEach(b => {
      b.classList.remove('bg-primary', 'text-ink', 'border-primary', 'scale-105', 'shadow-md');
      b.classList.add('bg-surface-high', 'text-ink-muted', 'border-transparent');
    });
    btn.classList.remove('bg-surface-high', 'text-ink-muted', 'border-transparent');
    btn.classList.add('bg-primary', 'text-ink', 'border-primary', 'scale-105', 'shadow-md');
    selectedRole = btn.dataset.role;
  });
});

// Password visibility
document.getElementById('toggle-pw').addEventListener('click', () => {
  const input = document.getElementById('password');
  const icon  = document.getElementById('toggle-pw-icon');
  input.type = input.type === 'password' ? 'text' : 'password';
  icon.textContent = input.type === 'password' ? 'visibility' : 'visibility_off';
});

// Login form
const form      = document.getElementById('login-form');
const errorMsg  = document.getElementById('error-msg');
const btnText   = document.getElementById('btn-text');
const submitBtn = document.getElementById('submit-btn');

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const email    = document.getElementById('email').value.trim();
  const password = document.getElementById('password').value;

  errorMsg.classList.add('hidden');
  submitBtn.disabled = true;
  btnText.textContent = 'Signing in…';

  try {
    const { role } = await login(email, password);
    const dest = role === 'PARENT' ? '/pages/dashboard-parent.html' : '/pages/dashboard-teacher.html';
    window.location.href = dest;
  } catch (err) {
    errorMsg.textContent = err.message.includes('401') || err.message.includes('credentials')
      ? 'Incorrect email or password. Please try again.'
      : (err.message || 'Something went wrong. Please try again.');
    errorMsg.classList.remove('hidden');
    submitBtn.disabled = false;
    btnText.textContent = 'Sign In to MyTadika';
  }
});
