import api from './axiosClient.js';

export const recordMeasurement = (body)                    => api.post('/health/record', body).then(r => r.data);
export const getHistory        = (studentId)               => api.get(`/health/history/${studentId}`).then(r => r.data);
export const getAdvice         = (studentId, activityLevel) =>
  api.get(`/health/advice/${studentId}`, { params: activityLevel ? { activityLevel } : {} }).then(r => r.data);
export const generateAdvice    = (body)                    => api.post('/health/predict', body).then(r => r.data);
export const getAllergies       = (studentId)               => api.get(`/health/allergies/${studentId}`).then(r => r.data);
export const updateAllergies   = (studentId, body)         => api.put(`/health/allergies/${studentId}`, body).then(r => r.data);
export const getGrowthChart    = (studentId)               => api.get(`/health/students/${studentId}/growth-chart`).then(r => r.data);
