import { useState } from 'react'
import { api, setToken, type User } from './api'
import { Err, Field, useAction } from './ui'

export default function Auth({ onAuth }: { onAuth: (u: User) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [f, setF] = useState({ email: '', password: '', fullName: '', phone: '' })
  const { run, error, busy } = useAction(() => {})
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value })

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    run(async () => {
      const body = mode === 'login'
        ? { email: f.email.trim(), password: f.password }
        : { email: f.email.trim(), password: f.password, fullName: f.fullName.trim(), phone: f.phone.trim() || undefined }
      const r = await api<{ token: string; user: User }>(`/auth/${mode}`, 'POST', body)
      setToken(r.token); onAuth(r.user)
    })
  }

  return (
    <div className="mx-auto mt-16 max-w-sm px-4">
      <h1 className="text-2xl font-bold text-indigo-700">Carryon</h1>
      <p className="mb-4 text-sm text-slate-600">Send parcels with travellers already going your way.</p>
      <form onSubmit={submit} className="card space-y-3">
        {mode === 'register' && <>
          <Field label="Full name"><input className="input" required value={f.fullName} onChange={set('fullName')} /></Field>
          <Field label="Phone (optional)"><input className="input" value={f.phone} onChange={set('phone')} placeholder="+91 98765 43210" /></Field>
        </>}
        <Field label="Email"><input className="input" type="email" required autoComplete="email" value={f.email} onChange={set('email')} /></Field>
        <Field label="Password (min 8 characters)">
          <input className="input" type="password" required minLength={8} autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            value={f.password} onChange={set('password')} />
        </Field>
        <button className="btn w-full" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}</button>
        {busy && <p className="text-xs text-slate-500">The free server sleeps when idle, so the first request can take up to a minute.</p>}
        <Err msg={error} />
      </form>
      <button className="mt-3 text-sm text-indigo-700 underline" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
        {mode === 'login' ? 'New here? Create an account' : 'Have an account? Log in'}
      </button>
    </div>
  )
}
