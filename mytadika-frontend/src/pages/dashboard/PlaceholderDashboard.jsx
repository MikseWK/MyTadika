import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import '../students/students.css'

function PlaceholderDashboard({ role }) {
  const { account } = useAuth()

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">{role} Dashboard</h1>
        </div>
      </div>
      <div className="card">
        <p style={{ marginBottom: 16 }}>Signed in as {account?.fullName} ({account?.email})</p>
        <Link className="btn btn-secondary" to="/students">Manage Students →</Link>
      </div>
    </div>
  )
}

export default PlaceholderDashboard
