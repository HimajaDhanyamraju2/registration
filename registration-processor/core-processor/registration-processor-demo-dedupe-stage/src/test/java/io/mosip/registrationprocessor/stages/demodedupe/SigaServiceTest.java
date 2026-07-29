package io.mosip.registrationprocessor.stages.demodedupe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpStatusCodeException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.registration.processor.core.constant.MappingJsonConstants;
import io.mosip.registration.processor.core.constant.ProviderStageName;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.packet.storage.utils.PriorityBasedPacketManagerService;
import io.mosip.registration.processor.packet.storage.utils.Utilities;
import io.mosip.registration.processor.stages.demodedupe.SigaService;
import io.mosip.registration.processor.stages.dto.SigaRecordDto;
import io.mosip.registration.processor.stages.dto.SigaResponseDto;

/**
 * The Class SigaServiceTest.
 */
@RunWith(MockitoJUnitRunner.class)
public class SigaServiceTest {

	private static final String REG_ID = "10011100010001820230101103045";
	private static final String PROCESS = "NEW";

	@Mock
	private Utilities utilities;

	@Mock
	private PriorityBasedPacketManagerService packetManagerService;

	@Mock
	private RegistrationProcessorRestClientService<Object> restClientService;

	@InjectMocks
	private SigaService sigaService;

	@Before
	public void setUp() throws Exception {
		when(utilities.getPacketManagerService()).thenReturn(packetManagerService);
		when(utilities.getRegistrationProcessorMappingJson(MappingJsonConstants.IDENTITY))
				.thenReturn(identityMapping());
		ReflectionTestUtils.setField(sigaService, "sigaVerificationEnabled", true);
		ReflectionTestUtils.setField(sigaService, "sigaMaxRetries", 3);
		ReflectionTestUtils.setField(sigaService, "sigaRetryDelayMs", 1L);
		ReflectionTestUtils.setField(sigaService, "preferredLanguage", "por");
		ReflectionTestUtils.setField(sigaService, "sigaUrl", "https://leginon.dgrn.gov.st/api/nip");
	}

	/**
	 * Mirrors the real RegistrationProcessorIdentity.json shape: each logical key (gender,
	 * dob) maps to {"value": "<literal packet field name>"}. Built via Jackson, like
	 * DemodedupeProcessorTest does, so nested objects come back as LinkedHashMap (what
	 * JsonUtil.getJSONObject expects), not a hand-built org.json.simple.JSONObject.
	 */
	private org.json.simple.JSONObject identityMapping() throws Exception {
		String json = "{\"gender\":{\"value\":\"gender\"},\"dob\":{\"value\":\"dob\"}}";
		return new ObjectMapper().readValue(json, org.json.simple.JSONObject.class);
	}

	@Test
	public void testVerificationDisabledSkipsCall() throws Exception {
		ReflectionTestUtils.setField(sigaService, "sigaVerificationEnabled", false);

		boolean result = sigaService.isVerified(REG_ID, PROCESS);

		assertTrue(result);
		verifyNoInteractions(restClientService);
	}

	@Test
	public void testRecordFound() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenReturn(sigaResponseWithRecord());

		boolean result = sigaService.isVerified(REG_ID, PROCESS);

