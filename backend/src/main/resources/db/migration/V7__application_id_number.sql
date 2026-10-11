-- The applicant's South African ID number, shown to the Manager reviewing the application.
--
-- It is also a third identity key for the "one application per person, forever" rule (see V6):
-- a rejected applicant who changes both their email and their phone number is still stopped by
-- the number on their ID. Unique, so two simultaneous submissions can't both win.
--
-- Nullable only so rows created before this column existed stay valid; the API requires it for
-- every new application. Partial index for the same reason.
ALTER TABLE technician_applications ADD COLUMN id_number VARCHAR(13);

CREATE UNIQUE INDEX technician_applications_id_number_unique_idx
    ON technician_applications (id_number) WHERE id_number IS NOT NULL;
