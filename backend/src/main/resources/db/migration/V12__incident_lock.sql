-- A controller who opens an incident to dispatch it takes a short lock on it, so two controllers
-- do not both fill in a dispatch for the same fault and send it to different technicians.
--
-- The lock is advisory and self-healing: it lapses on its own (lock_expires_at) if the controller
-- closes the tab or loses their connection. Creating a dispatch also refuses while someone else
-- holds it, and still refuses a second live dispatch regardless (see DispatchService.create).
ALTER TABLE incidents ADD COLUMN locked_by UUID REFERENCES users (id);
ALTER TABLE incidents ADD COLUMN lock_expires_at TIMESTAMPTZ;
