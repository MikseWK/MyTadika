import apiClient from './axiosClient'

export const academicApi = {
  async listForStudent(studentId) {
    const { data } = await apiClient.get(`/academic/students/${studentId}/records`)
    return data
  },
  async get(id) {
    const { data } = await apiClient.get(`/academic/records/${id}`)
    return data
  },
  async create(studentId, payload) {
    const { data } = await apiClient.post(`/academic/students/${studentId}/records`, payload)
    return data
  },
  async update(id, payload) {
    const { data } = await apiClient.put(`/academic/records/${id}`, payload)
    return data
  },
}
