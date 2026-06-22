import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { supabase } from '../../services/supabaseClient'
import './LoginPage.css'

function RegisterPage() {
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [checkEmail, setCheckEmail] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)

    const { data, error: signUpError } = await supabase.auth.signUp({
      email,
      password,
      options: { data: { full_name: fullName, phone_number: phoneNumber } },
    })

    setSubmitting(false)
    if (signUpError) {
      setError(signUpError.message)
      return
    }

    // With email confirmation enabled, signUp returns no session yet — the
    // local `account` row is created on first real login instead (see
    // AuthContext's 404 handling).
    if (data.session) {
      navigate('/', { replace: true })
    } else {
      setCheckEmail(true)
    }
  }

  if (checkEmail) {
    return (
      <div className="login-screen">
        <div className="login-panel-form">
          <div className="login-card">
            <h2 className="login-title">Almost there</h2>
            <p className="login-subtitle">
              If {email} isn't already registered, we just sent a confirmation link to it —
              confirm it, then sign in. Already have an account with this email? Just{' '}
              <Link className="link-accent" to="/login">sign in</Link> instead (no email needed).
            </p>
            <Link className="link-accent" to="/login">Back to login</Link>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="login-screen">
      <div className="login-panel-brand">
        <div className="brand-logo">
          <span className="brand-logo-icon">☀️</span>
          <span className="brand-logo-text">MyTadika</span>
        </div>
        <h1 className="brand-heading">
          Join the <span className="brand-heading-accent">MyTadika</span> family.
        </h1>
        <p className="brand-copy">
          Create a parent account to follow your child's academic and health
          milestones.
        </p>
      </div>

      <div className="login-panel-form">
        <div className="login-card">
          <h2 className="login-title">Create your account</h2>
          <p className="login-subtitle">Parent sign-up — staff accounts are provisioned by an admin</p>

          <form onSubmit={handleSubmit}>
            <label className="field-label" htmlFor="fullName">Full Name</label>
            <input
              id="fullName"
              className="text-input"
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              required
            />

            <label className="field-label" htmlFor="email">Email Address</label>
            <input
              id="email"
              type="email"
              className="text-input"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />

            <label className="field-label" htmlFor="phoneNumber">Phone Number</label>
            <input
              id="phoneNumber"
              className="text-input"
              value={phoneNumber}
              onChange={(event) => setPhoneNumber(event.target.value)}
            />

            <label className="field-label" htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              className="text-input"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              minLength={6}
            />

            {error && <p className="form-error">{error}</p>}

            <button type="submit" className="submit-button" disabled={submitting}>
              {submitting ? 'Creating account…' : 'Create Account'}
            </button>
          </form>

          <p className="signup-prompt">
            Already have an account? <Link className="link-accent" to="/login">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}

export default RegisterPage
