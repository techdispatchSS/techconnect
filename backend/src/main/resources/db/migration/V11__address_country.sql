-- Addresses are no longer South Africa only. Each address records its country (ISO 3166-1
-- alpha-2), and the province column becomes the free-text region it is everywhere but in South
-- Africa, where it still holds one of the nine province names. Suburb and postal code are
-- optional outside South Africa (many countries have no postcode system).
--
-- Everything on record today is South African.

ALTER TABLE users ADD COLUMN address_country VARCHAR(2);
UPDATE users SET address_country = 'ZA' WHERE address_street IS NOT NULL;

ALTER TABLE technician_applications
    ADD COLUMN address_country VARCHAR(2) NOT NULL DEFAULT 'ZA';
ALTER TABLE technician_applications ALTER COLUMN address_suburb DROP NOT NULL;
ALTER TABLE technician_applications ALTER COLUMN address_province TYPE VARCHAR(120);
ALTER TABLE technician_applications ALTER COLUMN address_postal_code TYPE VARCHAR(12);
ALTER TABLE technician_applications ALTER COLUMN address_postal_code DROP NOT NULL;

-- A national ID or passport number from outside South Africa is not 13 digits.
ALTER TABLE technician_applications ALTER COLUMN id_number TYPE VARCHAR(32);
