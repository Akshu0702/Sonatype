import { useEffect, useState } from 'react';
import * as api from './api';
import { finishLogin, logout, startLogin, storedToken, type Config } from './auth';
import { useLoad } from './hooks';
import { Tickets } from './Tickets';
import { menuFor, type Me } from './menu';

export function App() {
  const [config, setConfig] = useState<Config | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const c = (await (await fetch('/config.json')).json()) as Config;
        setConfig(c);
        setToken((await finishLogin(c)) ?? storedToken());
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setReady(true);
      }
    })();
  }, []);

  if (!ready) return <p className="center">Loading…</p>;
  if (!config) return <p className="center error">Could not load the console settings. {error}</p>;
  if (!token) {
    return (
      <div className="center">
        <h1>Managed Lab console</h1>
        {error && <p className="error">{error}</p>}
        <button onClick={() => startLogin(config)}>Sign in</button>
      </div>
    );
  }
  return <Console token={token} config={config} />;
}

function Console({ token, config }: { token: string; config: Config }) {
  const who = useLoad(() => api.me(token), 'me');
  const [page, setPage] = useState<string | null>(null);

  if (who.state === 'loading') return <p className="center">Loading…</p>;
  if (who.state === 'error') {
    return <div className="center"><p className="error">{who.message}</p><button onClick={() => logout(config)}>Sign out</button></div>;
  }
  const me: Me = who.data;
  const items = menuFor(me);
  const current = page ?? items[0]?.id ?? null;
  return (
    <div className="shell">
      <nav>
        <h2>Managed Lab</h2>
        {items.map((i) => <button key={i.id} className={i.id === current ? 'on' : ''} onClick={() => setPage(i.id)}>{i.label}</button>)}
        {items.length === 0 && <p className="muted">Nothing is switched on for your tenant yet.</p>}
        <div className="foot"><span className="muted">{me.subject.slice(0, 8)}</span><button onClick={() => logout(config)}>Sign out</button></div>
      </nav>
      <main>
        {current === 'environments' && <Environments token={token} />}
        {current === 'tickets' && <Tickets token={token} me={me} base={config.itsmApiUrl ?? '/itsm/api/v1'} />}
      </main>
    </div>
  );
}

function Environments({ token }: { token: string }) {
  const s = useLoad(() => api.environments(token), 'env');
  if (s.state === 'loading') return <p>Reading AWS…</p>;
  if (s.state === 'error') return <p className="error">{s.message}</p>;
  const o = s.data;
  return (
    <>
      <h1>AWS environments</h1>
      <p className="muted">Live from AWS, region {o.region}, read at {new Date(o.observedAt).toLocaleString()}. Read-only.</p>
      {o.environments.length === 0 && <p>No environments are tagged for this lab yet.</p>}
      {o.environments.map((e) => (
        <section key={e.name} className="card">
          <h3>{e.name} <span className="pill">{e.running} of {e.instances.length} running</span></h3>
          <h4>Networks</h4>
          <table><thead><tr><th>Name</th><th>VPC</th><th>CIDR</th><th>Role</th></tr></thead>
            <tbody>{e.vpcs.map((v) => <tr key={v.id}><td>{v.name}</td><td><code>{v.id}</code></td><td><code>{v.cidr}</code></td><td>{v.function ?? ''}</td></tr>)}</tbody></table>
          <h4>Servers</h4>
          <table><thead><tr><th>Name</th><th>State</th><th>Type</th><th>Private IP</th><th>Zone</th><th>Role</th></tr></thead>
            <tbody>{e.instances.map((i) => <tr key={i.id}><td>{i.name}</td><td><span className={`state ${i.state}`}>{i.state}</span></td><td>{i.type}</td><td><code>{i.privateIp ?? ''}</code></td><td>{i.zone}</td><td>{i.function ?? ''}</td></tr>)}</tbody></table>
        </section>
      ))}
    </>
  );
}
