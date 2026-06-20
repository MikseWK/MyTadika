import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useCreateStudent } from '../../hooks/useStudents'
import './students.css'

const initialState = {
  parentEmail: '',
  fullName: '',
  dateOfBirth: '',
  gender: 'MALE',
  emergencyContact: '',
  medicalInfo: '',
  studentCode: '',
}

function StudentFormPage() {
  const navigate = useNavigate()
  const createStudent = useCreateStudent()
  const [form, setForm] = useState(initialState)
  const [error, setError] = useState('')

  function handleChange(field) {
    return (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    try {
      const created = await createStudent.mutateAsync({
        ...form,
        studentCode: form.studentCode || null,
        medicalInfo: form.medicalInfo || null,
      })
      navigate(`/students/${created.id}`, { replace: true })
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create student. Please check the form and try again.')
    }
  }

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <Link className="back-link" to="/students">← Back to students</Link>
          <h1 className="page-title">Add Student</h1>
        </div>
      </div>

      <div className="card">
        <form className="form-grid" onSubmit={handleSubmit}>
          <div>
            <label className="field-label" htmlFor="parentEmail">Parent's Email</label>
            <input
              id="parentEmail"
              type="email"
              className="text-input"
              value={form.parentEmail}
              onChange={handleChange('parentEmail')}
              required
            />
          </div>

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
            <label className="field-label" htmlFor="emergencyContact">Emergency Contact</label>
            <input
              id="emergencyContact"
              className="text-input"
              value={form.emergencyContact}
              onChange={handleChange('emergencyContact')}
              required
            />
          </div>

          <div>
            <label className="field-label" htmlFor="studentCode">Student Code (optional)</label>
            <input
              id="studentCode"
              className="text-input"
              value={form.studentCode}
              onChange={handleChange('studentCode')}
            />
          </div>

          <div className="form-grid-full">
            <label className="field-label" htmlFor="medicalInfo">Medical Info (optional)</label>
            <textarea
              id="medicalInfo"
              className="text-input"
              rows={3}
              value={form.medicalInfo}
              onChange={handleChange('medicalInfo')}
            />
          </div>

          {error && <p className="form-error">{error}</p>}

          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={createStudent.isPending}>
              {createStudent.isPending ? 'Saving…' : 'Save Student'}
            </button>
            <Link className="btn btn-secondary" to="/students">Cancel</Link>
          </div>
        </form>
      </div>
    </div>
  )
}

export default StudentFormPage
