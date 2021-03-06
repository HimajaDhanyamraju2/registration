package io.mosip.registration.processor.stages.packet.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.mosip.kernel.packetmanager.exception.ApiNotAccessibleException;
import io.mosip.kernel.packetmanager.spi.PacketReaderService;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.abstractverticle.MessageBusAddress;
import io.mosip.registration.processor.core.abstractverticle.MessageDTO;
import io.mosip.registration.processor.core.code.ApiName;
import io.mosip.registration.processor.core.code.EventId;
import io.mosip.registration.processor.core.code.EventName;
import io.mosip.registration.processor.core.code.EventType;
import io.mosip.registration.processor.core.code.ModuleName;
import io.mosip.registration.processor.core.code.RegistrationExceptionTypeCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionStatusCode;
import io.mosip.registration.processor.core.code.RegistrationTransactionTypeCode;
import io.mosip.registration.processor.core.constant.JsonConstant;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.constant.PacketFiles;
import io.mosip.registration.processor.core.exception.ApisResourceAccessException;
import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;
import io.mosip.registration.processor.core.exception.util.PlatformSuccessMessages;
import io.mosip.registration.processor.core.logger.LogDescription;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;
import io.mosip.registration.processor.core.packet.dto.PacketMetaInfo;
import io.mosip.registration.processor.core.packet.dto.packetvalidator.MainRequestDTO;
import io.mosip.registration.processor.core.packet.dto.packetvalidator.MainResponseDTO;
import io.mosip.registration.processor.core.packet.dto.packetvalidator.ReverseDataSyncRequestDTO;
import io.mosip.registration.processor.core.packet.dto.packetvalidator.ReverseDatasyncReponseDTO;
import io.mosip.registration.processor.core.spi.restclient.RegistrationProcessorRestClientService;
import io.mosip.registration.processor.core.status.util.StatusUtil;
import io.mosip.registration.processor.core.status.util.TrimExceptionMessage;
import io.mosip.registration.processor.core.util.IdentityIteratorUtil;
import io.mosip.registration.processor.core.util.JsonUtil;
import io.mosip.registration.processor.core.util.RegistrationExceptionMapperUtil;
import io.mosip.registration.processor.packet.storage.exception.IdentityNotFoundException;
import io.mosip.registration.processor.packet.storage.exception.ParsingException;

import io.mosip.registration.processor.rest.client.audit.builder.AuditLogRequestBuilder;
import io.mosip.registration.processor.core.packet.dto.packetvalidator.PacketValidationDto;
import io.mosip.registration.processor.core.exception.PacketValidatorException;
import io.mosip.registration.processor.stages.utils.AuditUtility;
import io.mosip.registration.processor.core.spi.packet.validator.PacketValidator;
import io.mosip.registration.processor.status.code.RegistrationStatusCode;
import io.mosip.registration.processor.status.dto.InternalRegistrationStatusDto;
import io.mosip.registration.processor.status.dto.RegistrationStatusDto;
import io.mosip.registration.processor.status.dto.SyncRegistrationDto;
import io.mosip.registration.processor.status.dto.SyncResponseDto;
import io.mosip.registration.processor.status.entity.SyncRegistrationEntity;
import io.mosip.registration.processor.status.exception.TablenotAccessibleException;
import io.mosip.registration.processor.status.service.RegistrationStatusService;
import io.mosip.registration.processor.status.service.SyncRegistrationService;

@Service
@Transactional
public class PacketValidateProcessor {

	/** The Constant FILE_SEPARATOR. */
	public static final String FILE_SEPARATOR = "\\";

	/** The reg proc logger. */
	private static Logger regProcLogger = RegProcessorLogger.getLogger(PacketValidateProcessor.class);

	@Autowired
	private PacketReaderService packetReaderService;

	@Autowired
	private PacketValidator compositePacketValidator;

	/** The Constant USER. */
	private static final String USER = "MOSIP_SYSTEM";

	/** The Constant APPLICANT_TYPE. */
	public static final String APPLICANT_TYPE = "applicantType";

	/** The Constant APPROVED. */
	public static final String APPROVED = "APPROVED";

