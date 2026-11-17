// Rules for the ticket screens that need no browser. The service enforces every one of them again; these only keep people from walking into a refusal.
import type { Me } from './menu';
import type { State, Ticket } from './itsm';

export const MAX_BUILD_BYTES = 100 * 1024 * 1024;
export const MAX_TTL_HOURS = 72;

export const STATE_LABEL: Record<State, string> = {
  RAISED: 'Raised', PENDING_APPROVAL: 'Waiting for approval', APPROVED: 'Approved', REJECTED: 'Rejected', PROVISIONING: 'Building the environment',
  ACTIVE: 'Environment ready', TESTING: 'Testing', REPORTED: 'Report ready', CLOSED: 'Closed',
};

const has = (me: Me, feature: string) => me.features.includes(feature);

export const canRaise = (me: Me) => has(me, 'itsm.tickets.tickets_create_provisioning') && has(me, 'itsm.tickets.tickets_attach');
export const canSeeQueue = (me: Me) => has(me, 'itsm.approvals.approvals_queue');

/** The moves a person may ask for by hand (the service allows only these, and only in this order). */
const MOVES: Partial<Record<State, { to: State; label: string }>> = {
  ACTIVE: { to: 'TESTING', label: 'Start testing' },
  TESTING: { to: 'REPORTED', label: 'Mark the report ready' },
  REPORTED: { to: 'CLOSED', label: 'Close the ticket' },
  REJECTED: { to: 'CLOSED', label: 'Close the ticket' },
};

export function moveFor(me: Me, t: Pick<Ticket, 'state'>): { to: State; label: string } | null {
  return has(me, 'itsm.tickets.tickets_transition') ? MOVES[t.state] ?? null : null;
}

/** Approved and provisioning tickets move on when the environment is asked for or ready; "check" does that. */
export const needsCheck = (t: Pick<Ticket, 'state'>) => t.state === 'APPROVED' || t.state === 'PROVISIONING';

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export interface RaiseInput { title: string; config: string; ttlHours: number; file: { name: string; size: number } | null }

export function validateRaise(i: RaiseInput): string[] {
  const problems: string[] = [];
  const title = i.title.trim();
  if (title.length < 3 || title.length > 200) problems.push('The title needs 3 to 200 characters.');
  if (!i.config) problems.push('Choose a configuration.');
  if (!Number.isInteger(i.ttlHours) || i.ttlHours < 1 || i.ttlHours > MAX_TTL_HOURS) problems.push(`The lifetime must be 1 to ${MAX_TTL_HOURS} hours.`);
  if (!i.file) problems.push('Attach the build as a .zip file.');
  else if (!i.file.name.toLowerCase().endsWith('.zip')) problems.push('The build must be a .zip file.');
  else if (i.file.size <= 0) problems.push('The build file is empty.');
  else if (i.file.size > MAX_BUILD_BYTES) problems.push(`The build is larger than ${formatSize(MAX_BUILD_BYTES)}.`);
  return problems;
}
