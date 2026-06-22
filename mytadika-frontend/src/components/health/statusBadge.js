export function statusClass(status) {
  if (status === 'normal') return 'status-badge status-good'
  if (status === 'moderate') return 'status-badge status-ok'
  if (status === 'severe') return 'status-badge status-low'
  return 'status-badge'
}
