import { useState } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import { api, kg, type Parcel, type Trip } from './api'
import { DisputeButton, Err, Field, ParcelSummary, RateButton, Timeline, useAction, useAsync } from './ui'

function NewParcel({ onCreated }: { onCreated: () => void }) {
  const blank = { description: '', weightKg: '1', valueRs: '1000', rewardRs: '500', originCity: '', destinationCity: '', recipientName: '', recipientPhone: '', noProhibitedItems: false }
  const [f, setF] = useState(blank)
  const { run, error, busy } = useAction(() => { setF(blank); onCreated() })
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    run(() => api('/parcels', 'POST', {
      description: f.description, weightG: Math.round(+f.weightKg * 1000), declaredValuePaise: Math.round(+f.valueRs * 100),
      rewardPaise: Math.round(+f.rewardRs * 100), originCity: f.originCity, destinationCity: f.destinationCity,
      recipientName: f.recipientName, recipientPhone: f.recipientPhone, noProhibitedItems: f.noProhibitedItems,
    }))
  }
  return (
    <form onSubmit={submit} className="card grid gap-3 sm:grid-cols-2">
      <h3 className="font-semibold sm:col-span-2">New parcel</h3>
      <div className="sm:col-span-2"><Field label="What are you sending?"><input className="input" required value={f.description} onChange={set('description')} /></Field></div>
      <Field label="From city"><input className="input" required value={f.originCity} onChange={set('originCity')} /></Field>
      <Field label="To city"><input className="input" required value={f.destinationCity} onChange={set('destinationCity')} /></Field>
      <Field label="Weight (kg)"><input className="input" type="number" step="0.1" min="0.1" required value={f.weightKg} onChange={set('weightKg')} /></Field>
      <Field label="Declared value (₹)"><input className="input" type="number" min="0" required value={f.valueRs} onChange={set('valueRs')} /></Field>
      <Field label="Reward for traveller (₹)"><input className="input" type="number" min="1" required value={f.rewardRs} onChange={set('rewardRs')} /></Field>
      <span />
      <Field label="Recipient name"><input className="input" required value={f.recipientName} onChange={set('recipientName')} /></Field>
      <Field label="Recipient phone"><input className="input" required value={f.recipientPhone} onChange={set('recipientPhone')} placeholder="+91..." /></Field>
      <label className="flex items-center gap-2 text-sm sm:col-span-2">
        <input type="checkbox" checked={f.noProhibitedItems} onChange={set('noProhibitedItems')} />
        I confirm this parcel contains no prohibited or illegal items.
      </label>
      <div className="sm:col-span-2"><button className="btn" disabled={busy}>Create parcel</button><Err msg={error} /></div>
    </form>
  )
}

function Matches({ parcel, onDone }: { parcel: Parcel; onDone: () => void }) {
  const { data, error } = useAsync(() => api<Trip[]>(`/parcels/${parcel.id}/matches`), [parcel.id])
  const act = useAction(onDone)
  return (
    <div className="mt-3 space-y-2">
      <p className="text-sm font-medium">Matching travellers</p>
      {data?.length === 0 && <p className="text-sm text-slate-500">No matching trips yet. Check back soon.</p>}
      {data?.map(t => (
        <div key={t.id} className="flex items-center justify-between rounded-lg bg-slate-50 p-2 text-sm">
          <span>{t.departureDate} · {kg(t.availableG)} spare · {t.travellerRating ? `★ ${t.travellerRating.toFixed(1)}` : 'new traveller'}</span>
          <button className="btn" onClick={() => act.run(() => api(`/parcels/${parcel.id}/request`, 'POST', { tripId: t.id }))}>Request</button>
        </div>
      ))}
      <Err msg={error || act.error} />
    </div>
  )
}

function ParcelCard({ p, reload }: { p: Parcel; reload: () => void }) {
  const [showLog, setShowLog] = useState(false)
  const act = useAction(reload)
  const post = (path: string) => act.run(() => api(`/parcels/${p.id}/${path}`, 'POST'))
  return (
    <div className="card">
      <ParcelSummary p={p} />
      {p.status === 'CREATED' && <Matches parcel={p} onDone={reload} />}
      {['ACCEPTED', 'PICKED_UP'].includes(p.status) && p.pickupToken && (
        <div className="mt-3 flex flex-wrap gap-6">
          {p.status === 'ACCEPTED' && <Qr label="Pickup code: show to traveller" value={p.pickupToken} />}
          <Qr label="Delivery code: give to recipient" value={p.deliveryToken!} />
        </div>
      )}
      <div className="mt-3 flex flex-wrap gap-2">
        {['CREATED', 'REQUESTED', 'ACCEPTED'].includes(p.status) && <button className="btn-ghost" onClick={() => post('cancel')}>Cancel</button>}
        {p.status === 'DELIVERED' && <button className="btn" onClick={() => post('confirm')}>Confirm delivery & release payment</button>}
        {['ACCEPTED', 'PICKED_UP', 'DELIVERED'].includes(p.status) && <DisputeButton parcelId={p.id} onDone={reload} />}
        <button className="btn-ghost" onClick={() => setShowLog(!showLog)}>{showLog ? 'Hide' : 'Tracking'}</button>
      </div>
      {p.status === 'COMPLETED' && <RateButton parcelId={p.id} onDone={reload} />}
      {showLog && <Timeline parcelId={p.id} />}
      <Err msg={act.error} />
    </div>
  )
}

function Qr({ label, value }: { label: string; value: string }) {
  return (
    <div className="text-center">
      <QRCodeSVG value={value} size={110} />
      <p className="mt-1 text-xs text-slate-600">{label}</p>
      <p className="select-all break-all font-mono text-[10px] text-slate-500">{value}</p>
    </div>
  )
}

export default function Send() {
  const { data, error, reload } = useAsync(() => api<Parcel[]>('/parcels/sent'))
  return (
    <div className="space-y-4">
      <NewParcel onCreated={reload} />
      <Err msg={error} />
      {data?.length === 0 && <p className="text-sm text-slate-500">No parcels yet. Create your first one above.</p>}
      {data?.map(p => <ParcelCard key={p.id} p={p} reload={reload} />)}
    </div>
  )
}
