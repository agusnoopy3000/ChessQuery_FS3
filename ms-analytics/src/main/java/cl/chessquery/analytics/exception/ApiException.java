package cl.chessquery.analytics.exception;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final int    status;
    private final String error;

    public ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error  = error;
    }
}
