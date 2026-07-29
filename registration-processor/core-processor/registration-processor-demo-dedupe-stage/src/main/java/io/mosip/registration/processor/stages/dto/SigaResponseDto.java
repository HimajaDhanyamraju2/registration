package io.mosip.registration.processor.stages.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SigaResponseDto {

	private List<SigaRecordDto> data;
}
