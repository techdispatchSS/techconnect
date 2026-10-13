-- A technician application must now carry an ID document, a matric certificate and a proof of
-- residence, alongside any named certifications. Three new document kinds, and the column that
-- holds them is widened: PROOF_OF_RESIDENCE and MATRIC_CERTIFICATE are 18 characters, the
-- column was VARCHAR(16).
--
-- Existing rows only ever carry CERTIFICATE or QUALIFICATION and are left exactly as they are.

ALTER TABLE application_documents
    DROP CONSTRAINT application_documents_kind_check;

ALTER TABLE application_documents
    ALTER COLUMN kind TYPE VARCHAR(32);

ALTER TABLE application_documents
    ADD CONSTRAINT application_documents_kind_check CHECK (kind IN (
        'ID_DOCUMENT', 'MATRIC_CERTIFICATE', 'PROOF_OF_RESIDENCE',
        'CERTIFICATE', 'QUALIFICATION'
    ));
