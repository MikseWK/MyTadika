import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { healthApi } from '../services/api/healthApi'

export function useHealthHistory(studentId) {
  return useQuery({
    queryKey: ['health-history', studentId],
    queryFn: () => healthApi.getHistory(studentId),
    enabled: Boolean(studentId),
  })
}

export function useLatestAdvice(studentId, activityLevel = 1) {
  return useQuery({
    queryKey: ['health-advice', studentId, activityLevel],
    queryFn: () => healthApi.getLatestAdvice(studentId, activityLevel),
    enabled: Boolean(studentId),
  })
}

export function useAllergies(studentId) {
  return useQuery({
    queryKey: ['allergies', studentId],
    queryFn: () => healthApi.getAllergies(studentId),
    enabled: Boolean(studentId),
  })
}

export function useUpdateAllergies(studentId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (allergies) => healthApi.updateAllergies(studentId, allergies),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['allergies', studentId] })
      queryClient.invalidateQueries({ queryKey: ['health-advice', studentId] })
    },
  })
}

export function useRecordMeasurement(studentId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload) => healthApi.recordMeasurement(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['health-history', studentId] })
      queryClient.invalidateQueries({ queryKey: ['health-advice', studentId] })
    },
  })
}
