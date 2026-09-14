-- Replaces the single free-text `address` column with structured fields: street, suburb,
-- city, province and postal code. The free-text line was a deliberate original choice (see
-- V2's comment) but in practice let required-address onboarding still leave a technician's
-- suburb or province blank inside an otherwise-filled-in string, which is exactly the gap
-- distance-based job matching (PRD Phase 2) cannot tolerate. Structured, individually
-- validated fields close that gap; South Africa's province set is small and fixed (nine),
-- so it is no longer the "too much variation for a rigid structure" case V2 was written
-- against — only street/suburb/city keep the free-text flexibility informal addressing needs.
--
-- No data migration: the table has no rows with the old column populated at the time this
-- migration was written.
ALTER TABLE users DROP COLUMN address;

ALTER TABLE users ADD COLUMN address_street TEXT;
ALTER TABLE users ADD COLUMN address_suburb TEXT;
ALTER TABLE users ADD COLUMN address_city TEXT;
ALTER TABLE users ADD COLUMN address_province TEXT;
ALTER TABLE users ADD COLUMN address_postal_code TEXT;
