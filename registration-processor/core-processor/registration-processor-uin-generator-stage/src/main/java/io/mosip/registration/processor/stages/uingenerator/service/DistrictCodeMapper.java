package io.mosip.registration.processor.stages.uingenerator.service;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.registration.processor.core.constant.LoggerFileConstant;
import io.mosip.registration.processor.core.logger.RegProcessorLogger;

/**
 * Service for mapping district names/codes to 2-digit geographic codes
 *
 * @author Registration Processor
 */
@Service
public class DistrictCodeMapper {

    private static Logger regProcLogger = RegProcessorLogger.getLogger(DistrictCodeMapper.class);

    /**
     * Configuration property for district code mapping
     * Format: "DistrictName1:01,DistrictName2:02,DistrictName3:03"
     */
    @Value("${mosip.regproc.national-id.district-code-mapping:Agua Grande:21,Cantagalo:22,Caue:23,Lemba:24,Lobata:25,Mezochi:26,Rap:11}")
    private String districtCodeMappingConfig;

    /**
     * Default district code if mapping not found
     */
    @Value("${mosip.regproc.national-id.default-district-code:99}")
    private String defaultDistrictCode;

    private Map<String, String> districtCodeMap = new HashMap<>();

    /**
     * Initialize the district code mapping from configuration
     */
    @PostConstruct
    public void init() {
        if (districtCodeMappingConfig != null && !districtCodeMappingConfig.isEmpty()) {
            String[] mappings = districtCodeMappingConfig.split(",");
            for (String mapping : mappings) {
                String[] parts = mapping.trim().split(":");
                if (parts.length == 2) {
                    String districtName = parts[0].trim().toUpperCase();
                    String code = parts[1].trim();

                    // Validate code is 2 digits
                    if (code.matches("\\d{2}")) {
                        districtCodeMap.put(districtName, code);
                        regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
                                "DistrictCodeMapper", "init",
                                "Mapped district: " + districtName + " to code: " + code);
                    } else {
                        regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(),
                                "DistrictCodeMapper", "init",
                                "Invalid district code format: " + code + " for district: " + districtName);
                    }
                }
            }
            regProcLogger.info(LoggerFileConstant.SESSIONID.toString(),
                    "DistrictCodeMapper", "init",
                    "Loaded " + districtCodeMap.size() + " district code mappings");
        } else {
            regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(),
                    "DistrictCodeMapper", "init",
                    "No district code mapping configured. Using default code: " + defaultDistrictCode);
        }
    }

    /**
     * Get the 2-digit geographic code for a district
     *
     * @param districtName the district name (case-insensitive)
     * @return the 2-digit district code, or default code if not found
     */
    public String getDistrictCode(String districtName) {
        if (districtName == null || districtName.isEmpty()) {
            regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(),
                    "DistrictCodeMapper", "getDistrictCode",
                    "District name is null or empty. Using default code: " + defaultDistrictCode);
            return defaultDistrictCode;
        }

        String normalizedName = districtName.trim().toUpperCase();
        String code = districtCodeMap.get(normalizedName);

        if (code == null) {
            regProcLogger.warn(LoggerFileConstant.SESSIONID.toString(),
                    "DistrictCodeMapper", "getDistrictCode",
                    "District not found in mapping: " + districtName + ". Using default code: " + defaultDistrictCode);
            return defaultDistrictCode;
        }

        return code;
    }
}
