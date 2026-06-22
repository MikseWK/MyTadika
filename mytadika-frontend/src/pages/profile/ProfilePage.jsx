import { useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useUpdateProfile } from '../../hooks/useProfile'
import '../students/students.css'

function ProfilePage() {
  const { account } = useAuth()
  const updateProfile = useUpdateProfile()
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState(null)
  const [error, setError] = useState('')

  if (!account) {
    return (
      <div className="page-shell">
        <p className="empty-state">Loading profile…</p>
      </div>
    )
  }

  function startEditing() {
    setForm({
      fullName: account.fullName ?? '',
      phoneNumber: account.phoneNumber ?? '',
      address: account.address ?? '',
    })
    setError('')
    setEditing(true)
  }

  function handleChange(field) {
    return (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    try {
      await updateProfile.mutateAsync(form)
      setEditing(false)
    } catch (err) {
      setError(err.response?.data?.message || 'Could not save changes. Please try again.')
    }
  }

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <h1 className="page-title">My Profile</h1>
        </div>
        {!editing && (
          <button type="button" className="btn btn-primary" onClick={startEditing}>
            Edit
          </button>
        )}
      </div>

      <div className="card">
        {!editing ? (
          <div className="profile-grid">
            <div>
              <div className="profile-field-label">Full Name</div>
              <div className="profile-field-value">{account.fullName}</div>
            </div>
            <div>
              <div className="profile-field-label">Email</div>
              <div className="profile-field-value">{account.email}</div>
            </div>
            <div>
              <div className="profile-field-label">Role</div>
              <div className="profile-field-value">{account.role}</div>
            </div>
            <div>
              <div className="profile-field-label">Phone Number</div>
              <div className="profile-field-value">{account.phoneNumber || 'Not provided'}</div>
            </div>
            <div className="form-grid-full">
              <div className="profile-field-label">Address</div>
              <div className="profile-field-value">{account.address || 'Not provided'}</div>
            </div>
          </div>
        ) : (
          <form className="form-grid" onSubmit={handleSubmit}>
            <div>
              <label className="field-label" htmlFor="fullName">Full Name</label>
              <input
                id="fullName"
                className="text-input"
                value={form.fullName}
                onChange={handleChange('fullName')}
                required
              />
            </div>
            <div>
              <label className="field-label" htmlFor="phoneNumber">Phone Number</label>
              <input
                id="phoneNumber"
                className="text-input"
                value={form.phoneNumber}
                onChange={handleChange('phoneNumber')}
              />
            </div>
            <div className="form-grid-full">
              <label className="field-label" htmlFor="address">Address</label>
              <textarea
                id="address"
                className="text-input"
                rows={3}
                value={form.address}
                onChange={handleChange('address')}
              />
            </div>

            {error && <p className="form-error">{error}</p>}

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={updateProfile.isPending}>
                {updateProfile.isPending ? 'Saving…' : 'Save Changes'}
              </button>
              <button type="button" className="btn btn-secondary" onClick={() => setEditing(false)}>
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}

export default ProfilePage
