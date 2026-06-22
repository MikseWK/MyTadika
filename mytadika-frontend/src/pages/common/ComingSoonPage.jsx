import '../students/students.css'

function ComingSoonPage({ feature }) {
  return (
    <div className="page-shell">
      <div className="page-header">
        <h1 className="page-title">{feature}</h1>
      </div>
      <div className="card">
        <p className="empty-state">
          {feature} is being built by the Parent Engagement &amp; Communication team. Check back soon!
        </p>
      </div>
    </div>
  )
}

export default ComingSoonPage
