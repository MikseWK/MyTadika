import apiClient from './axiosClient'

export const healthApi = {
  async getHistory(studentId) {
    const { data } = await apiClient.get(`/health/history/${studentId}`)
    return data
  },
  async getLatestAdvice(studentId, activityLevel = 1) {
    try {
      const { data } = await apiClient.get(`/health/advice/${studentId}`, {
        params: { activityLevel },
      })
      return data
    } catch (err) {
      if (err.response?.status === 404) return null
      throw err
    }
  },
  async getAllergies(studentId) {
    const { data } = await apiClient.get(`/health/allergies/${studentId}`)
    return data
  },
  async updateAllergies(studentId, allergies) {
    const { data } = await apiClient.put(`/health/allergies/${studentId}`, { allergies })
    return data
  },
  async recordMeasurement(payload) {
    const { data } = await apiClient.post('/health/record', payload)
    return data
  },
}
