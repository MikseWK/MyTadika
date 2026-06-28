import { forgotPassword } from '../api/authApi.js';

const form       = document.getElementById('forgot-form');
const errorMsg   = document.getElementById('error-msg');
const successMsg = document.getElementById('success-msg');
const btnText    = document.getElementById('btn-text');
const submitBtn  = document.getElementById('submit-btn');

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const email = document.getElementById('email').value.trim();

  errorMsg.classList.add('hidden');
  successMsg.classList.add('hidden');
  submitBtn.disabled = true;
  btnText.textContent = 'Sending…';

  try {
    await forgotPassword(email);
    successMsg.textContent = 'Reset link sent! Check your email inbox.';
    successMsg.classList.remove('hidden');
    form.reset();
  } catch (err) {
    errorMsg.textContent = err.message || 'Something went wrong. Please try again.';
    errorMsg.classList.remove('hidden');
  } finally {
    submitBtn.disabled = false;
    btnText.textContent = 'Send Reset Link';
  }
});
