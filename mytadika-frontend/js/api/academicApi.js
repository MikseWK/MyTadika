import api from './axiosClient.js';

export const listRecords   = (studentId)       => api.get(`/academic/students/${studentId}/records`).then(r => r.data);
export const submitRecord  = (studentId, body) => api.post(`/academic/students/${studentId}/records`, body).then(r => r.data);
export const getRecord     = (id)              => api.get(`/academic/records/${id}`).then(r => r.data);
export const updateRecord  = (id, body)        => api.put(`/academic/records/${id}`, body).then(r => r.data);
