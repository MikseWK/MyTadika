export const DASHBOARD_PATHS = {
  PARENT: '/parent/dashboard',
  TEACHER: '/teacher/dashboard',
  ADMIN: '/admin/dashboard',
}

export function dashboardPathFor(role) {
  return DASHBOARD_PATHS[role] ?? '/login'
}
