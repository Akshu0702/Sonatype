import { useState, type FormEvent } from 'react';
import { useLoad } from './hooks';
import { client, type Detail, type ItsmClient, type Ticket } from './itsm';
import type { Me } from './menu';
import { canRaise, canSeeQueue, formatSize, moveFor, needsCheck, STATE_LABEL, validateRaise } from './ticketRules';

type View = { kind: 'list' } | { kind: 'queue' } | { kind: 'raise' } | { kind: 'detail'; id: string };

export function Tickets({ token, me, base }: { token: string; me: Me; base: string }) {
  const api = client(base, token);
  const [view, setView] = useState<View>({ kind: 'list' });
  const [version, setVersion] = useState(0);
  const reload = () => setVersion((v) => v + 1);
  const open = (id: string) => setView({ kind: 'detail', id });

  return (
    <>
      <h1>Tickets</h1>
      <div className="tabs">
        <button className={view.kind === 'list' ? 'on' : ''} onClick={() => setView({ kind: 'list' })}>My tickets</button>
        {canRaise(me) && <button className={view.kind === 'raise' ? 'on' : ''} onClick={() => setView({ kind: 'raise' })}>Raise a ticket</button>}
        {canSeeQueue(me) && <button className={view.kind === 'queue' ? 'on' : ''} onClick={() => setView({ kind: 'queue' })}>Approvals</button>}
      </div>
      {view.kind === 'list' && <TicketList api={api} version={version} open={open} />}
      {view.kind === 'queue' && <Queue api={api} version={version} open={open} />}
      {view.kind === 'raise' && <Raise api={api} done={(t) => { reload(); open(t.id); }} />}
      {view.kind === 'detail' && <TicketDetail api={api} me={me} id={view.id} version={version} changed={reload} />}
    </>
  );
}