	/** The Constant REJECTED. */
	public static final String REJECTED = "REJECTED";

	/** The registration status service. */
	@Autowired
	RegistrationStatusService<String, InternalRegistrationStatusDto, RegistrationStatusDto> registrationStatusService;

	/** The core audit request builder. */
	@Autowired
	AuditLogRequestBuilder auditLogRequestBuilder;
	
	@Autowired
	private RegistrationProcessorRestClientService<Object> restClientService;

	@Autowired
	private AuditUtility auditUtility;

	/** The sync registration service. */
	@Autowired
	private SyncRegistrationService<SyncResponseDto, SyncRegistrationDto> syncRegistrationService;
	
	@Value("${packet.default.source}")
	private String source;

	private static final String PRE_REG_ID = "mosip.pre-registration.datasync.store";
	private static final String VERSION = "1.0";

	String registrationId = null;

	@Autowired
	RegistrationExceptionMapperUtil registrationStatusMapperUtil;

	public MessageDTO process(MessageDTO object, String stageName) {
		TrimExceptionMessage trimMessage = new TrimExceptionMessage();
		LogDescription description = new LogDescription();
		PacketValidationDto packetValidationDto = new PacketValidationDto();
		String preRegId = null;

		InternalRegistrationStatusDto registrationStatusDto = new InternalRegistrationStatusDto();
		try {

			object.setMessageBusAddress(MessageBusAddress.PACKET_VALIDATOR_BUS_IN);
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.FALSE);
			regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					"", "PacketValidateProcessor::process()::entry");
			registrationId = object.getRid();
			packetValidationDto.setTransactionSuccessful(false);

