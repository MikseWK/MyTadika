import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { academicApi } from '../services/api/academicApi'

export function useAcademicRecords(studentId) {
  return useQuery({
    queryKey: ['academic-records', studentId],
    queryFn: () => academicApi.listForStudent(studentId),
    enabled: Boolean(studentId),
  })
}

export function useCreateAcademicRecord(studentId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload) => academicApi.create(studentId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['academic-records', studentId] }),
  })
}

export function useUpdateAcademicRecord(studentId, id) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload) => academicApi.update(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['academic-records', studentId] }),
  })
}
