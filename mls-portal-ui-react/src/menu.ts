// What the left menu shows follows what the tenant's package gives it. The API says no with 404 regardless; this only hides the doors.

export interface Me {
  tenantId: string;
  subject: string;
  products: string[];
  features: string[];
}

export interface Item {
  id: 'environments' | 'tickets';
  label: string;
  product: string;
  feature: string;
}

export const ALL_ITEMS: Item[] = [
  { id: 'environments', label: 'AWS environments', product: 'automation', feature: 'automation.deployments_and_environments.environments_view' },
  { id: 'tickets', label: 'Tickets', product: 'itsm', feature: 'itsm.tickets.tickets_view_own' },
];

export function menuFor(me: Me): Item[] {
  return ALL_ITEMS.filter((i) => me.products.includes(i.product) && me.features.includes(i.feature));
}
