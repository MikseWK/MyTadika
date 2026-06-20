import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import PlaceholderDashboard from './pages/dashboard/PlaceholderDashboard'
import ParentDashboard from './pages/dashboard/ParentDashboard'
import StudentListPage from './pages/students/StudentListPage'
import StudentFormPage from './pages/students/StudentFormPage'
import StudentProfilePage from './pages/students/StudentProfilePage'
import { ProtectedRoute } from './routes/ProtectedRoute'
import { useAuth } from './context/AuthContext'
import { dashboardPathFor } from './routes/roleRoutes'

function RootRedirect() {
  const { session, account, loading } = useAuth()
  if (loading) return null
  if (!session) return <Navigate to="/login" replace />
  if (!account) return null
  return <Navigate to={dashboardPathFor(account.role)} replace />
}

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        path="/parent/dashboard"
        element={
          <ProtectedRoute allowedRoles={['PARENT']}>
            <ParentDashboard />
          </ProtectedRoute>
        }
      />
      <Route
        path="/teacher/dashboard"
        element={
          <ProtectedRoute allowedRoles={['TEACHER']}>
            <PlaceholderDashboard role="Teacher" />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin/dashboard"
        element={
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <PlaceholderDashboard role="Admin" />
          </ProtectedRoute>
        }
      />
      <Route
        path="/students"
        element={
          <ProtectedRoute allowedRoles={['TEACHER', 'ADMIN']}>
            <StudentListPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/students/new"
        element={
          <ProtectedRoute allowedRoles={['TEACHER', 'ADMIN']}>
            <StudentFormPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/students/:id"
        element={
          <ProtectedRoute allowedRoles={['PARENT', 'TEACHER', 'ADMIN']}>
            <StudentProfilePage />
          </ProtectedRoute>
        }
      />
      <Route path="/" element={<RootRedirect />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
