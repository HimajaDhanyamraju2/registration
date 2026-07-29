package io.mosip.registration.processor.stages.demodedupe;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.PacketManagerException;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.stages.dto.SigaResponseDto;

/**
 * Verifies an applicant's core demographic data against the external SIGA (civil
 * registry) API before the local demographic dedupe check runs.
 */
@Service
public class SigaService {

	private static Logger regProcLogger = RegProcessorLogger.getLogger(SigaService.class);

	private static final String MASCULINO = "Masculino";
	private static final String FEMININO = "Feminino";
	private static final String FIRST_NAME_FIELD = "firstName";
	private static final String SURNAME_FIELD = "surname";

	@Value("${mosip.regproc.demo.dedupe.siga.enable:true}")
	private boolean sigaVerificationEnabled;

	@Value("${mosip.regproc.demo.dedupe.siga.url:https://leginon.dgrn.gov.st/api/nip}")
	private String sigaUrl;

	@Value("${mosip.regproc.demo.dedupe.siga.max-retries:3}")
	private int sigaMaxRetries;

	@Value("${mosip.regproc.demo.dedupe.siga.retry-delay-ms:2000}")
	private long sigaRetryDelayMs;

	@Value("${mosip.regproc.national-id.district-field:district}")
	private String districtFieldName;

	@Value("${mosip.regproc.national-id.preferred-language:por}")
	private String preferredLanguage;

	@Autowired
	private Utilities utilities;

	@Autowired
	private RegistrationProcessorRestClientService<Object> restClientService;

