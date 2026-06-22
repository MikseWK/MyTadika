import { statusClass } from './statusBadge'

function HealthSummaryCard({ record }) {
  if (!record) {
    return (
      <div className="card">
        <p className="empty-state">No health measurements recorded yet.</p>
      </div>
    )
  }

  return (
    <div className="card">
      <h2 className="section-title">Latest Measurement</h2>
      <div className="profile-grid">
        <div>
          <div className="profile-field-label">Weight</div>
          <div className="profile-field-value">{record.weightKg} kg</div>
        </div>
        <div>
          <div className="profile-field-label">Height</div>
          <div className="profile-field-value">{record.heightCm} cm</div>
        </div>
        <div>
          <div className="profile-field-label">BMI</div>
          <div className="profile-field-value">{record.bmi?.toFixed(1) ?? '—'}</div>
        </div>
        <div>
          <div className="profile-field-label">Status</div>
          <div>
            <span className={statusClass(record.nutritionStatus)}>{record.nutritionStatus ?? 'unknown'}</span>
          </div>
        </div>
        {record.muacCm != null && (
          <div>
            <div className="profile-field-label">MUAC</div>
            <div className="profile-field-value">{record.muacCm} cm</div>
          </div>
        )}
        <div>
          <div className="profile-field-label">Recorded</div>
          <div className="profile-field-value">{new Date(record.recordedAt).toLocaleDateString()}</div>
        </div>
      </div>
    </div>
  )
}

export default HealthSummaryCard
