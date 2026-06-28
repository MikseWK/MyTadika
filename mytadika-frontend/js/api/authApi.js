const BASE = 'http://localhost:8080/api';

function authHeaders() {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${localStorage.getItem('authToken')}`
  };
}

async function handleResponse(res) {
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) throw new Error(data.message || `HTTP ${res.status}`);
  return data;
}

export async function login(email, password) {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  const data = await handleResponse(res);
  localStorage.setItem('authToken', data.token);
  localStorage.setItem('userProfile', JSON.stringify({
    accountId: data.accountId,
    role:      data.role,
    fullName:  data.fullName,
    email:     data.email
  }));
  return data;
}

export async function register(body) {
  const res = await fetch(`${BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return handleResponse(res);
}

export async function getMe() {
  const res = await fetch(`${BASE}/auth/me`, { headers: authHeaders() });
  return handleResponse(res);
}

export async function updateMe(body) {
  const res = await fetch(`${BASE}/accounts/me`, {
    method: 'PUT',
    headers: authHeaders(),
    body: JSON.stringify(body)
  });
  return handleResponse(res);
}

export async function forgotPassword(email) {
  const res = await fetch(`${BASE}/auth/forgot-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });
  return handleResponse(res);
}

export async function resetPassword(token, newPassword) {
  const res = await fetch(`${BASE}/auth/reset-password`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token, newPassword })
  });
  return handleResponse(res);
}

export function logout() {
  localStorage.removeItem('authToken');
  localStorage.removeItem('userProfile');
  window.location.href = '/pages/login.html';
}
