import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { supabase } from '../services/supabaseClient'
import { authApi } from '../services/api/authApi'

const AuthContext = createContext(undefined)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null)
  const [account, setAccount] = useState(null)
  const [loading, setLoading] = useState(true)

  // First login: no local `account` row exists yet for this Supabase user.
  // Auto-create it from the signup metadata rather than requiring a separate
  // "finish setting up" screen — mirrors CLAUDE.md 7.3's complete-profile step.
  const loadAccount = useCallback(async (currentSession) => {
    if (!currentSession) {
      setAccount(null)
      return
    }
    try {
      setAccount(await authApi.me())
    } catch (err) {
      if (err.response?.status === 404) {
        const meta = currentSession.user.user_metadata ?? {}
        try {
          setAccount(
            await authApi.completeProfile({
              fullName: meta.full_name ?? currentSession.user.email,
              phoneNumber: meta.phone_number,
            }),
          )
        } catch {
          setAccount(null)
        }
      } else {
        setAccount(null)
      }
    }
  }, [])

  useEffect(() => {
    let active = true

    supabase.auth.getSession().then(async ({ data }) => {
      if (!active) return
      setSession(data.session)
      await loadAccount(data.session)
      if (active) setLoading(false)
    })

    const { data: listener } = supabase.auth.onAuthStateChange(async (_event, newSession) => {
      setSession(newSession)
      setLoading(true)
      await loadAccount(newSession)
      setLoading(false)
    })

    return () => {
      active = false
      listener.subscription.unsubscribe()
    }
  }, [loadAccount])

  const signOut = useCallback(() => supabase.auth.signOut(), [])

  return (
    <AuthContext.Provider value={{ session, account, loading, signOut, setAccount }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components -- hook must live next to its Provider
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
