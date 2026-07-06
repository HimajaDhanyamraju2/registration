package io.mosip.registration.processor.stages.uingenerator.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.stages.uingenerator.exception.NationalIdGenerationException;

/**
 * Service for generating unique National IDs.
 *
 * Format: 50XXXXXYYZDD (12 characters)
 * - 50: Fixed prefix
 * - XXXXX: 5 digits (structured, not random)
 * - YY: 2 alphanumeric characters (0-9, A-Z) (structured, not random)
 * - Z: Control digit (7=citizen, 5=foreigner)
 * - DD: 2-digit district code
 *
 * Uniqueness is guaranteed by construction, not by checking history:
 * - the per-day, per-district, per-control-digit sequence number comes from a single atomic
 *   Postgres upsert (see {@link NationalIdSequenceDao}), so it can never repeat, regardless of
 *   application restarts or how many instances are running concurrently - the database row is
 *   the only source of truth, there is no in-memory counter to lose or coordinate.
 * - the (day-since-epoch, sequence) pair is then run through a keyed, deterministic permutation
 *   (a Feistel cipher, format-preserving via cycle-walking) so the emitted digits are
 *   indistinguishable from random and do not reveal issuance order/volume. Since a permutation
 *   is bijective, distinct inputs can never produce the same output.
 *
 * All instances MUST be configured with the exact same fpe-key: two different keys would each
 * apply a different (individually valid) permutation, and nothing would then prevent two
 * different sequence numbers from mapping to the same emitted digits across instances.
 */
@Service
public class NationalIdGenerator {

    private static Logger regProcLogger = RegProcessorLogger.getLogger(NationalIdGenerator.class);

    private static final String FIXED_PREFIX = "50";
    private static final String CITIZEN_CONTROL_DIGIT = "7";
    private static final String FOREIGNER_CONTROL_DIGIT = "5";
    private static final String ALPHANUMERIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static final long DIGIT_SPACE = 100_000L;        // 5 digits
    private static final long ALNUM_SPACE = 1_296L;          // 2 chars, base 36
    private static final long TOTAL_SPACE = DIGIT_SPACE * ALNUM_SPACE; // 129,600,000

    private static final long SEQUENCE_CAPACITY = 10_000L;   // per day, per district, per control-digit
    private static final long DAY_CAPACITY = TOTAL_SPACE / SEQUENCE_CAPACITY; // 12,960 days (~35.5 years)
    private static final double DAY_CAPACITY_WARNING_THRESHOLD = 0.8;

    private static final int FPE_HALF_BITS = 14;             // 2^28 domain, cycle-walked down to TOTAL_SPACE
    private static final long FPE_HALF_MASK = (1L << FPE_HALF_BITS) - 1;
    private static final int FPE_ROUNDS = 4;

    @Autowired
    private DistrictCodeMapper districtCodeMapper;

    @Autowired
    private NationalIdSequenceDao nationalIdSequenceDao;

    @Value("${mosip.regproc.national-id.epoch-date:2024-01-01}")
    private String epochDateConfig;

    @Value("${mosip.regproc.national-id.fpe-key:CHANGE-ME-IN-PRODUCTION-SECRET-KEY}")
    private String fpeKey;

    private LocalDate epochDate;
    private SecretKeySpec fpeSecretKey;

    @PostConstruct
    public void init() {
        epochDate = LocalDate.parse(epochDateConfig);
        fpeSecretKey = new SecretKeySpec(fpeKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        if ("CHANGE-ME-IN-PRODUCTION-SECRET-KEY".equals(fpeKey)) {
            regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(), "NationalIdGenerator", "init",
                    "mosip.regproc.national-id.fpe-key is using the default placeholder value. "
                            + "This MUST be overridden with a secret key in production configuration, "
                            + "and MUST be identical across every running instance.");
        }
    }

    /**
     * Generate a unique National ID.
     *
     * @param registrationId the registration ID (for logging)
     * @param isCitizen true for citizens (control digit 7), false for foreigners (control digit 5)
     * @param districtName the district name for geographic code lookup
     * @return the generated national ID
     */
    public String generateNationalId(String registrationId, boolean isCitizen, String districtName)
            throws NationalIdGenerationException {

        regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
                "Generating national ID. Citizen: " + isCitizen + ", District: " + districtName);

        String controlDigit = isCitizen ? CITIZEN_CONTROL_DIGIT : FOREIGNER_CONTROL_DIGIT;
        String districtCode = districtCodeMapper.getDistrictCode(districtName);
        String citizenType = isCitizen ? "CITIZEN" : "FOREIGNER";

