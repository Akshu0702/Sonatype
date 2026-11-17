import { describe, expect, it } from 'vitest';
import { menuFor, type Me } from './menu';

const base: Me = { tenantId: 't', subject: 's', products: ['platform'], features: [] };

describe('menuFor', () => {
  it('shows nothing for a tenant with neither Product', () => {
    expect(menuFor(base)).toEqual([]);
  });

  it('shows AWS environments only with the automation Product and its feature', () => {
    const me = { ...base, products: ['platform', 'automation'], features: ['automation.deployments_and_environments.environments_view'] };
    expect(menuFor(me).map((i) => i.id)).toEqual(['environments']);
  });

  it('needs the feature as well as the Product', () => {
    expect(menuFor({ ...base, products: ['platform', 'itsm'], features: [] })).toEqual([]);
  });

  it('shows both for the full package', () => {
    const me = { ...base, products: ['platform', 'itsm', 'automation'], features: ['itsm.tickets.tickets_view_own', 'automation.deployments_and_environments.environments_view'] };
    expect(menuFor(me).map((i) => i.id)).toEqual(['environments', 'tickets']);
  });
});