	/**
	 * Looks up the applicant in SIGA by first/last name, gender, date of birth and place of
	 * birth (district). Returns true when SIGA reports a matching record (or when the SIGA
	 * check is disabled), false when SIGA has no matching record (HTTP 404 or empty
	 * {@code data}) - callers are expected to pause the packet for manual verification in
	 * that case rather than proceed with the local dedupe check.
	 *
	 * @throws ApisResourceAccessException if the SIGA API keeps failing after retries are
	 *             exhausted, so the caller can route the packet for automatic reprocessing
	 */
	public boolean isVerified(String registrationId, String process)
			throws ApisResourceAccessException, PacketManagerException, JsonProcessingException, IOException {
		if (!sigaVerificationEnabled) {
			return true;
		}

		org.json.simple.JSONObject regProcessorIdentityJson = utilities
				.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY);
		String genderFieldName = JsonUtil.getJSONValue(
				JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.GENDER),
				MappingJsonConstants.VALUE);
		String dobFieldName = JsonUtil.getJSONValue(
				JsonUtil.getJSONObject(regProcessorIdentityJson, MappingJsonConstants.DOB),
				MappingJsonConstants.VALUE);

		List<String> fields = Arrays.asList(FIRST_NAME_FIELD, SURNAME_FIELD, genderFieldName, dobFieldName,
				districtFieldName);
		Map<String, String> fieldMap = utilities.getPacketManagerService().getFields(registrationId, fields, process,
				ProviderStageName.DEMO_DEDUPE);

		String firstName = extractLanguageValue(fieldMap.get(FIRST_NAME_FIELD));
		String lastName = extractLanguageValue(fieldMap.get(SURNAME_FIELD));
		String gender = toSigaGender(extractLanguageValue(fieldMap.get(genderFieldName)));
		String birthDate = toSigaDate(extractLanguageValue(fieldMap.get(dobFieldName)));
		String placeOfBirth = extractLanguageValue(fieldMap.get(districtFieldName));

		return callSigaWithRetry(registrationId, firstName, lastName, gender, birthDate, placeOfBirth);
	}

	private boolean callSigaWithRetry(String registrationId, String firstName, String lastName, String gender,
			String birthDate, String placeOfBirth) throws ApisResourceAccessException {
		List<String> queryParamNames = Arrays.asList("firstName", "lastName", "gender", "birthDate", "placeOfBirth");
		List<Object> queryParamValues = Arrays.asList(firstName, lastName, gender, birthDate, placeOfBirth);

		ApisResourceAccessException lastException = null;
		for (int attempt = 1; attempt <= sigaMaxRetries; attempt++) {
			// TODO: remove before production
			regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
					LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
					"SigaService::isVerified():: calling SIGA API (attempt " + attempt + ") url=" + sigaUrl
							+ " | firstName=" + firstName + " lastName=" + lastName + " gender=" + gender
							+ " birthDate=" + birthDate + " placeOfBirth=" + placeOfBirth);
			try {
				SigaResponseDto response = (SigaResponseDto) restClientService.getApi(sigaUrl, null,
						queryParamNames, queryParamValues, SigaResponseDto.class);
				boolean found = response != null && response.getData() != null && !response.getData().isEmpty();
				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						"SigaService::isVerified():: SIGA API responded on attempt " + attempt + " - record "
								+ (found ? "found" : "not found"));
				return found;
			} catch (ApisResourceAccessException e) {
				if (isNotFound(e)) {
					regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
							"SigaService::isVerified():: SIGA API returned 404 - no matching record found");
					return false;
				}
				lastException = e;
				regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						"SigaService::isVerified():: attempt " + attempt + " of " + sigaMaxRetries + " failed: "
								+ e.getMessage() + ExceptionUtils.getStackTrace(e));
				if (attempt < sigaMaxRetries) {
					try {
						Thread.sleep(sigaRetryDelayMs);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new ApisResourceAccessException("SIGA API call interrupted during retry", ie);
					}
				}
			} catch (Exception e) {
				regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						"SigaService::isVerified():: unexpected error calling SIGA API on attempt " + attempt + ": "
								+ e.getMessage() + ExceptionUtils.getStackTrace(e));
				throw new ApisResourceAccessException("SIGA API call failed unexpectedly", e);
			}
		}
		throw lastException != null ? lastException
				: new ApisResourceAccessException("SIGA API call failed: no attempts were made ("
						+ "mosip.regproc.demo.dedupe.siga.max-retries=" + sigaMaxRetries + ")");
	}

	private boolean isNotFound(ApisResourceAccessException e) {
		Throwable cause = e.getCause();
		return cause instanceof HttpStatusCodeException
				&& ((HttpStatusCodeException) cause).getStatusCode() == HttpStatus.NOT_FOUND;
	}

	private String toSigaGender(String rawGender) {
		if (rawGender == null || rawGender.isEmpty())
			return rawGender;
		String upper = rawGender.trim().toUpperCase();
		if (upper.startsWith("M"))
			return MASCULINO;
		if (upper.startsWith("F"))
			return FEMININO;
		return rawGender;
	}

	private String toSigaDate(String rawDob) {
		if (rawDob == null || rawDob.isEmpty())
			return rawDob;
		try {
			LocalDate date = rawDob.contains("/")
					? LocalDate.parse(rawDob, DateTimeFormatter.ofPattern("yyyy/MM/dd"))
					: LocalDate.parse(rawDob.length() > 10 ? rawDob.substring(0, 10) : rawDob);
			return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
		} catch (Exception e) {
			regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(), "SigaService", "toSigaDate",
					"Failed to normalize DOB '" + rawDob + "' for SIGA request, using raw value");
			return rawDob;
		}
	}

	/**
	 * Packet fields may be stored as a multi-language JSON array, e.g.
	 * {@code [{"language":"por","value":"Masculino"}]}. Extracts the preferred-language
	 * value, falling back to the first available one.
	 */
	private String extractLanguageValue(String rawValue) {
		if (rawValue == null || rawValue.trim().isEmpty())
			return rawValue;
		String trimmed = rawValue.trim();
		if (!trimmed.startsWith("[")) {
			return rawValue;
		}
		try {
			JSONArray jsonArray = new JSONArray(trimmed);
			String fallbackValue = null;
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject obj = jsonArray.getJSONObject(i);
				if (obj.has("language") && obj.has("value")) {
					String lang = obj.getString("language");
					String value = obj.getString("value");
					if (fallbackValue == null) {
						fallbackValue = value;
					}
					if (lang.equalsIgnoreCase(preferredLanguage)) {
						return value;
					}
				}
			}
			return fallbackValue != null ? fallbackValue : rawValue;
		} catch (Exception e) {
			return rawValue;
		}
	}
}
