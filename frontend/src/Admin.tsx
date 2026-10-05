import { useState } from 'react'
import { api, type Dispute, type User } from './api'
import { Err, useAction, useAsync } from './ui'

export default function Admin() {
  const disputes = useAsync(() => api<Dispute[]>('/admin/disputes'))
  const users = useAsync(() => api<User[]>('/admin/users'))
  const act = useAction(() => { disputes.reload(); users.reload() })
  const [note, setNote] = useState('')
  return (
    <div className="space-y-4">
      <h3 className="font-semibold">Open disputes</h3>
      {disputes.data?.length === 0 && <p className="text-sm text-slate-500">No open disputes.</p>}
      {disputes.data?.map(d => (
        <div key={d.id} className="card text-sm">
          <p className="font-medium">{d.kind} · parcel {d.parcelId.slice(0, 8)}</p>
          <p className="text-slate-600">{d.description}</p>
          <input className="input my-2" placeholder="Resolution note" value={note} onChange={e => setNote(e.target.value)} />
          <div className="flex gap-2">
            <button className="btn-danger" onClick={() => act.run(() => api(`/admin/disputes/${d.id}/resolve`, 'POST', { decision: 'REFUND_SENDER', note }))}>Refund sender</button>
            <button className="btn" onClick={() => act.run(() => api(`/admin/disputes/${d.id}/resolve`, 'POST', { decision: 'RELEASE_TO_TRAVELLER', note }))}>Pay traveller</button>
          </div>
        </div>
      ))}
      <h3 className="font-semibold">Users</h3>
      <div className="card divide-y text-sm">
        {users.data?.map(u => (
          <div key={u.id} className="flex items-center justify-between py-2">
            <span>{u.fullName} · {u.email} · {u.role} · KYC {u.kycStatus}</span>
            {u.role !== 'ADMIN' && <button className="btn-ghost" onClick={() => act.run(() => api(`/admin/users/${u.id}/enabled`, 'POST', { enabled: false }))}>Disable</button>}
          </div>
        ))}
      </div>
      <Err msg={disputes.error || users.error || act.error} />
    </div>
  )
}
