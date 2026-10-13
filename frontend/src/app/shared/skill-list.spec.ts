import { joinSkills, MAX_SKILL_LENGTH, parseSkills } from './skill-list';

describe('skill list', () => {
  it('reads one skill per line', () => {
    expect(parseSkills('CCTV\nFibre splicing\nStructured cabling')).toEqual([
      'CCTV',
      'Fibre splicing',
      'Structured cabling',
    ]);
  });

  it('still reads older, comma-separated entries', () => {
    expect(parseSkills('CCTV, structured cabling, fibre splicing')).toEqual([
      'CCTV',
      'structured cabling',
      'fibre splicing',
    ]);
  });

  it('drops blanks and repeats, ignoring case', () => {
    expect(parseSkills('CCTV\n\n  cctv \n, ,Fibre')).toEqual(['CCTV', 'Fibre']);
  });

  it('keeps a long skill within the limit', () => {
    const [skill] = parseSkills('x'.repeat(200));
    expect(skill).toHaveLength(MAX_SKILL_LENGTH);
  });

  it('handles nothing at all', () => {
    expect(parseSkills(null)).toEqual([]);
    expect(parseSkills('')).toEqual([]);
    expect(joinSkills([])).toBeNull();
  });

  it('joins one per line', () => {
    expect(joinSkills(['CCTV', 'Fibre'])).toBe('CCTV\nFibre');
  });
});
