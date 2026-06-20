import apiClient from './axiosClient'

export const studentApi = {
  async list(classroomId) {
    const { data } = await apiClient.get('/students', { params: { classroomId } })
    return data
  },
  async myChildren() {
    const { data } = await apiClient.get('/students/my-children')
    return data
  },
  async get(id) {
    const { data } = await apiClient.get(`/students/${id}`)
    return data
  },
  async create(payload) {
    const { data } = await apiClient.post('/students', payload)
    return data
  },
  async update(id, payload) {
    const { data } = await apiClient.put(`/students/${id}`, payload)
    return data
  },
  async remove(id) {
    await apiClient.delete(`/students/${id}`)
  },
}
