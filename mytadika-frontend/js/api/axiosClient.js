// Shared axios instance — reads JWT from localStorage, no supabase-js dependency

const api = axios.create({ baseURL: 'http://localhost:8080/api' });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('authToken');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('authToken');
      localStorage.removeItem('userProfile');
      window.location.href = '/pages/login.html';
    }
    return Promise.reject(err);
  }
);

export default api;
