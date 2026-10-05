import { useState } from 'react'
import { api, kg, type Parcel, type Trip, type User } from './api'
import { Badge, DisputeButton, Err, Field, ParcelSummary, RateButton, Timeline, useAction, useAsync } from './ui'

function NewTrip({ onCreated }: { onCreated: () => void }) {
  const blank = { originCity: '', destinationCity: '', departureDate: '', totalKg: '20', personalKg: '12' }
  const [f, setF] = useState(blank)
  const { run, error, busy } = useAction(() => { setF(blank); onCreated() })
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value })
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    run(() => api('/trips', 'POST', {
      originCity: f.originCity, destinationCity: f.destinationCity, departureDate: f.departureDate,
      totalCapacityG: Math.round(+f.totalKg * 1000), personalLuggageG: Math.round(+f.personalKg * 1000),
    }))
  }
  return (
    <form onSubmit={submit} className="card grid gap-3 sm:grid-cols-2">
      <h3 className="font-semibold sm:col-span-2">Post a trip</h3>
      <Field label="From"><input className="input" required value={f.originCity} onChange={set('originCity')} /></Field>
      <Field label="To"><input className="input" required value={f.destinationCity} onChange={set('destinationCity')} /></Field>
      <Field label="Departure date"><input className="input" type="date" required value={f.departureDate} onChange={set('departureDate')} /></Field>
      <span />
      <Field label="Total luggage capacity (kg)"><input className="input" type="number" step="0.5" required value={f.totalKg} onChange={set('totalKg')} /></Field>
      <Field label="Your own luggage (kg)"><input className="input" type="number" step="0.5" required value={f.personalKg} onChange={set('personalKg')} /></Field>
      <div className="sm:col-span-2"><button className="btn" disabled={busy}>Post trip</button><Err msg={error} /></div>
    </form>
  )
}

function AssignedCard({ p, reload }: { p: Parcel; reload: () => void }) {
  const [token, setToken] = useState('')
  const [showLog, setShowLog] = useState(false)
  const act = useAction(reload)
  const post = (path: string, body?: unknown) => act.run(() => api(`/parcels/${p.id}/${path}`, 'POST', body))
  return (
    <div className="card">
      <ParcelSummary p={p} />
      <div className="mt-3 flex flex-wrap items-center gap-2">
        {p.status === 'REQUESTED' && <>
          <button className="btn" onClick={() => post('accept')}>Accept</button>
          <button className="btn-ghost" onClick={() => post('reject')}>Decline</button>
        </>}
        {(p.status === 'ACCEPTED' || p.status === 'PICKED_UP') && <>
          <input className="input !w-64 font-mono" placeholder={p.status === 'ACCEPTED' ? 'Paste pickup code' : 'Paste recipient delivery code'}
            value={token} onChange={e => setToken(e.target.value.trim())} />
          <button className="btn" onClick={() => post(p.status === 'ACCEPTED' ? 'pickup' : 'deliver', { token })}>
            {p.status === 'ACCEPTED' ? 'Confirm pickup' : 'Confirm delivery'}
          </button>
        </>}
        {['ACCEPTED', 'PICKED_UP', 'DELIVERED'].includes(p.status) && <DisputeButton parcelId={p.id} onDone={reload} />}
        <button className="btn-ghost" onClick={() => setShowLog(!showLog)}>{showLog ? 'Hide' : 'Tracking'}</button>
      </div>
      {p.status === 'COMPLETED' && <RateButton parcelId={p.id} onDone={reload} />}
      {showLog && <Timeline parcelId={p.id} />}
      <Err msg={act.error} />
    </div>
  )
}

export default function Travel({ user, refreshUser }: { user: User; refreshUser: () => void }) {
  const trips = useAsync(() => api<Trip[]>('/trips/mine'))
  const parcels = useAsync(() => api<Parcel[]>('/parcels/assigned'))
  const kyc = useAction(refreshUser)
  const cancel = useAction(trips.reload)

  if (user.kycStatus !== 'VERIFIED') {
    return (
      <div className="card">
        <h3 className="font-semibold">Verify your identity to travel with parcels</h3>
        <p className="my-2 text-sm text-slate-600">Demo mode: verification is simulated. In production this hands off to a KYC provider and we store only the result.</p>
        <button className="btn" onClick={() => kyc.run(() => api('/users/me/kyc/mock-verify', 'POST'))}>Verify (demo)</button>
        <Err msg={kyc.error} />
      </div>
    )
  }
  return (
    <div className="space-y-4">
      <NewTrip onCreated={trips.reload} />
      <h3 className="font-semibold">My trips</h3>
      {trips.data?.map(t => (
        <div key={t.id} className="card flex items-center justify-between text-sm">
          <div>
            <p className="font-medium">{t.originCity} → {t.destinationCity} · {t.departureDate}</p>
            <p className="text-slate-600">Spare {kg(t.availableG)} of {kg(t.totalCapacityG - t.personalLuggageG)} · booked {kg(t.bookedG)}</p>
          </div>
          <div className="flex items-center gap-2"><Badge s={t.status} />
            {t.status === 'OPEN' && <button className="btn-ghost" onClick={() => cancel.run(() => api(`/trips/${t.id}`, 'DELETE'))}>Cancel</button>}
          </div>
        </div>
      ))}
      <Err msg={trips.error || cancel.error} />
      <h3 className="font-semibold">Parcels on my trips</h3>
      {parcels.data?.length === 0 && <p className="text-sm text-slate-500">Nothing yet. Requests appear here.</p>}
      {parcels.data?.map(p => <AssignedCard key={p.id} p={p} reload={() => { parcels.reload(); trips.reload() }} />)}
    </div>
  )
}
