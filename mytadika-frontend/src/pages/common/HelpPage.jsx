import '../students/students.css'

const FAQS = [
  {
    question: 'How do I update my child\'s profile?',
    answer: "Go to your child's profile from the Home page and click Edit. Some fields (like name and date of birth) can only be changed by your child's teacher.",
  },
  {
    question: 'Where can I see my child\'s academic results?',
    answer: 'Open Academic Report from the sidebar, or visit your child\'s profile and choose "Academic Records".',
  },
  {
    question: 'How does the AI nutrition advice work?',
    answer: 'After a height/weight measurement is logged, the system predicts a nutrition status and generates dietary and activity suggestions. It is not a medical diagnosis — always consult a healthcare professional for concerns.',
  },
  {
    question: 'Who do I contact for other questions?',
    answer: 'Reach out to your child\'s classroom teacher through the school office in the meantime — in-app messaging is on its way.',
  },
]

function HelpPage() {
  return (
    <div className="page-shell">
      <div className="page-header">
        <h1 className="page-title">Help</h1>
      </div>
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        {FAQS.map((faq) => (
          <div key={faq.question}>
            <div className="profile-field-value" style={{ marginBottom: 4 }}>{faq.question}</div>
            <p style={{ color: 'var(--color-text-muted)', margin: 0 }}>{faq.answer}</p>
          </div>
        ))}
      </div>
    </div>
  )
}

export default HelpPage
