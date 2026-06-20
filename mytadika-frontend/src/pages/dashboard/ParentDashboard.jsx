import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useMyChildren } from '../../hooks/useStudents'
import '../students/students.css'

function ParentDashboard() {
  const { account, signOut } = useAuth()
  const { data: children, isLoading, isError } = useMyChildren()

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">Welcome, {account?.fullName}</h1>
        </div>
        <button type="button" className="btn btn-secondary" onClick={signOut}>Sign out</button>
      </div>

      <div className="card">
        <h2 style={{ marginBottom: 16, fontSize: 18 }}>My Children</h2>
        {isLoading && <p className="empty-state">Loading…</p>}
        {isError && <p className="empty-state">Could not load your children. Please try again.</p>}
        {!isLoading && !isError && children?.length === 0 && (
          <p className="empty-state">No linked children yet. Contact your child's teacher to get set up.</p>
        )}
        {!isLoading && !isError && children?.length > 0 && (
          <table className="student-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Gender</th>
                <th>Classroom</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {children.map((child) => (
                <tr key={child.id}>
                  <td>{child.fullName}</td>
                  <td>{child.gender}</td>
                  <td>{child.className ?? 'Not assigned'}</td>
                  <td>
                    <Link className="link-accent" to={`/students/${child.id}`}>View Profile →</Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default ParentDashboard
