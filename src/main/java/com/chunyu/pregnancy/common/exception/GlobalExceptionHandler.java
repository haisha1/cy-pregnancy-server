package com.chunyu.pregnancy.common.exception;

import com.chunyu.pregnancy.common.result.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** MVC 层错误处理；Spring Security 过滤器层的错误在 SecurityConfig 单独输出。 */
// 集中处理 Controller 抛出的异常并生成统一响应；安全过滤器异常另行处理。
@RestControllerAdvice
public class GlobalExceptionHandler {
  // 指定此方法处理的异常类型，将预期失败转换成对应 HTTP 状态和错误包体。
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiResponse<Void>> uploadTooLarge(Exception ex) {
    return ResponseEntity.status(413).body(ApiResponse.error("AVATAR_TOO_LARGE", "头像不能超过2MB"));
  }

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(BizException.class)
  public ResponseEntity<ApiResponse<Void>> business(BizException ex) {
    return ResponseEntity.status(ex.getStatus())
        .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
    return ResponseEntity.unprocessableEntity()
        .body(ApiResponse.error("VALIDATION_FAILED", "请求字段缺失或格式无效"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> malformed(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "JSON格式或字段无效"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
    // 不直接打印异常 message/stack：第三方异常可能带 URL、code、SQL 参数等敏感数据。
    LOG.error("请求处理失败，异常类型={}", ex.getClass().getSimpleName());
    return ResponseEntity.status(500).body(ApiResponse.error("INTERNAL_ERROR", "服务处理失败，请稍后重试"));
  }

  @ExceptionHandler({
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class
  })
  public ResponseEntity<ApiResponse<Void>> queryFormat(Exception ex) {
    return ResponseEntity.badRequest().body(ApiResponse.error("BAD_REQUEST", "查询参数格式无效"));
  }
}
