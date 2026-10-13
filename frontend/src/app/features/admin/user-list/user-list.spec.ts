import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { UserRole } from '../../../core/auth/auth.models';
import { AdminNavCountsService } from '../admin-nav-counts.service';
import { AdminUserService } from '../admin-user.service';
import { AdminUser, UserListFilters } from '../admin.models';
import { UserList } from './user-list';

function person(id: string, name: string, role: UserRole): AdminUser {
  return {
    id,
    name,
    email: `${id}@example.com`,
    phone: null,
    address: {
      street: '1 Test Street',
      suburb: 'Bryanston',
      city: 'Sandton',
      province: 'GAUTENG',
      postalCode: '2191',
    },
    role,
    status: 'ACTIVE',
    technicianStatus: role === 'TECHNICIAN' ? 'AVAILABLE' : null,
    locked: false,
    activatedAt: '2026-10-01T08:00:00Z',
    createdAt: '2026-10-01T08:00:00Z',
  };
}

const PEOPLE = [
  person('c1', 'Gerard Nagura', 'CONTROLLER'),
  person('t1', 'Tyra Nair', 'TECHNICIAN'),
  person('t2', 'Mike Naidoo', 'TECHNICIAN'),
];

describe('UserList', () => {
  let fixture: ComponentFixture<UserList>;
  let el: HTMLElement;
  let list: ReturnType<typeof vi.fn>;

  async function setup(): Promise<void> {
    list = vi.fn((filters: UserListFilters) => {
      const rows = PEOPLE.filter((p) => !filters.role || p.role === filters.role);
      return of({ content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 });
    });
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: AdminUserService, useValue: { list, get: vi.fn() } },
        { provide: AdminNavCountsService, useValue: { refresh: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(UserList);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  const shownPerson = (): string => el.querySelector('.ul-panel-head-name')!.textContent!.trim();

  async function chooseTab(label: string): Promise<void> {
    const tab = [...el.querySelectorAll<HTMLInputElement>('.admin-seg-opt')].find((o) =>
      (o.textContent ?? '').includes(label),
    )!;
    tab.querySelector('input')!.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  it('shows the first person in the details below the table', async () => {
    await setup();

    expect(shownPerson()).toBe('Gerard Nagura');
    // The details sit under the directory in the document, not beside it.
    const directory = el.querySelector('.ul-directory')!;
    const panel = el.querySelector('.ul-panel')!;
    expect(
      directory.compareDocumentPosition(panel) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(directory.contains(panel)).toBe(false);
  });

  it('replaces the person shown when a filter removes them from the list', async () => {
    await setup();
    expect(shownPerson()).toBe('Gerard Nagura');

    await chooseTab('Technicians');

    expect(shownPerson()).toBe('Tyra Nair');
    expect(el.querySelector('tr[data-selected] .ul-name')!.textContent).toBe('Tyra Nair');
  });

  it('clears the details when a filter matches nobody', async () => {
    await setup();
    list.mockImplementation(() =>
      of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    );

    await chooseTab('Managers');

    expect(el.querySelector('.ul-panel-head-name')).toBeNull();
    expect(el.querySelector('.ul-panel')!.textContent).toContain(
      'Select someone from the directory',
    );
  });
});
