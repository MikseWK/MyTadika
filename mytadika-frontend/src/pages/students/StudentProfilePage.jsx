import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useDeleteStudent, useStudent, useUpdateStudent } from '../../hooks/useStudents'
import { dashboardPathFor } from '../../routes/roleRoutes'
import './students.css'

function formFromStudent(student) {
  return {
    fullName: student.fullName,
    dateOfBirth: student.dateOfBirth,
    gender: student.gender,
    emergencyContact: student.emergencyContact,
    medicalInfo: student.medicalInfo ?? '',
    studentCode: student.studentCode ?? '',
  }
}

function StudentProfilePage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { account } = useAuth()
  const { data: student, isLoading, isError, error } = useStudent(id)
  const updateStudent = useUpdateStudent(id)
  const deleteStudent = useDeleteStudent()

  const isStaff = account?.role === 'TEACHER' || account?.role === 'ADMIN'
  const [editing, setEditing] = useState(false)
  const [form, setForm] = useState(null)
  const [formError, setFormError] = useState('')

  function startEditing() {
    setForm(formFromStudent(student))
    setFormError('')
    setEditing(true)
  }

  function handleChange(field) {
    return (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')
    try {
      const payload = isStaff
        ? { ...form, studentCode: form.studentCode || null, medicalInfo: form.medicalInfo || null }
        : { emergencyContact: form.emergencyContact, medicalInfo: form.medicalInfo || null }
      await updateStudent.mutateAsync(payload)
      setEditing(false)
    } catch (err) {
      setFormError(err.response?.data?.message || 'Could not save changes. Please try again.')
    }
  }

  async function handleDelete() {
    if (!window.confirm(`Remove ${student.fullName}'s profile? This can't be undone from here.`)) return
    await deleteStudent.mutateAsync(student.id)
    navigate('/students', { replace: true })
  }

  if (isLoading) {
    return (
      <div className="page-shell">
        <p className="empty-state">Loading student…</p>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="page-shell">
        <Link className="back-link" to={dashboardPathFor(account?.role)}>← Back to dashboard</Link>
        <p className="empty-state">
          {error?.response?.status === 403
            ? "You don't have access to this student's profile."
            : 'Student not found.'}
        </p>
      </div>
    )
  }

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <Link className="back-link" to={isStaff ? '/students' : dashboardPathFor(account?.role)}>
            ← Back {isStaff ? 'to students' : 'to dashboard'}
          </Link>
          <h1 className="page-title">{student.fullName}</h1>
        </div>
        <div style={{ display: 'flex', gap: 12 }}>
          {!editing && (
            <button type="button" className="btn btn-primary" onClick={startEditing}>
              Edit
            </button>
          )}
          {isStaff && (
            <button type="button" className="btn btn-danger" onClick={handleDelete}>
              Remove
            </button>
          )}
        </div>
      </div>

      <div className="card">
        {!editing ? (
          <div className="profile-grid">
            <div>
              <div className="profile-field-label">Date of Birth</div>
              <div className="profile-field-value">{student.dateOfBirth}</div>
            </div>
            <div>
              <div className="profile-field-label">Gender</div>
              <div className="profile-field-value">{student.gender}</div>
            </div>
            <div>
              <div className="profile-field-label">Parent</div>
              <div className="profile-field-value">{student.parentName}</div>
            </div>
            <div>
              <div className="profile-field-label">Classroom</div>
              <div className="profile-field-value">{student.className ?? 'Not assigned'}</div>
            </div>
            <div>
              <div className="profile-field-label">Emergency Contact</div>
              <div className="profile-field-value">{student.emergencyContact}</div>
            </div>
            <div>
              <div className="profile-field-label">Student Code</div>
              <div className="profile-field-value">{student.studentCode ?? '—'}</div>
            </div>
            <div className="form-grid-full">
              <div className="profile-field-label">Medical Info</div>
              <div className="profile-field-value">{student.medicalInfo || 'None on file'}</div>
            </div>
          </div>
        ) : (
          <form className="form-grid" onSubmit={handleSubmit}>
            {isStaff && (
              <>
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
                  <label className="field-label" htmlFor="dateOfBirth">Date of Birth</label>
                  <input
                    id="dateOfBirth"
                    type="date"
                    className="text-input"
                    value={form.dateOfBirth}
                    onChange={handleChange('dateOfBirth')}
                    required
                  />
                </div>
                <div>
                  <label className="field-label" htmlFor="gender">Gender</label>
                  <select id="gender" className="select-input" value={form.gender} onChange={handleChange('gender')}>
                    <option value="MALE">Male</option>
                    <option value="FEMALE">Female</option>
                  </select>
                </div>
                <div>
                  <label className="field-label" htmlFor="studentCode">Student Code</label>
                  <input
                    id="studentCode"
                    className="text-input"
                    value={form.studentCode}
                    onChange={handleChange('studentCode')}
                  />
                </div>
              </>
            )}

            <div>
              <label className="field-label" htmlFor="emergencyContact">Emergency Contact</label>
              <input
                id="emergencyContact"
                className="text-input"
                value={form.emergencyContact}
                onChange={handleChange('emergencyContact')}
                required
              />
            </div>

            <div className="form-grid-full">
              <label className="field-label" htmlFor="medicalInfo">Medical Info</label>
              <textarea
                id="medicalInfo"
                className="text-input"
                rows={3}
                value={form.medicalInfo}
                onChange={handleChange('medicalInfo')}
              />
            </div>

            {formError && <p className="form-error">{formError}</p>}

            <div className="form-actions">
              <button type="submit" className="btn btn-primary" disabled={updateStudent.isPending}>
                {updateStudent.isPending ? 'Saving…' : 'Save Changes'}
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

export default StudentProfilePage
