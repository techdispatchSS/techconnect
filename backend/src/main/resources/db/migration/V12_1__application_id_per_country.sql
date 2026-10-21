-- An identification number belongs to a person in the country that issued it, so it is unique
-- within a country, not across the world: 12345678 in Kenya and 12345678 in Ghana are different
-- people, and the second must not be quietly discarded as a repeat of the first.
--
-- Everything on record today is from South Africa (address_country defaults to 'ZA'), so no
-- existing row can collide under the narrower rule.
DROP INDEX technician_applications_id_number_unique_idx;

CREATE UNIQUE INDEX technician_applications_country_id_number_unique_idx
    ON technician_applications (address_country, id_number) WHERE id_number IS NOT NULL;
