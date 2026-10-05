import { useEffect, useState } from 'react'
import { api, getToken, setToken, type Notification, type User } from './api'
import Auth from './Auth'
import Send from './Send'
import Travel from './Travel'
import Admin from './Admin'

type Tab = 'send' | 'travel' | 'admin'

function Bell() {
  const [items, setItems] = useState<Notification[]>([])
  const [open, setOpen] = useState(false)
  // Polling is enough: parcel status changes are infrequent. See docs/ARCHITECTURE.md (real-time).
  useEffect(() => {
    const load = () => api<Notification[]>('/notifications').then(setItems).catch(() => {})
    load()
    const id = setInterval(load, 20000)
    return () => clearInterval(id)
  }, [])
  const unread = items.filter(n => !n.read).length
  const toggle = () => {
    setOpen(!open)
    if (!open && unread) api('/notifications/read-all', 'POST').then(() => api<Notification[]>('/notifications').then(setItems))
  }
  return (
    <div className="relative">
      <button className="btn-ghost" onClick={toggle}>Alerts{unread > 0 && <span className="ml-1 rounded-full bg-rose-600 px-1.5 text-xs text-white">{unread}</span>}</button>
      {open && (
        <div className="absolute right-0 z-10 mt-2 max-h-80 w-72 overflow-auto rounded-xl border bg-white p-2 shadow-lg">
          {items.length === 0 && <p className="p-2 text-sm text-slate-500">No notifications.</p>}
          {items.map(n => <p key={n.id} className="border-b p-2 text-sm last:border-0">{n.message}<br /><span className="text-xs text-slate-400">{new Date(n.createdAt).toLocaleString()}</span></p>)}
        </div>
      )}
    </div>
  )
}

export default function App() {
  const [user, setUser] = useState<User | null>(null)
  const [ready, setReady] = useState(!getToken())
  const [tab, setTab] = useState<Tab>('send')

  const refreshUser = () => api<User>('/users/me').then(setUser)
  useEffect(() => { if (getToken()) refreshUser().catch(() => setToken(null)).finally(() => setReady(true)) }, [])

  if (!ready) return null
  if (!user) return <Auth onAuth={u => { setUser(u); setTab(u.role === 'ADMIN' ? 'admin' : 'send') }} />

  const tabs: [Tab, string][] = user.role === 'ADMIN' ? [['admin', 'Admin']] : [['send', 'Send a parcel'], ['travel', 'I\'m travelling']]
  return (
    <div className="mx-auto max-w-3xl px-4 pb-16">
      <header className="flex items-center justify-between py-4">
        <h1 className="text-xl font-bold text-indigo-700">Carryon</h1>
        <div className="flex items-center gap-2">
          <span className="hidden text-sm text-slate-600 sm:inline">{user.fullName}</span>
          <Bell />
          <button className="btn-ghost" onClick={() => { setToken(null); setUser(null) }}>Log out</button>
        </div>
      </header>
      <nav className="mb-4 flex gap-2">
        {tabs.map(([k, label]) => (
          <button key={k} onClick={() => setTab(k)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium ${tab === k ? 'bg-indigo-600 text-white' : 'bg-white text-slate-700 border'}`}>{label}</button>
        ))}
      </nav>
      {tab === 'send' && <Send />}
      {tab === 'travel' && <Travel user={user} refreshUser={refreshUser} />}
      {tab === 'admin' && <Admin />}
    </div>
  )
}
