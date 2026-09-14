-- Self-service profile edits (name/phone/address from the profile menu) now write an audit
-- row too, with SELF_PROFILE_UPDATED distinguishing "a user edited their own record" from
-- USER_UPDATED ("a Manager edited someone else's").
ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_action_check;

ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_action_check CHECK (action IN (
    'USER_CREATED', 'USER_UPDATED', 'USER_ROLE_CHANGED',
    'USER_DEACTIVATED', 'USER_REACTIVATED', 'INVITE_RESENT',
    'SELF_PROFILE_UPDATED'
));
