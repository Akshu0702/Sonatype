import type { Me } from './menu';

export interface Vpc { id: string; name: string; cidr: string; function: string | null }
export interface Instance { id: string; name: string; type: string; state: string; privateIp: string | null; zone: string; function: string | null; launched: string }
export interface Environment { name: string; vpcs: Vpc[]; instances: Instance[]; running: number }
export interface Overview { source: string; region: string; observedAt: string; environments: Environment[] }

export class Unauthorized extends Error {}
export class NotAvailable extends Error {}

async function get<T>(path: string, token: string): Promise<T> {
  const res = await fetch(`/api/v1${path}`, { headers: { Authorization: `Bearer ${token}` } });
  if (res.status === 401) throw new Unauthorized();
  if (res.status === 404) throw new NotAvailable();
  if (!res.ok) throw new Error(`The server answered ${res.status}`);
  return (await res.json()) as T;
}

export const me = (t: string) => get<Me>('/me', t);
export const environments = (t: string) => get<Overview>('/environments', t);
