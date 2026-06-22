import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useStudentList } from '../../hooks/useStudents'
import '../students/students.css'

function TeacherDashboard() {
  const { account } = useAuth()
  const { data: students, isLoading, isError } = useStudentList()

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">Welcome, {account?.fullName}</h1>
        </div>
        <Link className="btn btn-primary" to="/students/new">+ Add Student</Link>
      </div>

      <div className="card">
        <h2 style={{ marginBottom: 16, fontSize: 18 }}>Your Students</h2>
        {isLoading && <p className="empty-state">Loading…</p>}
        {isError && <p className="empty-state">Could not load students. Please try again.</p>}
        {!isLoading && !isError && students?.length === 0 && (
          <p className="empty-state">No students yet. Add your first student to get started.</p>
        )}
        {!isLoading && !isError && students?.length > 0 && (
          <>
            <p style={{ color: 'var(--color-text-muted)', marginBottom: 16 }}>
              {students.length} student{students.length === 1 ? '' : 's'} across your classrooms.
            </p>
            <Link className="btn btn-secondary" to="/students">View All Students →</Link>
          </>
        )}
      </div>
    </div>
  )
}

export default TeacherDashboard
