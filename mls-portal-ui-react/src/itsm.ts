// The ITSM service's API (its own service, own address: config.itsmApiUrl). Contract: mls-itsm-api/openapi/tickets.v1.yaml.

export type State = 'RAISED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'PROVISIONING' | 'ACTIVE' | 'TESTING' | 'REPORTED' | 'CLOSED';

export interface Build { name: string; sha256: string; size: number }
export interface Ticket {
  id: string; reference: string; number: number; title: string; description: string; requester: string; requesterName: string | null;
  state: State; config: string; ttlHours: number; build: Build; environmentRef: string | null; createdAt: string; updatedAt: string;
}
export interface TicketEvent { id: number; at: string; actor: string; action: string; from: State | null; to: State | null; comment: string | null }
export interface Detail { ticket: Ticket; history: TicketEvent[]; canDecide: boolean }

export class Unauthorized extends Error {}
export class NotAvailable extends Error {}
/** A refusal with a reason the person can read (the service writes them for people). */
export class Refused extends Error {
  constructor(public status: number, message: string) { super(message); }
}

export interface RaiseForm { title: string; description: string; config: string; ttlHours: number; build: File }

export function client(base: string, token: string) {
  async function call<T>(method: string, path: string, body?: FormData | object): Promise<T> {
    const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
    let payload: BodyInit | undefined;
    if (body instanceof FormData) payload = body;               // the browser sets the multipart boundary itself
    else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }
    const res = await fetch(`${base}${path}`, { method, headers, body: payload });
    if (res.status === 401) throw new Unauthorized();
    if (res.status === 404) throw new NotAvailable();
    if (!res.ok) {
      let detail = `The server answered ${res.status}`;
      try { const p = (await res.json()) as { detail?: string }; if (p.detail) detail = p.detail; } catch { /* keep the generic text */ }
      throw new Refused(res.status, detail);
    }
    return (await res.json()) as T;
  }
  return {
    configurations: () => call<string[]>('GET', '/configurations'),
    list: (scope: 'mine' | 'all' = 'mine') => call<Ticket[]>('GET', `/tickets?scope=${scope}`),
    queue: () => call<Ticket[]>('GET', '/approvals'),
    get: (id: string) => call<Detail>('GET', `/tickets/${id}`),
    raise: (f: RaiseForm) => {
      const data = new FormData();
      data.set('title', f.title); data.set('description', f.description); data.set('config', f.config); data.set('ttlHours', String(f.ttlHours)); data.set('build', f.build);
      return call<Ticket>('POST', '/tickets', data);
    },
    approve: (id: string, comment: string) => call<Ticket>('POST', `/tickets/${id}/approve`, { comment }),
    reject: (id: string, comment: string) => call<Ticket>('POST', `/tickets/${id}/reject`, { comment }),
    refresh: (id: string) => call<Ticket>('POST', `/tickets/${id}/refresh`),
    move: (id: string, to: State, comment: string) => call<Ticket>('POST', `/tickets/${id}/transitions`, { to, comment }),
  };
}

export type ItsmClient = ReturnType<typeof client>;
