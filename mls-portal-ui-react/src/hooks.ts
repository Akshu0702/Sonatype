import { useEffect, useState } from 'react';
import * as api from './api';
import { NotAvailable } from './itsm';

export type Load<T> = { state: 'loading' } | { state: 'error'; message: string } | { state: 'done'; data: T };

/** Runs a read when the key changes (a new key re-reads). A feature that is switched off says so in plain words. */
export function useLoad<T>(fetcher: () => Promise<T>, key: string): Load<T> {
  const [s, set] = useState<Load<T>>({ state: 'loading' });
  useEffect(() => {
    let live = true;
    set({ state: 'loading' });
    fetcher()
      .then((data) => live && set({ state: 'done', data }))
      .catch((e: Error) => live && set({ state: 'error', message: e instanceof api.NotAvailable || e instanceof NotAvailable ? 'Your plan does not include this.' : e.message }));
    return () => { live = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
  return s;
}
