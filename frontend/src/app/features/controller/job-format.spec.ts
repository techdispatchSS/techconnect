import { elapsedLabel, progressOf, slaCountdown, statusTone } from './job-format';

const NOW = Date.parse('2026-10-14T10:00:00Z');
const ago = (minutes: number): string => new Date(NOW - minutes * 60_000).toISOString();
const ahead = (minutes: number): string => new Date(NOW + minutes * 60_000).toISOString();

describe('elapsedLabel', () => {
  it('reads minutes, hours, and days the way the queue does', () => {
    expect(elapsedLabel(ago(0), NOW)).toBe('0m');
    expect(elapsedLabel(ago(45), NOW)).toBe('45m');
    expect(elapsedLabel(ago(60), NOW)).toBe('1h');
    expect(elapsedLabel(ago(130), NOW)).toBe('2h 10m');
    expect(elapsedLabel(ago(60 * 24 * 2 + 60 * 4), NOW)).toBe('2d 4h');
  });

  it('says nothing for a time that has not happened', () => {
    expect(elapsedLabel(null, NOW)).toBe('—');
  });
});

describe('slaCountdown', () => {
  it('counts down to a deadline still ahead', () => {
    expect(slaCountdown(ahead(235), false, NOW)).toEqual({ text: '3h 55m left', overdue: false });
  });

  it('says how late once it has passed', () => {
    expect(slaCountdown(ago(20), false, NOW)).toEqual({ text: 'Overdue by 20m', overdue: true });
  });

  it('is not shown for a closed job, or one with no SLA', () => {
    expect(slaCountdown(ago(20), true, NOW)).toBeNull();
    expect(slaCountdown(null, false, NOW)).toBeNull();
  });
});

describe('job status', () => {
  it('colours a held job differently from one that is moving', () => {
    expect(statusTone('EN_ROUTE')).toBe('moving');
    expect(statusTone('ON_HOLD')).toBe('held');
    expect(statusTone('SUBMITTED')).toBe('review');
    expect(statusTone('CLOSED')).toBe('done');
  });

  it('only ever moves the bar forward as the job moves on', () => {
    const order = [
      'EN_ROUTE',
      'ON_SITE',
      'WORK_STARTED',
      'WORK_RESUMED',
      'COMPLETE',
      'SUBMITTED',
      'CLOSED',
    ] as const;
    const values = order.map(progressOf);
    expect([...values].sort((a, b) => a - b)).toEqual(values);
    expect(progressOf('CLOSED')).toBe(1);
  });
});
