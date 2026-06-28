import api from './axiosClient.js';

export const listAll      = (classroomId) => api.get('/students', { params: classroomId ? { classroomId } : {} }).then(r => r.data);
export const listMyChildren = ()           => api.get('/students/my-children').then(r => r.data);
export const getOne       = (id)           => api.get(`/students/${id}`).then(r => r.data);
export const create       = (body)         => api.post('/students', body).then(r => r.data);
export const update       = (id, body)     => api.put(`/students/${id}`, body).then(r => r.data);
