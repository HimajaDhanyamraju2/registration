package io.mosip.registration.processor.stages.uingenerator.service;

import java.time.LocalDateTime;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.mosip.kernel.core.util.DateUtils;

/**
 * Hands out the next per-day, per-district, per-control-digit sequence number for National
 * ID generation via a single atomic Postgres upsert (INSERT ... ON CONFLICT DO UPDATE ...
 * RETURNING). The database row is the single source of truth, so this is safe across any
 * number of application restarts and any number of concurrently running instances - there is
 * no in-memory state to lose or to coordinate.
 */
@Repository
public class NationalIdSequenceDao {

    private static final String CREATED_BY = "mosip";

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public int nextSequence(long dayIndex, String districtCode, String controlDigit) {
        LocalDateTime now = DateUtils.getUTCCurrentDateTime();
        Query query = entityManager.createNativeQuery(
                "INSERT INTO regprc.national_id_seq (day_index, district_code, control_digit, curr_seq_no, cr_by, cr_dtimes) "
                        + "VALUES (:dayIndex, :districtCode, :controlDigit, 0, :createdBy, :now) "
                        + "ON CONFLICT (day_index, district_code, control_digit) "
                        + "DO UPDATE SET curr_seq_no = regprc.national_id_seq.curr_seq_no + 1, upd_by = :createdBy, upd_dtimes = :now "
                        + "RETURNING curr_seq_no");
        query.setParameter("dayIndex", dayIndex);
        query.setParameter("districtCode", districtCode);
        query.setParameter("controlDigit", controlDigit);
        query.setParameter("createdBy", CREATED_BY);
        query.setParameter("now", now);

        Object result = query.getSingleResult();
        return ((Number) result).intValue();
    }
}
