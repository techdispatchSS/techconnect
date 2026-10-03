import { MAX_FILE_BYTES, extensionOf, formatBytes, problemWithFile } from './apply.models';

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
