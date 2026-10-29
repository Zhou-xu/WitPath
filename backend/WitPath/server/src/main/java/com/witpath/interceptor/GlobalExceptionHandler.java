package com.witpath.interceptor;



import com.witpath.exception.BusinessException;
import com.witpath.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice(basePackages = "com.witpath.controller")
@Slf4j
public class GlobalExceptionHandler {

    // 业务自定义异常
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e){
        log.error("业务异常：{}",e.getMessage(),e);
        return Result.error(e.getCode(),e.getMessage());
    }

    // 参数校验异常 @Valid
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidException(MethodArgumentNotValidException e){
        if (e.getBindingResult().getFieldError() != null) {
            String msg = e.getBindingResult().getFieldError().getDefaultMessage();
            log.error("参数校验失败：{}",msg);
            return Result.error(400,msg);
        }
        log.error("getDefaultMessage为null，参数校验失败：{}",e.getMessage());
        return Result.error(400,"参数校验失败");
    }

    // 兜底：所有其他未知异常
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e){
        log.error("系统未知异常：",e);
        return Result.error(500,"服务器内部错误");
    }
}
