import { Link, useParams } from 'react-router-dom'
import { useStudent } from '../../hooks/useStudents'
import { useHealthHistory, useLatestAdvice } from '../../hooks/useHealthRecords'
import AllergyAlertBanner from '../../components/health/AllergyAlertBanner'
import HealthSummaryCard from '../../components/health/HealthSummaryCard'
import AIAdvicePanel from '../../components/health/AIAdvicePanel'
import MeasurementForm from '../../components/health/MeasurementForm'
import HistoryTimeline from '../../components/health/HistoryTimeline'
import '../students/students.css'
import './health.css'

function HealthTrackerPage() {
  const { studentId } = useParams()
  const { data: student } = useStudent(studentId)
  const { data: history, isLoading: historyLoading, isError: historyError } = useHealthHistory(studentId)
  const { data: advice, isLoading: adviceLoading } = useLatestAdvice(studentId)

  const latestRecord = history?.[0] ?? null

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <Link className="back-link" to={`/students/${studentId}`}>← Back to {student?.fullName ?? 'student'}</Link>
          <h1 className="page-title">Health &amp; Nutrition</h1>
        </div>
      </div>

      <AllergyAlertBanner studentId={studentId} canEdit />

      <AIAdvicePanel advice={advice} isLoading={adviceLoading} />

      <div style={{ height: 20 }} />

      <HealthSummaryCard record={latestRecord} />

      <div style={{ height: 20 }} />

      {student && <MeasurementForm student={student} />}

      <div style={{ height: 20 }} />

      {historyLoading && (
        <div className="card">
          <p className="empty-state">Loading history…</p>
        </div>
      )}
      {historyError && (
        <div className="card">
          <p className="empty-state">Could not load measurement history. Please try again.</p>
        </div>
      )}
      {!historyLoading && !historyError && <HistoryTimeline records={history} />}
    </div>
  )
}

export default HealthTrackerPage
