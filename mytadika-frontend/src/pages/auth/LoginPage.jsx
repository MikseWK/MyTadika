import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { supabase } from '../../services/supabaseClient'
import { useAuth } from '../../context/AuthContext'
import { dashboardPathFor } from '../../routes/roleRoutes'
import './LoginPage.css'

function mapAuthError(message) {
  if (message && message.toLowerCase().includes('invalid login credentials')) {
    return 'Incorrect email or password. Please try again.'
  }
  return message || 'Incorrect email or password. Please try again.'
}

function LoginPage() {
  const navigate = useNavigate()
  const { session, account } = useAuth()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (session && account) {
      navigate(dashboardPathFor(account.role), { replace: true })
    }
  }, [session, account, navigate])

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    const { error: signInError } = await supabase.auth.signInWithPassword({ email, password })
    setSubmitting(false)
    if (signInError) setError(mapAuthError(signInError.message))
  }

  async function handleGoogleLogin() {
    setError('')
    const { error: oauthError } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: window.location.origin },
    })
    if (oauthError) setError(oauthError.message)
  }

  return (
    <div className="login-screen">
      <div className="login-panel-brand">
        <div className="brand-logo">
          <span className="brand-logo-icon">☀️</span>
          <span className="brand-logo-text">MyTadika</span>
        </div>

        <h1 className="brand-heading">
          Nurturing <span className="brand-heading-accent">Brilliance</span> with Every Smile.
        </h1>
        <p className="brand-copy">
          The sunniest corner for educators and parents to celebrate every
          magical milestone in your child's early adventure.
        </p>

        <div className="brand-illustration">
          <div className="brand-illustration-card brand-illustration-card-1">📖</div>
          <div className="brand-illustration-card brand-illustration-card-2">🎨</div>
        </div>

        <p className="brand-footer">© 2026 MyTadika. Grow with us.</p>
      </div>

      <div className="login-panel-form">
        <div className="login-card">
          <h2 className="login-title">Welcome Back!</h2>
          <p className="login-subtitle">Step inside the garden of learning</p>

          <form onSubmit={handleSubmit}>
            <label className="field-label" htmlFor="email">Email Address</label>
            <input
              id="email"
              type="email"
              className="text-input"
              placeholder="hello@example.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />

            <div className="field-row">
              <label className="field-label" htmlFor="password">Password</label>
              <a className="link-muted" href="#forgot-password">Forgot?</a>
            </div>
            <div className="password-input-wrap">
              <input
                id="password"
                type={showPassword ? 'text' : 'password'}
                className="text-input"
                placeholder="Your secret code"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                required
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? 'Hide password' : 'Show password'}
              >
                {showPassword ? '🙈' : '👁️'}
              </button>
            </div>

            {error && <p className="form-error">{error}</p>}

            <button type="submit" className="submit-button" disabled={submitting}>
              {submitting ? 'Signing in…' : 'Sign In to MyTadika'}
            </button>
          </form>

          <button type="button" className="google-button" onClick={handleGoogleLogin}>
            Continue with Google
          </button>

          <p className="signup-prompt">
            New to MyTadika? <Link className="link-accent" to="/register">Create Account</Link>
          </p>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