function Table({ rows, open, empty }: { rows: Ticket[]; open: (id: string) => void; empty: string }) {
  if (rows.length === 0) return <p className="muted">{empty}</p>;
  return (
    <table>
      <thead><tr><th>Ticket</th><th>Title</th><th>Status</th><th>Configuration</th><th>Raised by</th><th>Raised</th></tr></thead>
      <tbody>
        {rows.map((t) => (
          <tr key={t.id} className="click" onClick={() => open(t.id)}>
            <td><code>{t.reference}</code></td><td>{t.title}</td><td><span className={`ticket-state ${t.state}`}>{STATE_LABEL[t.state]}</span></td>
            <td><code>{t.config}</code></td><td>{t.requesterName ?? t.requester}</td><td>{new Date(t.createdAt).toLocaleString()}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function TicketList({ api, version, open }: { api: ItsmClient; version: number; open: (id: string) => void }) {
  const s = useLoad(() => api.list('mine'), `mine${version}`);
  if (s.state === 'loading') return <p>Loading…</p>;
  if (s.state === 'error') return <p className="error">{s.message}</p>;
  return <Table rows={s.data} open={open} empty="You have not raised any tickets yet." />;
}

function Queue({ api, version, open }: { api: ItsmClient; version: number; open: (id: string) => void }) {
  const s = useLoad(() => api.queue(), `queue${version}`);
  if (s.state === 'loading') return <p>Loading…</p>;
  if (s.state === 'error') return <p className="error">{s.message === 'Only approvers can see the approval queue' ? 'You are not an approver.' : s.message}</p>;
  return (
    <>
      <p className="muted">Tickets waiting for a decision. You cannot decide your own.</p>
      <Table rows={s.data} open={open} empty="Nothing is waiting for approval." />
    </>
  );
}

function Raise({ api, done }: { api: ItsmClient; done: (t: Ticket) => void }) {
  const configs = useLoad(() => api.configurations(), 'configs');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [config, setConfig] = useState('');
  const [ttl, setTtl] = useState(8);
  const [file, setFile] = useState<File | null>(null);
  const [problems, setProblems] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);

  if (configs.state === 'loading') return <p>Loading…</p>;
  if (configs.state === 'error') return <p className="error">{configs.message}</p>;

  const options = configs.data;
  const chosen = config || options[0] || '';

  async function submit(e: FormEvent) {
    e.preventDefault();
    const found = validateRaise({ title, config: chosen, ttlHours: ttl, file: file ? { name: file.name, size: file.size } : null });
    setProblems(found);
    if (found.length > 0 || !file) return;
    setBusy(true);
    try {
      done(await api.raise({ title: title.trim(), description, config: chosen, ttlHours: ttl, build: file }));
    } catch (err) {
      setProblems([(err as Error).message]);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card form" onSubmit={submit}>
      <h3>Raise a ticket for a lab environment</h3>
      <label>Title<input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} /></label>
      <label>What do you want to test?<textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={4} maxLength={8000} /></label>
      <label>Configuration
        <select value={chosen} onChange={(e) => setConfig(e.target.value)}>{options.map((c) => <option key={c}>{c}</option>)}</select>
      </label>
      <label>Lifetime in hours (1 to 72)<input type="number" min={1} max={72} value={ttl} onChange={(e) => setTtl(Number(e.target.value))} /></label>
      <label>Build (.zip, up to 100 MB)<input type="file" accept=".zip,application/zip" onChange={(e) => setFile(e.target.files?.[0] ?? null)} /></label>
      {problems.length > 0 && <ul className="error">{problems.map((p) => <li key={p}>{p}</li>)}</ul>}
      <div><button type="submit" className="primary" disabled={busy}>{busy ? 'Sending…' : 'Raise the ticket'}</button></div>
    </form>
  );
}

function TicketDetail({ api, me, id, version, changed }: { api: ItsmClient; me: Me; id: string; version: number; changed: () => void }) {
  const s = useLoad(() => api.get(id), `${id}${version}`);
  if (s.state === 'loading') return <p>Loading…</p>;
  if (s.state === 'error') return <p className="error">{s.message === 'Your plan does not include this.' ? 'No such ticket.' : s.message}</p>;
  return <DetailBody api={api} me={me} detail={s.data} changed={changed} />;
}

function DetailBody({ api, me, detail, changed }: { api: ItsmClient; me: Me; detail: Detail; changed: () => void }) {
  const t = detail.ticket;
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const move = moveFor(me, t);

  async function act(action: () => Promise<unknown>) {
    setBusy(true); setError(null);
    try { await action(); setComment(''); changed(); } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }

  return (
    <>
      <section className="card">
        <h3>{t.reference} <span className={`ticket-state ${t.state}`}>{STATE_LABEL[t.state]}</span></h3>
        <p><strong>{t.title}</strong></p>
        {t.description && <p className="pre">{t.description}</p>}
        <table className="facts"><tbody>
          <tr><th>Raised by</th><td>{t.requesterName ?? t.requester}</td></tr>
          <tr><th>Configuration</th><td><code>{t.config}</code></td></tr>
          <tr><th>Lifetime</th><td>{t.ttlHours} hours</td></tr>
          <tr><th>Build</th><td>{t.build.name} ({formatSize(t.build.size)})<br /><code className="muted">sha256 {t.build.sha256}</code></td></tr>
          {t.environmentRef && <tr><th>Environment</th><td><code>{t.environmentRef}</code></td></tr>}
        </tbody></table>
      </section>

      {detail.canDecide && (
        <section className="card">
          <h3>Your decision</h3>
          <label className="form">Comment (needed to reject)<textarea value={comment} onChange={(e) => setComment(e.target.value)} rows={2} maxLength={2000} /></label>
          <div className="row">
            <button className="primary" disabled={busy} onClick={() => act(() => api.approve(t.id, comment))}>Approve</button>
            <button disabled={busy || comment.trim() === ''} title={comment.trim() === '' ? 'Say why first' : ''} onClick={() => act(() => api.reject(t.id, comment))}>Reject</button>
          </div>
        </section>
      )}

      <div className="row">
        {needsCheck(t) && <button disabled={busy} onClick={() => act(() => api.refresh(t.id))}>Check the environment</button>}
        {move && <button disabled={busy} onClick={() => act(() => api.move(t.id, move.to, ''))}>{move.label}</button>}
      </div>
      {error && <p className="error">{error}</p>}

      <h4 className="subhead">History</h4>
      <table>
        <thead><tr><th>When</th><th>Who</th><th>What</th><th>Comment</th></tr></thead>
        <tbody>
          {detail.history.map((e) => (
            <tr key={e.id}><td>{new Date(e.at).toLocaleString()}</td><td>{e.actor}</td>
              <td>{e.action}{e.to ? ` → ${STATE_LABEL[e.to]}` : ''}</td><td className="pre">{e.comment ?? ''}</td></tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
