import { Link } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

function PlaceholderDashboard({ role }) {
  const { account, signOut } = useAuth()

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 12 }}>
      <h1>{role} Dashboard</h1>
      <p>Signed in as {account?.fullName} ({account?.email})</p>
      <Link to="/students">Manage Students →</Link>
      <button type="button" onClick={signOut}>Sign out</button>
    </div>
  )
}

export default PlaceholderDashboard
