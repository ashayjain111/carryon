// Thin fetch wrapper. The JWT lives in memory + localStorage; see docs/ARCHITECTURE.md (security) for the trade-off.
export type User = { id: string; email: string; fullName: string; phone?: string; role: 'USER' | 'ADMIN'; kycStatus: string }
export type Trip = {
  id: string; travellerId: string; originCity: string; destinationCity: string; departureDate: string
  totalCapacityG: number; personalLuggageG: number; bookedG: number; availableG: number; status: string; travellerRating?: number
}
export type Parcel = {
  id: string; status: string; description: string; weightG: number; declaredValuePaise: number; rewardPaise: number
  originCity: string; destinationCity: string; recipientName: string; recipientPhone?: string; tripId?: string
  senderName?: string; travellerName?: string; pickupToken?: string; deliveryToken?: string; createdAt: string; deliveredAt?: string
}
export type ParcelEvent = { type: string; actorId?: string; note?: string; createdAt: string }
export type Notification = { id: string; parcelId?: string; message: string; read: boolean; createdAt: string }
export type Dispute = { id: string; parcelId: string; kind: string; description: string; status: string; createdAt: string }

// Dev: empty (Vite proxies /api). Production: set VITE_API_BASE to the deployed backend URL.
const API_BASE = import.meta.env.VITE_API_BASE ?? ''

let token: string | null = localStorage.getItem('token')
export const getToken = () => token
export function setToken(t: string | null) {
  token = t
  if (t) localStorage.setItem('token', t)
  else localStorage.removeItem('token')
}

export class ApiError extends Error {
  constructor(message: string, public status: number) { super(message) }
}

export async function api<T = unknown>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const res = await fetch(API_BASE + '/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && !path.startsWith('/auth/') ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    if (res.status === 401 && token && !path.startsWith('/auth/')) { setToken(null); location.reload() }
    const fields = data?.fields ? ' (' + Object.entries(data.fields).map(([k, v]) => `${k}: ${v}`).join(', ') + ')' : ''
    throw new ApiError((data?.error ?? res.statusText) + fields, res.status)
  }
  return data as T
}

export const rupees = (paise: number) => '₹' + (paise / 100).toLocaleString('en-IN')
export const kg = (g: number) => (g / 1000).toFixed(g % 1000 ? 1 : 0) + ' kg'
