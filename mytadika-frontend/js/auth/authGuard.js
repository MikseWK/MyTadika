// Runs on every protected page — redirect to login if unauthenticated or wrong role.
// Usage: import { token, profile } from '../auth/authGuard.js';

const token = localStorage.getItem('authToken');

if (!token) {
  window.location.replace('/pages/login.html');
  throw new Error('Unauthenticated');
}

// Decode JWT payload (client-side expiry check — no signature verification needed here)
let payload;
try {
  payload = JSON.parse(atob(token.split('.')[1]));
} catch {
  localStorage.clear();
  window.location.replace('/pages/login.html');
  throw new Error('Invalid token');
}

if (Date.now() / 1000 > payload.exp) {
  localStorage.removeItem('authToken');
  localStorage.removeItem('userProfile');
  window.location.replace('/pages/login.html');
  throw new Error('Token expired');
}

// Load profile from cache or backend
let profile = JSON.parse(localStorage.getItem('userProfile') || 'null');
if (!profile) {
  const res = await fetch('http://localhost:8080/api/auth/me', {
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!res.ok) {
    localStorage.clear();
    window.location.replace('/pages/login.html');
    throw new Error('Profile fetch failed');
  }
  profile = await res.json();
  localStorage.setItem('userProfile', JSON.stringify(profile));
}

// Role guard — <body data-roles="TEACHER,ADMIN">
const allowedRoles = (document.body.dataset.roles ?? '').split(',').filter(Boolean);
if (allowedRoles.length && !allowedRoles.includes(profile.role)) {
  window.location.replace('/pages/login.html');
  throw new Error('Forbidden');
}

export { token, profile };
