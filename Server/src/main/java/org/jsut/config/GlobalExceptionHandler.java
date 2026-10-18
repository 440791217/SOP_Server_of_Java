package org.jsut.config;

import jakarta.validation.ConstraintViolationException;
import org.jsut.common.ApiResponse;
import org.jsut.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "org.jsut")
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> uploadTooLarge(Exception exception){return error(413,"上传图片过大，单张不能超过5MiB");}
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException exception) {
        return error(exception.getStatus().value(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException exception) {
        var fieldError = exception.getBindingResult().getFieldError();
        return error(400, fieldError == null ? "请求参数不正确" : fieldError.getDefaultMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> badRequest(Exception exception) {
        return error(400, "请求参数不正确");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException exception) {
        return error(409, "数据重复或关联约束冲突");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse response) {
            int status = response.getStatusCode().value();
            return error(status, "请求失败");
        }
        log.error("请求处理失败", exception);
        return error(500, "服务器内部错误");
    }

    private ResponseEntity<ApiResponse<Void>> error(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status, message));
    }
}
