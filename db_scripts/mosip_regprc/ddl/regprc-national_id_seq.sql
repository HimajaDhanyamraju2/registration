

-- object: regprc.national_id_seq | type: TABLE --
-- DROP TABLE IF EXISTS regprc.national_id_seq CASCADE;
CREATE TABLE regprc.national_id_seq(
	day_index bigint NOT NULL,
	district_code character varying(2) NOT NULL,
	control_digit character varying(1) NOT NULL,
	curr_seq_no integer NOT NULL,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	CONSTRAINT pk_natidseq_id PRIMARY KEY (day_index,district_code,control_digit)

);
-- ddl-end --
COMMENT ON TABLE regprc.national_id_seq IS 'National ID Sequence: Keeps the current per-day, per-district, per-citizen-type sequence counter used while generating National IDs, so the counter survives application restarts and is coordinated across all running instances.';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.day_index IS 'Day Index: Number of days since the configured National ID epoch date.';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.district_code IS 'District Code: 2-digit geographic district code used in the National ID.';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.control_digit IS 'Control Digit: National ID control digit (7=citizen, 5=foreigner).';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.curr_seq_no IS 'Current Sequence Number: Latest sequence number issued for this day/district/control-digit bucket, used to keep generated National IDs unique.';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.cr_by IS 'Created By : ID or name of the user who create / insert record.';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.upd_by IS 'Updated By : ID or name of the user who update the record with new values';
-- ddl-end --
COMMENT ON COLUMN regprc.national_id_seq.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- ddl-end --
