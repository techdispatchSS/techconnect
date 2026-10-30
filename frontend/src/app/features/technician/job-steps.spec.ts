import { directionsUrl, orderSteps } from './job-steps';

describe('orderSteps', () => {
  it('puts the way forward before going on hold', () => {
    expect(orderSteps(['ON_HOLD', 'COMPLETE'])).toEqual(['COMPLETE', 'ON_HOLD']);
  });

  it('leaves a single step alone', () => {
    expect(orderSteps(['ON_SITE'])).toEqual(['ON_SITE']);
    expect(orderSteps([])).toEqual([]);
  });
});

describe('directionsUrl', () => {
  it('links to the site in maps', () => {
    expect(directionsUrl('12 Main Rd, Sandton')).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=12%20Main%20Rd%2C%20Sandton',
    );
  });

  it('has nothing to link to without an address', () => {
    expect(directionsUrl(null)).toBeNull();
    expect(directionsUrl('')).toBeNull();
  });
});
