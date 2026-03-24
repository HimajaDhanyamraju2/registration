package io.mosip.registration.processor.packet.storage.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode()
public class IdRequestDTO1 {
    private String id;
    private String idType;
}
