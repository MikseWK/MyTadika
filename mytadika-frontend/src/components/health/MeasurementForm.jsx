import { useState } from 'react'
import { useAuth } from '../../context/AuthContext'
import { useRecordMeasurement } from '../../hooks/useHealthRecords'

function ageInMonths(dateOfBirth) {
  const dob = new Date(dateOfBirth)
  const now = new Date()
  let months = (now.getFullYear() - dob.getFullYear()) * 12 + (now.getMonth() - dob.getMonth())
  if (now.getDate() < dob.getDate()) months -= 1
  return Math.max(months, 0)
}

const initialState = { weightKg: '', heightCm: '', muacCm: '', activityLevel: '1' }

function MeasurementForm({ student }) {
  const { account } = useAuth()
  const recordMeasurement = useRecordMeasurement(student.id)
  const [form, setForm] = useState(initialState)
  const [error, setError] = useState('')

  function handleChange(field) {
    return (event) => setForm((prev) => ({ ...prev, [field]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    try {
      await recordMeasurement.mutateAsync({
        childId: String(student.id),
        ageMonths: ageInMonths(student.dateOfBirth),
        weightKg: Number(form.weightKg),
        heightCm: Number(form.heightCm),
        muacCm: form.muacCm ? Number(form.muacCm) : null,
        gender: student.gender,
        activityLevel: Number(form.activityLevel),
        recordedBy: account?.id,
      })
      setForm(initialState)
    } catch (err) {
      setError(err.response?.data?.message || 'Please enter valid numerical values for height and weight.')
    }
  }

  return (
    <div className="card">
      <h2 className="section-title">Log New Measurement</h2>
      <form className="form-grid" onSubmit={handleSubmit}>
        <div>
          <label className="field-label" htmlFor="weightKg">Weight (kg)</label>
          <input
            id="weightKg"
            type="number"
            step="0.1"
            min="2"
            max="30"
            className="text-input"
            value={form.weightKg}
            onChange={handleChange('weightKg')}
            required
          />
        </div>
        <div>
          <label className="field-label" htmlFor="heightCm">Height (cm)</label>
          <input
            id="heightCm"
            type="number"
            step="0.1"
            min="45"
            max="130"
            className="text-input"
            value={form.heightCm}
            onChange={handleChange('heightCm')}
            required
          />
        </div>
        <div>
          <label className="field-label" htmlFor="muacCm">MUAC (cm, optional)</label>
          <input
            id="muacCm"
            type="number"
            step="0.1"
            className="text-input"
            value={form.muacCm}
            onChange={handleChange('muacCm')}
          />
        </div>
        <div>
          <label className="field-label" htmlFor="activityLevel">Activity Level</label>
          <select
            id="activityLevel"
            className="select-input"
            value={form.activityLevel}
            onChange={handleChange('activityLevel')}
          >
            <option value="0">Sedentary</option>
            <option value="1">Normal</option>
            <option value="2">Highly Active</option>
          </select>
        </div>

        {error && <p className="form-error">{error}</p>}

        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={recordMeasurement.isPending}>
            {recordMeasurement.isPending ? 'Saving…' : 'Save Measurement'}
          </button>
        </div>
      </form>
    </div>
  )
}

export default MeasurementForm
