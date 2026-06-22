import { statusClass } from './statusBadge'

function HistoryTimeline({ records }) {
  if (!records?.length) {
    return (
      <div className="card">
        <p className="empty-state">No history yet.</p>
      </div>
    )
  }

  return (
    <div className="card">
      <h2 className="section-title">Measurement History</h2>
      <table className="student-table">
        <thead>
          <tr>
            <th>Date</th>
            <th>Weight</th>
            <th>Height</th>
            <th>BMI</th>
            <th>Status</th>
          </tr>
        </thead>
        <tbody>
          {records.map((record) => (
            <tr key={record.id}>
              <td>{new Date(record.recordedAt).toLocaleDateString()}</td>
              <td>{record.weightKg} kg</td>
              <td>{record.heightCm} cm</td>
              <td>{record.bmi?.toFixed(1) ?? '—'}</td>
              <td>
                <span className={statusClass(record.nutritionStatus)}>{record.nutritionStatus ?? 'unknown'}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default HistoryTimeline
