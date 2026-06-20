import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { studentApi } from '../services/api/studentApi'

export function useStudentList(classroomId) {
  return useQuery({
    queryKey: ['students', { classroomId }],
    queryFn: () => studentApi.list(classroomId),
  })
}

export function useMyChildren() {
  return useQuery({
    queryKey: ['students', 'my-children'],
    queryFn: () => studentApi.myChildren(),
  })
}

export function useStudent(id) {
  return useQuery({
    queryKey: ['students', id],
    queryFn: () => studentApi.get(id),
    enabled: Boolean(id),
  })
}

export function useCreateStudent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload) => studentApi.create(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  })
}

export function useUpdateStudent(id) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload) => studentApi.update(id, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  })
}

export function useDeleteStudent() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id) => studentApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['students'] }),
  })
}
