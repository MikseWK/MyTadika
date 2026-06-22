function AdviceList({ title, items }) {
  if (!items?.length) return null
  return (
    <div className="advice-section">
      <h3 className="advice-subtitle">{title}</h3>
      <ul className="advice-list">
        {items.map((item) => (
          <li key={item.id} className="advice-card">
            <div className="advice-card-title">{item.title}</div>
            <p className="advice-card-body">{item.body}</p>
            {item.source && <div className="advice-card-source">{item.source}</div>}
          </li>
        ))}
      </ul>
    </div>
  )
}

function AllergyWarnings({ warnings }) {
  if (!warnings?.length) return null
  return (
    <div className="advice-allergy-warning">
      {warnings.map((warning) => (
        <div key={warning.allergen} className="allergy-warning-card">
          <div className="allergy-warning-title">⚠ {warning.allergen} allergy on file</div>
          <p>{warning.description}</p>
          {warning.safeSubstitutions?.length > 0 && (
            <ul>
              {warning.safeSubstitutions.map((sub) => (
                <li key={sub.id}>
                  Avoid <strong>{sub.avoid}</strong> — try <strong>{sub.substitute}</strong> instead.{' '}
                  {sub.rationale}
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  )
}

function AIAdvicePanel({ advice, isLoading }) {
  if (isLoading) {
    return (
      <div className="card">
        <p className="empty-state">Loading AI advice…</p>
      </div>
    )
  }

  if (!advice) {
    return (
      <div className="card">
        <p className="empty-state">No AI advice yet — record a measurement to generate one.</p>
      </div>
    )
  }

  return (
    <div className="card">
      <h2 className="section-title">AI Nutrition &amp; Activity Advice</h2>

      {advice.requiresUrgentReferral && (
        <div className="advice-urgent">
          This result suggests urgent attention — please consult a healthcare professional.
        </div>
      )}

      <AllergyWarnings warnings={advice.allergyWarnings} />

      {advice.confidenceCaveat && <p className="advice-caveat">{advice.confidenceCaveat}</p>}

      <AdviceList title="Dietary Advice" items={advice.dietaryAdvice} />
      <AdviceList title="Activity Advice" items={advice.activityAdvice} />

      <p className="advice-disclaimer">{advice.disclaimer}</p>
    </div>
  )
}

export default AIAdvicePanel
