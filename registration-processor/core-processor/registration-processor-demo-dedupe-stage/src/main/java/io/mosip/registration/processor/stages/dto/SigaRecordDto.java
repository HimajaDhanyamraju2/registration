package io.mosip.registration.processor.stages.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SigaRecordDto {

	@JsonProperty("NIP")
	private String nip;

	@JsonProperty("FullName")
	private String fullName;

	@JsonProperty("Gender")
	private String gender;

	@JsonProperty("BirthDate")
	private String birthDate;

	@JsonProperty("PlaceOfBirth")
	private String placeOfBirth;
}
