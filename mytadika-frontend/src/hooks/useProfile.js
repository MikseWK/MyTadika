import { useMutation } from '@tanstack/react-query'
import { authApi } from '../services/api/authApi'
import { useAuth } from '../context/AuthContext'

export function useUpdateProfile() {
  const { setAccount } = useAuth()
  return useMutation({
    mutationFn: (payload) => authApi.updateMe(payload),
    onSuccess: (data) => setAccount(data),
  })
}