			registrationStatusDto = registrationStatusService.getRegistrationStatus(registrationId);
			registrationStatusDto
					.setLatestTransactionTypeCode(RegistrationTransactionTypeCode.VALIDATE_PACKET.toString());
			registrationStatusDto.setRegistrationStageName(stageName);
			boolean isValidSupervisorStatus = isValidSupervisorStatus();
			if (isValidSupervisorStatus) {
			Boolean isValid = compositePacketValidator.validate(object.getRid(), object.getReg_type().toString(), packetValidationDto);
			if (isValid) {
				// save audit details
				Runnable r = () -> {
					try {
						auditUtility.saveAuditDetails(registrationId,source);
					} catch (Exception e) {
						regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
								LoggerFileConstant.REGISTRATIONID.toString(),
								description.getCode() + " Inside Runnable ", "");

					}
				};
				ExecutorService es = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
				es.submit(r);
				es.shutdown();
				registrationStatusDto
						.setLatestTransactionStatusCode(RegistrationTransactionStatusCode.SUCCESS.toString());
				object.setIsValid(Boolean.TRUE);
				registrationStatusDto.setStatusComment(StatusUtil.PACKET_STRUCTURAL_VALIDATION_SUCCESS.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.PACKET_STRUCTURAL_VALIDATION_SUCCESS.getCode());
				registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
				// ReverseDataSync
				InputStream packetMetaInfoStream = packetReaderService.getFile(registrationId,
						PacketFiles.PACKET_META_INFO.name(),source);
				PacketMetaInfo packetMetaInfo = (PacketMetaInfo) JsonUtil.inputStreamtoJavaObject(packetMetaInfoStream,
						PacketMetaInfo.class);

				IdentityIteratorUtil identityIteratorUtil = new IdentityIteratorUtil();
				preRegId = identityIteratorUtil.getFieldValue(packetMetaInfo.getIdentity().getMetaData(),
						JsonConstant.PREREGISTRATIONID);
				reverseDataSync(preRegId, registrationId, description, packetValidationDto);

				object.setRid(registrationStatusDto.getRegistrationId());
				packetValidationDto.setTransactionSuccessful(true);
				description.setMessage(
						PlatformSuccessMessages.RPR_PKR_PACKET_VALIDATE.getMessage() + " -- " + registrationId);
				description.setCode(PlatformSuccessMessages.RPR_PKR_PACKET_VALIDATE.getCode());

				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						description.getCode() + description.getMessage());

			} else {
				registrationStatusDto.setLatestTransactionStatusCode(registrationStatusMapperUtil
						.getStatusCode(RegistrationExceptionTypeCode.PACKET_STRUCTURAL_VALIDATION_FAILED));
				object.setIsValid(Boolean.FALSE);
				int retryCount = registrationStatusDto.getRetryCount() != null
						? registrationStatusDto.getRetryCount() + 1
						: 1;
				description.setMessage("File validation(" + packetValidationDto.isFilesValidated()
						+ ")/Checksum validation(" + packetValidationDto.isCheckSumValidated() + ")"
						+ "/Applicant Document Validation(" + packetValidationDto.isApplicantDocumentValidation() + ")"
						+ "/Schema Validation(" + packetValidationDto.isSchemaValidated() + ")"
						+ "/Master Data Validation(" + packetValidationDto.isMasterDataValidation() + ")"
						+ "/MandatoryField Validation(" + packetValidationDto.isMandatoryValidation() + ")"
						+ "/isRidAndType Sync Validation(" + packetValidationDto.isRIdAndTypeSynched() + ")"
						+ " failed for registrationId " + registrationId);
				packetValidationDto.setTransactionSuccessful(false);
				registrationStatusDto.setRetryCount(retryCount);
				registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
				registrationStatusDto.setStatusComment(packetValidationDto.getPacketValidaionFailureMessage());
				registrationStatusDto.setSubStatusCode(packetValidationDto.getPacketValidatonStatusCode());

				description.setMessage(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getMessage());
				description.setCode(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getCode());

				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), description.getCode() + " -- " + registrationId,
						description.getMessage());

				}
			} else {
				registrationStatusDto.setLatestTransactionStatusCode(
						registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.PACKET_REJECTED));
				object.setIsValid(Boolean.FALSE);
				int retryCount = registrationStatusDto.getRetryCount() != null
						? registrationStatusDto.getRetryCount() + 1
						: 1;
				packetValidationDto.setTransactionSuccessful(false);
				registrationStatusDto.setRetryCount(retryCount);
				registrationStatusDto.setStatusCode(RegistrationStatusCode.REJECTED.toString());
				registrationStatusDto.setStatusComment(StatusUtil.PACKET_REJECTED.getMessage());
				registrationStatusDto.setSubStatusCode(StatusUtil.PACKET_REJECTED.getCode());

				description.setMessage(PlatformErrorMessages.RPR_PVM_PACKET_REJECTED.getMessage());
				description.setCode(PlatformErrorMessages.RPR_PVM_PACKET_REJECTED.getCode());

				regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
						LoggerFileConstant.REGISTRATIONID.toString(), description.getCode() + " -- " + registrationId,
						description.getMessage());
			}
			registrationStatusDto.setUpdatedBy(USER);

		}  catch(PacketValidatorException e) {
			managePacketValidatorException(e, registrationStatusDto, trimMessage, description, object, packetValidationDto);
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), description.getCode(), registrationId,
					description + e.getMessage() + ExceptionUtils.getStackTrace(e));

		}
		 catch (DataAccessException e) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
			registrationStatusDto.setStatusComment(
					trimMessage.trimExceptionMessage(StatusUtil.DB_NOT_ACCESSIBLE.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.DB_NOT_ACCESSIBLE.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.DATA_ACCESS_EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE.getMessage());
			description.setCode(PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE.getCode());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					description.getCode() + " -- " + registrationId,
					PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE.getMessage() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());
		} catch (IdentityNotFoundException | IOException exc) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
			registrationStatusDto.setStatusComment(
					trimMessage.trimExceptionMessage(StatusUtil.IO_EXCEPTION.getMessage() + exc.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.IO_EXCEPTION.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.IOEXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_SYS_IO_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getCode());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					description.getCode() + " -- " + registrationId,
					PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getMessage() + exc.getMessage()
							+ ExceptionUtils.getStackTrace(exc));
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());

		} catch (ParsingException exc) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
			registrationStatusDto.setStatusComment(trimMessage
					.trimExceptionMessage(StatusUtil.JSON_PARSING_EXCEPTION.getMessage() + exc.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.JSON_PARSING_EXCEPTION.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.PARSE_EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_SYS_JSON_PARSING_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getCode());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					description.getCode() + " -- " + registrationId,
					PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getMessage() + exc.getMessage()
							+ ExceptionUtils.getStackTrace(exc));
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());

		} catch (TablenotAccessibleException e) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
			registrationStatusDto.setStatusComment(
					trimMessage.trimExceptionMessage(StatusUtil.DB_NOT_ACCESSIBLE.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.DB_NOT_ACCESSIBLE.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(registrationStatusMapperUtil
					.getStatusCode(RegistrationExceptionTypeCode.TABLE_NOT_ACCESSIBLE_EXCEPTION));
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			description.setMessage(PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE.getMessage());
			description.setCode(PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE.getCode());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(),
					description.getCode() + " -- " + registrationId,
					PlatformErrorMessages.RPR_RGS_REGISTRATION_TABLE_NOT_ACCESSIBLE.getMessage(),
					ExceptionUtils.getStackTrace(e));

		} catch (BaseCheckedException e) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
			registrationStatusDto.setStatusComment(
					trimMessage.trimExceptionMessage(StatusUtil.BASE_CHECKED_EXCEPTION.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.BASE_CHECKED_EXCEPTION.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.BASE_CHECKED_EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_PVM_BASE_CHECKED_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.RPR_PVM_BASE_CHECKED_EXCEPTION.getCode());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					description.getCode() + " -- " + registrationId,
					PlatformErrorMessages.RPR_PVM_BASE_CHECKED_EXCEPTION.getMessage() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());
		} catch (BaseUncheckedException e) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
			registrationStatusDto.setStatusComment(trimMessage
					.trimExceptionMessage(StatusUtil.BASE_UNCHECKED_EXCEPTION.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.BASE_UNCHECKED_EXCEPTION.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.BASE_UNCHECKED_EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_PVM_BASE_UNCHECKED_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.RPR_PVM_BASE_UNCHECKED_EXCEPTION.getCode());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					description.getCode() + " -- " + registrationId,
					PlatformErrorMessages.RPR_PVM_BASE_UNCHECKED_EXCEPTION.getMessage() + e.getMessage()
							+ ExceptionUtils.getStackTrace(e));
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());

		} catch (Exception ex) {
			registrationStatusDto.setStatusComment(trimMessage
					.trimExceptionMessage(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getMessage() + ex.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getMessage());
			description.setCode(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getCode());
			regProcLogger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.REGISTRATIONID.toString(),
					description.getCode() + " -- " + registrationId,
					PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getMessage() + ex.getMessage()
							+ ExceptionUtils.getStackTrace(ex));
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());

		} finally {

			if (object.getInternalError()) {
				registrationStatusDto.setUpdatedBy(USER);
				int retryCount = registrationStatusDto.getRetryCount() != null
						? registrationStatusDto.getRetryCount() + 1
						: 1;
				registrationStatusDto.setRetryCount(retryCount);
			}
			/** Module-Id can be Both Success/Error code */
			String moduleId = packetValidationDto.isTransactionSuccessful()
					? PlatformSuccessMessages.RPR_PKR_PACKET_VALIDATE.getCode()
					: description.getCode();
			String moduleName = ModuleName.PACKET_VALIDATOR.toString();
			registrationStatusService.updateRegistrationStatus(registrationStatusDto, moduleId, moduleName);
			if (packetValidationDto.isTransactionSuccessful())
				description.setMessage(PlatformSuccessMessages.RPR_PKR_PACKET_VALIDATE.getMessage());
			String eventId = packetValidationDto.isTransactionSuccessful() ? EventId.RPR_402.toString()
					: EventId.RPR_405.toString();
			String eventName = packetValidationDto.isTransactionSuccessful() ? EventName.UPDATE.toString()
					: EventName.EXCEPTION.toString();
			String eventType = packetValidationDto.isTransactionSuccessful() ? EventType.BUSINESS.toString()
					: EventType.SYSTEM.toString();

			auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType,
					moduleId, moduleName, registrationId);
		}

		return object;

	}

	private void managePacketValidatorException(PacketValidatorException e,InternalRegistrationStatusDto registrationStatusDto,
			TrimExceptionMessage trimMessage, LogDescription description, MessageDTO object, PacketValidationDto packetValidationDto) {
		if ( e.getCause() instanceof ApisResourceAccessException || e.getCause() instanceof ApiNotAccessibleException) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.PROCESSING.toString());
			registrationStatusDto.setStatusComment(trimMessage
					.trimExceptionMessage(StatusUtil.API_RESOUCE_ACCESS_FAILED.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.API_RESOUCE_ACCESS_FAILED.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(registrationStatusMapperUtil
					.getStatusCode(RegistrationExceptionTypeCode.APIS_RESOURCE_ACCESS_EXCEPTION));
			description.setCode(PlatformErrorMessages.RPR_PVM_API_RESOUCE_ACCESS_FAILED.getCode());
			description.setMessage(PlatformErrorMessages.RPR_PVM_API_RESOUCE_ACCESS_FAILED.getMessage());
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
		}
		else if (e.getCause() instanceof BaseCheckedException) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
			registrationStatusDto.setStatusComment(
					trimMessage.trimExceptionMessage(StatusUtil.BASE_CHECKED_EXCEPTION.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.BASE_CHECKED_EXCEPTION.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.BASE_CHECKED_EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_PVM_BASE_CHECKED_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.RPR_PVM_BASE_CHECKED_EXCEPTION.getCode());
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());
		}  
		else if (e.getCause() instanceof BaseUncheckedException ) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
			registrationStatusDto.setStatusComment(trimMessage
					.trimExceptionMessage(StatusUtil.BASE_UNCHECKED_EXCEPTION.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.BASE_UNCHECKED_EXCEPTION.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.BASE_UNCHECKED_EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_PVM_BASE_UNCHECKED_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.RPR_PVM_BASE_UNCHECKED_EXCEPTION.getCode());
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());
		} 
		else if (e.getCause() instanceof IOException ) {
			registrationStatusDto.setStatusCode(RegistrationStatusCode.FAILED.toString());
			registrationStatusDto.setStatusComment(
					trimMessage.trimExceptionMessage(StatusUtil.IO_EXCEPTION.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.IO_EXCEPTION.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.IOEXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.RPR_SYS_IO_EXCEPTION.getMessage());
			description.setCode(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getCode());
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());
		}else if (e.getCause() instanceof Exception) {
			registrationStatusDto.setStatusComment(trimMessage
					.trimExceptionMessage(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getMessage() + e.getMessage()));
			registrationStatusDto.setSubStatusCode(StatusUtil.UNKNOWN_EXCEPTION_OCCURED.getCode());
			registrationStatusDto.setLatestTransactionStatusCode(
					registrationStatusMapperUtil.getStatusCode(RegistrationExceptionTypeCode.EXCEPTION));
			packetValidationDto.setTransactionSuccessful(false);
			description.setMessage(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getMessage());
			description.setCode(PlatformErrorMessages.STRUCTURAL_VALIDATION_FAILED.getCode());
			object.setIsValid(Boolean.FALSE);
			object.setInternalError(Boolean.TRUE);
			object.setRid(registrationStatusDto.getRegistrationId());
		}
		
	}

	private boolean isValidSupervisorStatus() {
		SyncRegistrationEntity regEntity = syncRegistrationService.findByRegistrationId(registrationId);
		if (regEntity.getSupervisorStatus().equalsIgnoreCase(APPROVED)) {
			return true;

		} else if (regEntity.getSupervisorStatus().equalsIgnoreCase(REJECTED)) {
			return false;
		}
		return false;
	}





	private void reverseDataSync(String preRegId, String registrationId, LogDescription description,
			PacketValidationDto packetValidationDto) throws IOException {
		if (preRegId == null || preRegId.trim().isEmpty()) {
			regProcLogger.info(LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
					"Pre-registration id not present.", "Reverse datasync is not applicable for the registration id");
			return;
		}
		try {
			if (registrationId != null) {
				packetValidationDto.setTransactionSuccessful(false);
				MainResponseDTO<ReverseDatasyncReponseDTO> mainResponseDto = null;
				if (preRegId != null && !preRegId.trim().isEmpty()) {
					MainRequestDTO<ReverseDataSyncRequestDTO> mainRequestDto = new MainRequestDTO<>();
					mainRequestDto.setId(PRE_REG_ID);
					mainRequestDto.setVersion(VERSION);
					mainRequestDto.setRequesttime(new Date());
					ReverseDataSyncRequestDTO reverseDataSyncRequestDto = new ReverseDataSyncRequestDTO();
					reverseDataSyncRequestDto.setPreRegistrationIds(Arrays.asList(preRegId));
					mainRequestDto.setRequest(reverseDataSyncRequestDto);
					regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
							"PacketValidateProcessor::reverseDataSync()::ReverseDataSync Api call started with request data :"
									+ JsonUtil.objectMapperObjectToJson(mainRequestDto));
					mainResponseDto = (MainResponseDTO) restClientService.postApi(ApiName.REVERSEDATASYNC, "", "",
							mainRequestDto, MainResponseDTO.class);

					regProcLogger.debug(LoggerFileConstant.SESSIONID.toString(),
							LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
							"\"PacketValidateProcessor::reverseDataSync()::ReverseDataSync Api call ended with response data : "
									+ JsonUtil.objectMapperObjectToJson(mainResponseDto));
					packetValidationDto.setTransactionSuccessful(true);

				}
				if (mainResponseDto != null && mainResponseDto.getErrors() != null
						&& mainResponseDto.getErrors().size() > 0) {
					regProcLogger.error(LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
							PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage(),
							mainResponseDto.getErrors().toString());
					packetValidationDto.setTransactionSuccessful(false);
					description.setMessage(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage());

				} else if (mainResponseDto == null) {
					packetValidationDto.setTransactionSuccessful(false);
					description.setMessage(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage()
							+ " null response from rest client ");
				} else {
					packetValidationDto.setTransactionSuccessful(true);
					regProcLogger.info(LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
							PlatformErrorMessages.REVERSE_DATA_SYNC_SUCCESS.getMessage(), "");
				}

			}

		} catch (ApisResourceAccessException e) {

			if (e.getCause() instanceof HttpClientErrorException) {
				HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
				regProcLogger.info(LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage(),
						httpClientException.getResponseBodyAsString() + ExceptionUtils.getStackTrace(e));
				description.setMessage(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage());
				description.setCode(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getCode());
			} else if (e.getCause() instanceof HttpServerErrorException) {
				HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
				regProcLogger.info(LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage(),
						httpServerException.getResponseBodyAsString() + ExceptionUtils.getStackTrace(e));
				description.setMessage(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage());
				description.setCode(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getCode());
			} else {
				regProcLogger.info(LoggerFileConstant.REGISTRATIONID.toString(), registrationId,
						PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage(), e.getMessage());
				description.setMessage(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getMessage());
				description.setCode(PlatformErrorMessages.REVERSE_DATA_SYNC_FAILED.getCode());
			}

		} finally {
			if (packetValidationDto.isTransactionSuccessful())
				description.setMessage("Reverse data sync of Pre-RegistrationIds sucessful");
			String eventId = packetValidationDto.isTransactionSuccessful() ? EventId.RPR_402.toString()
					: EventId.RPR_405.toString();
			String eventName = packetValidationDto.isTransactionSuccessful() ? EventName.UPDATE.toString()
					: EventName.EXCEPTION.toString();
			String eventType = packetValidationDto.isTransactionSuccessful() ? EventType.BUSINESS.toString()
					: EventType.SYSTEM.toString();

			/** Module-Id can be Both Success/Error code */
			String moduleId = packetValidationDto.isTransactionSuccessful()
					? PlatformSuccessMessages.RPR_PKR_PACKET_VALIDATE.getCode()
					: description.getCode();
			String moduleName = ModuleName.PACKET_VALIDATOR.toString();
			auditLogRequestBuilder.createAuditRequestBuilder(description.getMessage(), eventId, eventName, eventType,
					moduleId, moduleName, registrationId);
		}

	}

}
