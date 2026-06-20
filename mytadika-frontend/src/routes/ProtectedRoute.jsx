import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { dashboardPathFor } from './roleRoutes'

export function ProtectedRoute({ allowedRoles, children }) {
  const { session, account, loading } = useAuth()

  if (loading) return null
  if (!session) return <Navigate to="/login" replace />
  if (!account) return null

  if (allowedRoles && !allowedRoles.includes(account.role)) {
    return <Navigate to={dashboardPathFor(account.role)} replace />
  }

  return children
}
