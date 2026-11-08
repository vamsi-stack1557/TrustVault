package com.snovasys.trustvault.web;

import com.snovasys.trustvault.dto.ApiDtos.MessageResponse;
import com.snovasys.trustvault.service.VaultExceptions.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotFound.class) public ResponseEntity<MessageResponse> notFound() { return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse("Resource not found")); }
    @ExceptionHandler(Forbidden.class) public ResponseEntity<MessageResponse> forbidden() { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("You are not allowed to perform this action")); }
    @ExceptionHandler(BadRequest.class) public ResponseEntity<MessageResponse> badRequest(BadRequest ex) { return ResponseEntity.badRequest().body(new MessageResponse(ex.getMessage())); }
    @ExceptionHandler(Conflict.class) public ResponseEntity<MessageResponse> conflict(Conflict ex) { return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponse(ex.getMessage())); }
    @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<MessageResponse> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("Validation failed");
        return ResponseEntity.badRequest().body(new MessageResponse(message));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class) public ResponseEntity<MessageResponse> tooLarge() { return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new MessageResponse("File exceeds the 10 MB limit")); }
    @ExceptionHandler(HttpMessageNotReadableException.class) public ResponseEntity<MessageResponse> unreadable() { return ResponseEntity.badRequest().body(new MessageResponse("Invalid request body")); }
}
