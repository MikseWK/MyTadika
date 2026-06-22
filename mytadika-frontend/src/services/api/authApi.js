import apiClient from './axiosClient'

export const authApi = {
  async me() {
    const { data } = await apiClient.get('/auth/me')
    return data
  },
  async completeProfile(payload) {
    const { data } = await apiClient.post('/accounts/complete-profile', payload)
    return data
  },
  async updateMe(payload) {
    const { data } = await apiClient.put('/accounts/me', payload)
    return data
  },
}