        long dayIndex = ChronoUnit.DAYS.between(epochDate, LocalDate.now());
        if (dayIndex < 0 || dayIndex >= DAY_CAPACITY) {
            throw new NationalIdGenerationException("National ID day index " + dayIndex
                    + " is out of the supported range [0, " + DAY_CAPACITY + "). "
                    + "The configured epoch-date needs to be rolled forward.");
        }
        if (dayIndex >= DAY_CAPACITY * DAY_CAPACITY_WARNING_THRESHOLD) {
            regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(), "NationalIdGenerator", "generateNationalId",
                    "National ID day index " + dayIndex + " has passed " + (DAY_CAPACITY_WARNING_THRESHOLD * 100)
                            + "% of its capacity (" + DAY_CAPACITY + "). Plan to roll the epoch-date forward.");
        }

        // The database row is the single source of truth for this counter - safe across
        // restarts and across any number of concurrently running instances. If this fails,
        // no id is fabricated: the exception propagates and no national id is returned.
        int sequence;
        try {
            sequence = nationalIdSequenceDao.nextSequence(dayIndex, districtCode, controlDigit);
        } catch (RuntimeException e) {
            throw new NationalIdGenerationException("Failed to allocate a national id sequence number for district code "
                    + districtCode + ", control digit " + controlDigit + " on day index " + dayIndex, e);
        }
        if (sequence >= SEQUENCE_CAPACITY) {
            throw new NationalIdGenerationException("Daily national ID capacity (" + SEQUENCE_CAPACITY
                    + ") exceeded for district code " + districtCode + ", control digit " + controlDigit
                    + " on day index " + dayIndex);
        }

        long structuredIndex = dayIndex * SEQUENCE_CAPACITY + sequence;

        // Tweak the permutation by district+control-digit so buckets with the same day/sequence
        // (e.g. the first registration of the day in every district) don't look alike - only the
        // sequence's own bucket shares a permutation, keeping the bijection (and uniqueness) intact.
        String tweak = districtCode + ":" + controlDigit;
        long permutedIndex = permute(structuredIndex, tweak);

        long digitPart = permutedIndex / ALNUM_SPACE;
        long alnumIndex = permutedIndex % ALNUM_SPACE;

        String randomDigits = String.format("%05d", digitPart);
        String randomAlphanumeric = toAlphanumeric(alnumIndex);

        String nationalId = FIXED_PREFIX + randomDigits + randomAlphanumeric + controlDigit + districtCode;

        regProcLogger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
                "Successfully generated national ID (Type: " + citizenType + ", District Code: " + districtCode
                        + ", Day Index: " + dayIndex + ", Sequence: " + sequence + ")");

        return nationalId;
    }

    private String toAlphanumeric(long alnumIndex) {
        int first = (int) (alnumIndex / 36);
        int second = (int) (alnumIndex % 36);
        return String.valueOf(ALPHANUMERIC_CHARS.charAt(first)) + ALPHANUMERIC_CHARS.charAt(second);
    }

    /**
     * Format-preserving permutation of [0, TOTAL_SPACE) via a balanced Feistel cipher over a
     * 2^28 domain with cycle-walking. A Feistel network is bijective for any round function,
     * so re-applying it to out-of-range outputs (cycle-walking) still yields a bijection when
     * restricted to [0, TOTAL_SPACE).
     */
    private long permute(long value, String tweak) {
        long current = value;
        do {
            current = feistelRound(current, tweak);
        } while (current >= TOTAL_SPACE);
        return current;
    }

    private long feistelRound(long input, String tweak) {
        long left = (input >>> FPE_HALF_BITS) & FPE_HALF_MASK;
        long right = input & FPE_HALF_MASK;
        for (int round = 0; round < FPE_ROUNDS; round++) {
            long f = roundFunction(round, right, tweak) & FPE_HALF_MASK;
            long newRight = left ^ f;
            left = right;
            right = newRight;
        }
        return (left << FPE_HALF_BITS) | right;
    }

    private long roundFunction(int round, long right, String tweak) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(fpeSecretKey);
            byte[] data = (tweak + ":" + round + ":" + right).getBytes(StandardCharsets.UTF_8);
            byte[] hash = mac.doFinal(data);
            long result = 0;
            for (int i = 0; i < 4; i++) {
                result = (result << 8) | (hash[i] & 0xFF);
            }
            return result & FPE_HALF_MASK;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute national ID round function", e);
        }
    }
}
