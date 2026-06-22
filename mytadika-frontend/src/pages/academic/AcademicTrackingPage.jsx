import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { useStudent } from '../../hooks/useStudents'
import { useAcademicRecords, useCreateAcademicRecord, useUpdateAcademicRecord } from '../../hooks/useAcademicRecords'
import '../students/students.css'
import './academic.css'

const emptyScoreRow = () => ({ subjectName: '', score: '' })

function gradeClass(grade) {
  if (grade === 'A' || grade === 'B') return 'grade-badge grade-good'
  if (grade === 'C' || grade === 'D') return 'grade-badge grade-ok'
  return 'grade-badge grade-low'
}

function ScoreForm({ initialTerm = '', initialScores, submitting, error, onSubmit, onCancel, submitLabel }) {
  const [academicTerm, setAcademicTerm] = useState(initialTerm)
  const [scores, setScores] = useState(initialScores ?? [emptyScoreRow()])

  function updateScore(index, field, value) {
    setScores((prev) => prev.map((row, i) => (i === index ? { ...row, [field]: value } : row)))
  }

  function addRow() {
    setScores((prev) => [...prev, emptyScoreRow()])
  }

  function removeRow(index) {
    setScores((prev) => prev.filter((_, i) => i !== index))
  }

  function handleSubmit(event) {
    event.preventDefault()
    onSubmit({
      academicTerm,
      scores: scores.map((row) => ({ subjectName: row.subjectName, score: Number(row.score) })),
    })
  }

  return (
    <form className="form-grid" onSubmit={handleSubmit}>
      <div className="form-grid-full">
        <label className="field-label" htmlFor="academicTerm">Academic Term</label>
        <input
          id="academicTerm"
          className="text-input"
          placeholder="e.g. Term 1 - 2026"
          value={academicTerm}
          onChange={(event) => setAcademicTerm(event.target.value)}
          required
        />
      </div>

      <div className="form-grid-full">
        <label className="field-label">Subject Scores</label>
        <div className="score-rows">
          {scores.map((row, index) => (
            <div className="score-row" key={index}>
              <input
                className="text-input"
                placeholder="Subject"
                value={row.subjectName}
                onChange={(event) => updateScore(index, 'subjectName', event.target.value)}
                required
              />
              <input
                className="text-input score-input"
                type="number"
                min="0"
                max="100"
                placeholder="Score"
                value={row.score}
                onChange={(event) => updateScore(index, 'score', event.target.value)}
                required
              />
              <button
                type="button"
                className="btn btn-secondary score-remove"
                onClick={() => removeRow(index)}
                disabled={scores.length === 1}
              >
                Remove
              </button>
            </div>
          ))}
        </div>
        <button type="button" className="btn btn-secondary" onClick={addRow} style={{ marginTop: 12 }}>
          + Add Subject
        </button>
      </div>

      {error && <p className="form-error">{error}</p>}

      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={submitting}>
          {submitting ? 'Saving…' : submitLabel}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-secondary" onClick={onCancel}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}

function AcademicTrackingPage() {
  const { studentId } = useParams()
  const { account } = useAuth()
  const { data: student } = useStudent(studentId)
  const { data: records, isLoading, isError } = useAcademicRecords(studentId)
  const createRecord = useCreateAcademicRecord(studentId)
  const [editingId, setEditingId] = useState(null)
  const updateRecord = useUpdateAcademicRecord(studentId, editingId)
  const [showForm, setShowForm] = useState(false)
  const [formError, setFormError] = useState('')

  const isStaff = account?.role === 'TEACHER' || account?.role === 'ADMIN'
  const editingRecord = useMemo(
    () => records?.find((record) => record.id === editingId) ?? null,
    [records, editingId],
  )

  async function handleCreate(payload) {
    setFormError('')
    try {
      await createRecord.mutateAsync(payload)
      setShowForm(false)
    } catch (err) {
      setFormError(err.response?.data?.message || 'Could not save record. Please check the scores and try again.')
    }
  }

  async function handleUpdate(payload) {
    setFormError('')
    try {
      await updateRecord.mutateAsync(payload)
      setEditingId(null)
    } catch (err) {
      setFormError(err.response?.data?.message || 'Could not save record. Please check the scores and try again.')
    }
  }

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <Link className="back-link" to={`/students/${studentId}`}>← Back to {student?.fullName ?? 'student'}</Link>
          <h1 className="page-title">Academic Tracking</h1>
        </div>
        {isStaff && !showForm && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              setShowForm(true)
              setEditingId(null)
              setFormError('')
            }}
          >
            + New Record
          </button>
        )}
      </div>

      {isStaff && showForm && (
        <div className="card" style={{ marginBottom: 20 }}>
          <h2 className="section-title">New Academic Record</h2>
          <ScoreForm
            submitting={createRecord.isPending}
            error={formError}
            submitLabel="Save Record"
            onSubmit={handleCreate}
            onCancel={() => {
              setShowForm(false)
              setFormError('')
            }}
          />
        </div>
      )}

      {isStaff && editingRecord && (
        <div className="card" style={{ marginBottom: 20 }}>
          <h2 className="section-title">Edit {editingRecord.academicTerm}</h2>
          <ScoreForm
            initialTerm={editingRecord.academicTerm}
            initialScores={editingRecord.scores.map((s) => ({ subjectName: s.subjectName, score: s.score }))}
            submitting={updateRecord.isPending}
            error={formError}
            submitLabel="Update Record"
            onSubmit={handleUpdate}
            onCancel={() => {
              setEditingId(null)
              setFormError('')
            }}
          />
        </div>
      )}

      <div className="card">
        {isLoading && <p className="empty-state">Loading academic records…</p>}
        {isError && <p className="empty-state">Could not load academic records. Please try again.</p>}
        {!isLoading && !isError && records?.length === 0 && (
          <p className="empty-state">No academic records yet.</p>
        )}
        {!isLoading && !isError && records?.length > 0 && (
          <table className="student-table">
            <thead>
              <tr>
                <th>Term</th>
                <th>Average</th>
                <th>Grade</th>
                <th>Subjects</th>
                {isStaff && <th />}
              </tr>
            </thead>
            <tbody>
              {records.map((record) => (
                <tr key={record.id}>
                  <td>{record.academicTerm}</td>
                  <td>{record.averageMark.toFixed(1)}</td>
                  <td>
                    <span className={gradeClass(record.finalGrade)}>
                      {record.finalGrade} — {record.gradeLabel}
                    </span>
                  </td>
                  <td>{record.scores.map((s) => `${s.subjectName} (${s.score})`).join(', ')}</td>
                  {isStaff && (
                    <td>
                      <button
                        type="button"
                        className="link-accent"
                        style={{ background: 'none', border: 'none', cursor: 'pointer' }}
                        onClick={() => {
                          setEditingId(record.id)
                          setShowForm(false)
                          setFormError('')
                        }}
                      >
                        Edit
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default AcademicTrackingPage
