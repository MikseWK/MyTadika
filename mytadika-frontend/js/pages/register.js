import { register, login } from '../api/authApi.js';

// Password visibility toggles
function makeToggle(btnId, iconId, inputId) {
  document.getElementById(btnId).addEventListener('click', () => {
    const input = document.getElementById(inputId);
    const icon  = document.getElementById(iconId);
    input.type = input.type === 'password' ? 'text' : 'password';
    icon.textContent = input.type === 'password' ? 'visibility' : 'visibility_off';
  });
}
makeToggle('toggle-pw',  'toggle-pw-icon',  'password');
makeToggle('toggle-cpw', 'toggle-cpw-icon', 'confirm-password');

const form       = document.getElementById('register-form');
const errorMsg   = document.getElementById('error-msg');
const successMsg = document.getElementById('success-msg');
const btnText    = document.getElementById('btn-text');
const submitBtn  = document.getElementById('submit-btn');

form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const fullName        = document.getElementById('name').value.trim();
  const email           = document.getElementById('email').value.trim();
  const password        = document.getElementById('password').value;
  const confirmPassword = document.getElementById('confirm-password').value;

  errorMsg.classList.add('hidden');
  successMsg.classList.add('hidden');

  if (password !== confirmPassword) {
    errorMsg.textContent = 'Passwords do not match.';
    errorMsg.classList.remove('hidden');
    return;
  }

  if (password.length < 8) {
    errorMsg.textContent = 'Password must be at least 8 characters.';
    errorMsg.classList.remove('hidden');
    return;
  }

  submitBtn.disabled = true;
  btnText.textContent = 'Creating account…';

  try {
    await register({ fullName, email, password });
    // Auto-login after registration
    await login(email, password);
    successMsg.textContent = 'Account created! Redirecting…';
    successMsg.classList.remove('hidden');
    setTimeout(() => { window.location.href = '/pages/dashboard-parent.html'; }, 800);
  } catch (err) {
    errorMsg.textContent = err.message || 'Registration failed. Please try again.';
    errorMsg.classList.remove('hidden');
    submitBtn.disabled = false;
    btnText.textContent = 'Start the Adventure';
  }
});
