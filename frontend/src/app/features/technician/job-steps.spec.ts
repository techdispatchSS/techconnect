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
  it('starts driving directions to the address', () => {
    expect(directionsUrl('12 Main Rd, Sandton')).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=12%20Main%20Rd%2C%20Sandton' +
        '&travelmode=driving&dir_action=navigate',
    );
  });

  it("uses the site's position when known, as an address can be vague", () => {
    expect(directionsUrl('12 Main Rd, Sandton', -26.1076, 28.0567)).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=-26.1076%2C28.0567' +
        '&travelmode=driving&dir_action=navigate',
    );
  });

  it('falls back to the address with only half a position', () => {
    expect(directionsUrl('12 Main Rd', -26.1, null)).toContain('destination=12%20Main%20Rd');
  });

  it('has nothing to link to without an address', () => {
    expect(directionsUrl(null)).toBeNull();
    expect(directionsUrl(null, null, null)).toBeNull();
    expect(directionsUrl('')).toBeNull();
  });
});
