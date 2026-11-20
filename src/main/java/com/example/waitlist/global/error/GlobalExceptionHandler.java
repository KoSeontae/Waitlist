package com.example.waitlist.global.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		return respond(e.getErrorCode());
	}

	@ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
	ResponseEntity<ErrorResponse> handleInvalidRequest(Exception e) {
		return respond(ErrorCode.VALIDATION_FAILED);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException e) {
		return respond(ErrorCode.CONFLICT);
	}

	private ResponseEntity<ErrorResponse> respond(ErrorCode code) {
		return ResponseEntity.status(code.getStatus()).body(ErrorResponse.of(code));
	}

}
