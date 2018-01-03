package com.kingfisher.payment.api.error;

import com.kingfisher.payment.api.optile.model.ErrorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Order(value = 2)
@ControllerAdvice
public class GeneralExceptionHandler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @ExceptionHandler({Exception.class})
    public ResponseEntity handle(Exception e){
        logger.trace("Processing: {}", e.getStackTrace());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorInfo info = new ErrorInfo(status.getReasonPhrase());

        return ResponseEntity.status(status.value()).body(info);
    }
}
