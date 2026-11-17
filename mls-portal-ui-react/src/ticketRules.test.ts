import { describe, expect, it } from 'vitest';
import type { Me } from './menu';
import { canRaise, canSeeQueue, formatSize, moveFor, needsCheck, STATE_LABEL, validateRaise } from './ticketRules';

const me = (...features: string[]): Me => ({ tenantId: 't', subject: 's', products: ['platform', 'itsm'], features });
const zip = { name: 'build.zip', size: 1000 };
const ok = { title: 'Test the plugin', config: 'ubuntu-22.04-postgres-16', ttlHours: 8, file: zip };

describe('what a person may start', () => {
  it('raising needs both the create and the attach features', () => {
    expect(canRaise(me('itsm.tickets.tickets_create_provisioning'))).toBe(false);
    expect(canRaise(me('itsm.tickets.tickets_attach'))).toBe(false);
    expect(canRaise(me('itsm.tickets.tickets_create_provisioning', 'itsm.tickets.tickets_attach'))).toBe(true);
  });

  it('the approvals tab needs the queue feature', () => {
    expect(canSeeQueue(me())).toBe(false);
    expect(canSeeQueue(me('itsm.approvals.approvals_queue'))).toBe(true);
  });
});

describe('moving a ticket on by hand', () => {
  const all = me('itsm.tickets.tickets_transition');
  it('follows the lifecycle: ready, testing, report, closed', () => {
    expect(moveFor(all, { state: 'ACTIVE' })?.to).toBe('TESTING');
    expect(moveFor(all, { state: 'TESTING' })?.to).toBe('REPORTED');
    expect(moveFor(all, { state: 'REPORTED' })?.to).toBe('CLOSED');
    expect(moveFor(all, { state: 'REJECTED' })?.to).toBe('CLOSED');
  });

  it('offers nothing in the states the system moves, or when finished', () => {
    for (const s of ['RAISED', 'PENDING_APPROVAL', 'APPROVED', 'PROVISIONING', 'CLOSED'] as const) expect(moveFor(all, { state: s })).toBeNull();
  });

  it('offers nothing without the transition feature', () => {
    expect(moveFor(me(), { state: 'ACTIVE' })).toBeNull();
  });

  it('asks to check provisioning only for approved and provisioning tickets', () => {
    expect(needsCheck({ state: 'APPROVED' })).toBe(true);
    expect(needsCheck({ state: 'PROVISIONING' })).toBe(true);
    expect(needsCheck({ state: 'ACTIVE' })).toBe(false);
  });

  it('has a plain label for every state', () => {
    expect(Object.keys(STATE_LABEL)).toHaveLength(9);
  });
});

describe('the raise form', () => {
  it('accepts a good request', () => {
    expect(validateRaise(ok)).toEqual([]);
  });

  it('wants a title of 3 to 200 characters', () => {
    expect(validateRaise({ ...ok, title: ' ab ' })).toHaveLength(1);
    expect(validateRaise({ ...ok, title: 'x'.repeat(201) })).toHaveLength(1);
    expect(validateRaise({ ...ok, title: 'x'.repeat(200) })).toEqual([]);
  });

  it('wants a configuration and a lifetime of 1 to 72 hours', () => {
    expect(validateRaise({ ...ok, config: '' })).toHaveLength(1);
    for (const h of [0, 73, 1.5, Number.NaN]) expect(validateRaise({ ...ok, ttlHours: h })).toHaveLength(1);
    for (const h of [1, 72]) expect(validateRaise({ ...ok, ttlHours: h })).toEqual([]);
  });

  it('wants a non-empty .zip of at most 100 MB', () => {
    expect(validateRaise({ ...ok, file: null })).toHaveLength(1);
    expect(validateRaise({ ...ok, file: { name: 'build.tar.gz', size: 10 } })).toHaveLength(1);
    expect(validateRaise({ ...ok, file: { name: 'BUILD.ZIP', size: 10 } })).toEqual([]);
    expect(validateRaise({ ...ok, file: { name: 'a.zip', size: 0 } })).toHaveLength(1);
    expect(validateRaise({ ...ok, file: { name: 'a.zip', size: 100 * 1024 * 1024 } })).toEqual([]);
    expect(validateRaise({ ...ok, file: { name: 'a.zip', size: 100 * 1024 * 1024 + 1 } })).toHaveLength(1);
  });

  it('reports every problem at once', () => {
    expect(validateRaise({ title: '', config: '', ttlHours: 0, file: null })).toHaveLength(4);
  });
});

describe('formatSize', () => {
  it('reads naturally', () => {
    expect(formatSize(512)).toBe('512 B');
    expect(formatSize(2048)).toBe('2.0 KB');
    expect(formatSize(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});
