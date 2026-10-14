-- The time zone a person is in, so a time in an email ("respond by 20:24") is said in their own.
-- It is taken from their browser when they sign in, so it follows them if they move.
--
-- Everyone who exists today works in South Africa, so they start there. A person added from now
-- on has none until they first sign in, and until then times are given in UTC.

ALTER TABLE users ADD COLUMN time_zone VARCHAR(64);

UPDATE users SET time_zone = 'Africa/Johannesburg' WHERE time_zone IS NULL;
