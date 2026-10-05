import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { api, ApiError, kg, rupees, type Parcel, type ParcelEvent } from './api'

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState('')
  const reload = useCallback(() => fn().then(d => { setData(d); setError('') }).catch(e => setError(e.message)), deps) // eslint-disable-line
  useEffect(() => { reload() }, [reload])
  return { data, error, reload }
}

/** Runs an action, surfaces errors, then refreshes. */
export function useAction(after: () => void) {
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError('')
    try { await fn(); after() } catch (e) { setError(e instanceof ApiError ? e.message : 'Something went wrong') }
    finally { setBusy(false) }
  }
  return { run, error, busy }
}

export const Err = ({ msg }: { msg?: string }) => msg ? <p className="mt-2 rounded bg-rose-50 px-3 py-2 text-sm text-rose-700">{msg}</p> : null

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="block"><span className="label">{label}</span>{children}</label>
}

const tone: Record<string, string> = {
  CREATED: 'bg-slate-100 text-slate-700', REQUESTED: 'bg-amber-100 text-amber-800', ACCEPTED: 'bg-blue-100 text-blue-800',
  PICKED_UP: 'bg-indigo-100 text-indigo-800', DELIVERED: 'bg-teal-100 text-teal-800', COMPLETED: 'bg-emerald-100 text-emerald-800',
  CANCELLED: 'bg-slate-200 text-slate-600', DISPUTED: 'bg-rose-100 text-rose-800', REFUNDED: 'bg-purple-100 text-purple-800',
}
export const Badge = ({ s }: { s: string }) =>
  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${tone[s] ?? 'bg-slate-100'}`}>{s.replace('_', ' ')}</span>

export function Timeline({ parcelId }: { parcelId: string }) {
  const { data } = useAsync(() => api<ParcelEvent[]>(`/parcels/${parcelId}/events`), [parcelId])
  return (
    <ol className="mt-3 space-y-1 border-l-2 border-slate-200 pl-3 text-xs text-slate-600">
      {data?.map((e, i) => (
        <li key={i}><span className="font-medium">{e.type.replace('_', ' ')}</span> · {new Date(e.createdAt).toLocaleString()}{e.note ? ` · ${e.note}` : ''}</li>
      ))}
    </ol>
  )
}

export function ParcelSummary({ p }: { p: Parcel }) {
  return (
    <div>
      <div className="flex items-center justify-between gap-2">
        <p className="font-medium">{p.description}</p><Badge s={p.status} />
      </div>
      <p className="text-sm text-slate-600">{p.originCity} → {p.destinationCity} · {kg(p.weightG)} · reward {rupees(p.rewardPaise)} · value {rupees(p.declaredValuePaise)}</p>
      <p className="text-xs text-slate-500">
        To {p.recipientName}{p.recipientPhone ? ` (${p.recipientPhone})` : ''}
        {p.travellerName ? ` · traveller ${p.travellerName}` : ''}{p.senderName ? ` · sender ${p.senderName}` : ''}
      </p>
    </div>
  )
}

export function DisputeButton({ parcelId, onDone }: { parcelId: string; onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [kind, setKind] = useState('DAMAGED')
  const [description, setDescription] = useState('')
  const { run, error } = useAction(() => { setOpen(false); onDone() })
  if (!open) return <button className="btn-ghost" onClick={() => setOpen(true)}>Report a problem</button>
  return (
    <div className="mt-2 space-y-2 rounded-lg bg-rose-50 p-3">
      <select className="input" value={kind} onChange={e => setKind(e.target.value)}>
        {['LOST', 'DAMAGED', 'NOT_DELIVERED', 'OTHER'].map(k => <option key={k}>{k}</option>)}
      </select>
      <textarea className="input" placeholder="What happened?" value={description} onChange={e => setDescription(e.target.value)} />
      <div className="flex gap-2">
        <button className="btn-danger" onClick={() => run(() => api(`/parcels/${parcelId}/disputes`, 'POST', { kind, description }))}>Open dispute</button>
        <button className="btn-ghost" onClick={() => setOpen(false)}>Cancel</button>
      </div>
      <Err msg={error} />
    </div>
  )
}

export function RateButton({ parcelId, onDone }: { parcelId: string; onDone: () => void }) {
  const [score, setScore] = useState(5)
  const { run, error } = useAction(onDone)
  return (
    <div className="mt-2 flex items-center gap-2">
      <select className="input !w-auto" value={score} onChange={e => setScore(+e.target.value)}>
        {[5, 4, 3, 2, 1].map(n => <option key={n} value={n}>{'★'.repeat(n)}</option>)}
      </select>
      <button className="btn-ghost" onClick={() => run(() => api(`/parcels/${parcelId}/ratings`, 'POST', { score }))}>Rate</button>
      <Err msg={error} />
    </div>
  )
}
