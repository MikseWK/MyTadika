import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useStudentList } from '../../hooks/useStudents'
import { dashboardPathFor } from '../../routes/roleRoutes'
import './students.css'

function calculateAge(dateOfBirth) {
  const dob = new Date(dateOfBirth)
  const diffMs = Date.now() - dob.getTime()
  const ageDate = new Date(diffMs)
  return Math.abs(ageDate.getUTCFullYear() - 1970)
}

function StudentListPage() {
  const navigate = useNavigate()
  const { account } = useAuth()
  const { data: students, isLoading, isError } = useStudentList()
  const [search, setSearch] = useState('')

  const filtered = useMemo(() => {
    if (!students) return []
    const term = search.trim().toLowerCase()
    if (!term) return students
    return students.filter(
      (student) =>
        student.fullName.toLowerCase().includes(term) ||
        student.studentCode?.toLowerCase().includes(term) ||
        student.parentName.toLowerCase().includes(term),
    )
  }, [students, search])

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <Link className="back-link" to={dashboardPathFor(account?.role)}>
            ← Back to dashboard
          </Link>
          <h1 className="page-title">Students</h1>
        </div>
        <Link className="btn btn-primary" to="/students/new">
          + Add Student
        </Link>
      </div>

      <div className="search-bar">
        <input
          className="text-input"
          placeholder="Search by name, code, or parent…"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
      </div>

      <div className="card">
        {isLoading && <p className="empty-state">Loading students…</p>}
        {isError && <p className="empty-state">Could not load students. Please try again.</p>}
        {!isLoading && !isError && filtered.length === 0 && (
          <p className="empty-state">No students found.</p>
        )}
        {!isLoading && !isError && filtered.length > 0 && (
          <table className="student-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Age</th>
                <th>Gender</th>
                <th>Parent</th>
                <th>Classroom</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((student) => (
                <tr
                  key={student.id}
                  className="row-link"
                  onClick={() => navigate(`/students/${student.id}`)}
                >
                  <td>{student.fullName}</td>
                  <td>{calculateAge(student.dateOfBirth)}</td>
                  <td>{student.gender}</td>
                  <td>{student.parentName}</td>
                  <td>{student.className ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default StudentListPage
