import {
  MAX_FILE_BYTES,
  extensionOf,
  formatBytes,
  isValidSouthAfricanId,
  problemWithFile,
} from './apply.models';

function fileOf(name: string, size: number): File {
  // Defining `size` avoids allocating megabytes just to test a limit.
  const file = new File(['x'], name);
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('apply models', () => {
  describe('problemWithFile', () => {
    it.each(['cert.pdf', 'scan.JPG', 'photo.jpeg', 'diploma.png'])('accepts %s', (name) => {
      expect(problemWithFile(fileOf(name, 5000))).toBeNull();
    });

    it.each(['virus.exe', 'page.html', 'macro.docm', 'archive.zip', 'noextension', 'x.pdf.exe'])(
      'rejects %s by extension',
      (name) => {
        expect(problemWithFile(fileOf(name, 5000))).toContain("isn't a PDF, JPG or PNG");
      },
    );

    it('rejects an empty file', () => {
      expect(problemWithFile(fileOf('cert.pdf', 0))).toContain('is empty');
    });

    it('rejects a file over the size limit but accepts one exactly at it', () => {
      expect(problemWithFile(fileOf('big.pdf', MAX_FILE_BYTES + 1))).toContain('the limit is');
      expect(problemWithFile(fileOf('ok.pdf', MAX_FILE_BYTES))).toBeNull();
    });
  });

  describe('isValidSouthAfricanId', () => {
    it('accepts a well-formed number, with or without spaces', () => {
      expect(isValidSouthAfricanId('9001015000085')).toBe(true);
      expect(isValidSouthAfricanId('900101 5000 085')).toBe(true);
      expect(isValidSouthAfricanId('8506151231188')).toBe(true); // permanent resident (digit 11 = 1)
    });

    it.each([
      ['a bad checksum', '9001015000084'],
      ['too short', '900101500008'],
      ['too long', '90010150000850'],
      ['letters', '90010150000A5'],
      ['an impossible month', '9013015000080'],
      ['an impossible day', '9002305000082'],
      ['an invalid citizenship digit', '9001015000283'],
      ['nothing', ''],
    ])('rejects %s', (_label, id) => {
      expect(isValidSouthAfricanId(id)).toBe(false);
    });

    it('treats 29 February as real only in a leap year', () => {
      expect(isValidSouthAfricanId('0002295000083')).toBe(true); // 2000 was a leap year
      expect(isValidSouthAfricanId('0102295000081')).toBe(false); // 2001 / 1901 were not
    });
  });

  it('reads the extension case-insensitively and tolerates none', () => {
    expect(extensionOf('Cert.PDF')).toBe('pdf');
    expect(extensionOf('archive.tar.gz')).toBe('gz');
    expect(extensionOf('README')).toBe('');
  });

  it('formats sizes for people', () => {
    expect(formatBytes(500)).toBe('1 KB');
    expect(formatBytes(2048)).toBe('2 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB');
  });
});