		assertTrue(result);
	}

	@Test
	public void testRecordNotFoundEmptyData() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		SigaResponseDto emptyResponse = new SigaResponseDto();
		emptyResponse.setData(new ArrayList<>());
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenReturn(emptyResponse);

		boolean result = sigaService.isVerified(REG_ID, PROCESS);

		assertFalse(result);
	}

	@Test
	public void testRecordNotFound404DoesNotRetry() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		HttpStatusCodeException notFoundCause = mock(HttpStatusCodeException.class);
		when(notFoundCause.getStatusCode()).thenReturn(HttpStatus.NOT_FOUND);
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenThrow(new ApisResourceAccessException("not found", notFoundCause));

		boolean result = sigaService.isVerified(REG_ID, PROCESS);

		assertFalse(result);
		verify(restClientService, times(1)).getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class));
	}

	@Test
	public void testTransientFailureRetriesThenSucceeds() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenThrow(new ApisResourceAccessException("timeout")).thenReturn(sigaResponseWithRecord());

		boolean result = sigaService.isVerified(REG_ID, PROCESS);

		assertTrue(result);
		verify(restClientService, times(2)).getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class));
	}

	@Test(expected = ApisResourceAccessException.class)
	public void testTransientFailureExhaustsRetriesAndThrows() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenThrow(new ApisResourceAccessException("down"));

		sigaService.isVerified(REG_ID, PROCESS);
	}

	/**
	 * An unexpected, non-ApisResourceAccessException failure (e.g. a malformed SIGA response
	 * that fails to deserialize) must be wrapped and surfaced immediately, without silently
	 * retrying like a transient network error.
	 */
	@Test
	public void testUnexpectedExceptionIsWrappedAndNotRetried() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenThrow(new ClassCastException("unexpected response shape"));

		try {
			sigaService.isVerified(REG_ID, PROCESS);
			org.junit.Assert.fail("Expected ApisResourceAccessException");
		} catch (ApisResourceAccessException e) {
			assertEquals(ClassCastException.class, e.getCause().getClass());
		}
		verify(restClientService, times(1)).getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGenderNormalizedToPortuguese() throws Exception {
		stubPacketFields("Ledley", "Quintas", "F", "1996-02-22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenReturn(sigaResponseWithRecord());

		sigaService.isVerified(REG_ID, PROCESS);

		ArgumentCaptor<List<Object>> valuesCaptor = ArgumentCaptor.forClass(List.class);
		verify(restClientService).getApi(anyString(), isNull(), any(), valuesCaptor.capture(),
				eq(SigaResponseDto.class));
		assertEquals("Feminino", valuesCaptor.getValue().get(2));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMultiLanguageFieldExtractsPreferredLanguageAndNormalizesDob() throws Exception {
		stubPacketFields("[{\"language\":\"eng\",\"value\":\"Ledley-EN\"},{\"language\":\"por\",\"value\":\"Ledley-PT\"}]",
				"Quintas", "Masculino", "1996/02/22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenReturn(sigaResponseWithRecord());

		sigaService.isVerified(REG_ID, PROCESS);

		ArgumentCaptor<List<Object>> valuesCaptor = ArgumentCaptor.forClass(List.class);
		verify(restClientService).getApi(anyString(), isNull(), any(), valuesCaptor.capture(),
				eq(SigaResponseDto.class));
		assertEquals("Ledley-PT", valuesCaptor.getValue().get(0));
		assertEquals("1996-02-22", valuesCaptor.getValue().get(3));
	}

	@Test
	public void testFieldsAreFetchedInASingleBatchedCall() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenReturn(sigaResponseWithRecord());

		sigaService.isVerified(REG_ID, PROCESS);

		verify(packetManagerService, times(1)).getFields(eq(REG_ID), any(), eq(PROCESS),
				eq(ProviderStageName.DEMO_DEDUPE));
		verify(packetManagerService, never()).getField(any(), any(), any(), any());
	}

	/**
	 * placeOfBirth is optional on the SIGA side and no longer sourced from the packet - it
	 * must still be sent as a query param, just empty.
	 */
	@Test
	@SuppressWarnings("unchecked")
	public void testPlaceOfBirthIsSentEmpty() throws Exception {
		stubPacketFields("Ledley", "Quintas", "Masculino", "1996-02-22");
		when(restClientService.getApi(anyString(), isNull(), any(), any(), eq(SigaResponseDto.class)))
				.thenReturn(sigaResponseWithRecord());

		sigaService.isVerified(REG_ID, PROCESS);

		ArgumentCaptor<List<String>> namesCaptor = ArgumentCaptor.forClass(List.class);
		ArgumentCaptor<List<Object>> valuesCaptor = ArgumentCaptor.forClass(List.class);
		verify(restClientService).getApi(anyString(), isNull(), namesCaptor.capture(), valuesCaptor.capture(),
				eq(SigaResponseDto.class));
		int index = namesCaptor.getValue().indexOf("placeOfBirth");
		assertEquals("", valuesCaptor.getValue().get(index));
	}

	private SigaResponseDto sigaResponseWithRecord() {
		SigaResponseDto response = new SigaResponseDto();
		response.setData(Collections.singletonList(new SigaRecordDto()));
		return response;
	}

	private void stubPacketFields(String firstName, String surname, String gender, String dob) throws Exception {
		Map<String, String> fieldMap = new HashMap<>();
		fieldMap.put("firstName", firstName);
		fieldMap.put("surname", surname);
		fieldMap.put("gender", gender);
		fieldMap.put("dob", dob);
		when(packetManagerService.getFields(eq(REG_ID), any(), eq(PROCESS), eq(ProviderStageName.DEMO_DEDUPE)))
				.thenReturn(fieldMap);
	}
}
