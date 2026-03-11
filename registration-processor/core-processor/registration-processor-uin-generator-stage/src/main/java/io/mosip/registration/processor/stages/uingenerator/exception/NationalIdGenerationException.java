package io.mosip.registration.processor.stages.uingenerator.exception;

import io.mosip.registration.processor.core.exception.util.PlatformErrorMessages;

/**
 * Exception thrown when National ID generation fails
 *
 * @author Registration Processor
 */
public class NationalIdGenerationException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Instantiates a new national id generation exception.
     */
    public NationalIdGenerationException() {
        super();
    }

    /**
     * Instantiates a new national id generation exception.
     *
     * @param message the message
     */
    public NationalIdGenerationException(String message) {
        super(message);
    }

    /**
     * Instantiates a new national id generation exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public NationalIdGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Instantiates a new national id generation exception.
     *
     * @param errorCode the error code
     * @param errorMessage the error message
     */
    public NationalIdGenerationException(PlatformErrorMessages errorCode, String errorMessage) {
        super(errorCode + " --> " + errorMessage);
    }

    /**
     * Instantiates a new national id generation exception.
     *
     * @param errorCode the error code
     * @param errorMessage the error message
     * @param rootCause the root cause
     */
    public NationalIdGenerationException(PlatformErrorMessages errorCode, String errorMessage, Throwable rootCause) {
        super(errorCode + " --> " + errorMessage, rootCause);
    }
}
