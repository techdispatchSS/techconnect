-- Every user carries a postal address. For technicians this is the origin point for
-- distance-based job matching (PRD Phase 2 — "AI-assisted technician recommendation
-- (skills, distance, availability scoring)").
--
-- Kept as a single free-text line rather than split into street/city/postal fields: that is
-- what geocoding services accept, and South African address formats vary too much for a
-- rigid structure to help. Nullable because rows created before this migration have none —
-- the API requires it on new users.
ALTER TABLE users ADD COLUMN address TEXT;
