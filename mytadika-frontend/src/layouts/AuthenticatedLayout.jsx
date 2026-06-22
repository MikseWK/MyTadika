import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import Sidebar from '../components/common/Sidebar'
import '../components/common/Sidebar.css'

function AuthenticatedLayout() {
  const { session, account, loading } = useAuth()

  if (loading) {
    return <p className="empty-state">Loading…</p>
  }
  if (!session) {
    return <Navigate to="/login" replace />
  }
  if (!account) {
    return null
  }

  return (
    <div className="app-shell">
      <Sidebar role={account.role} />
      <main className="app-content">
        <Outlet />
      </main>
    </div>
  )
}

export default AuthenticatedLayout
