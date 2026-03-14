package com.scbrbackend.common.exception;

import com.scbrbackend.common.Result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        log.warn("业务异常：{}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        log.error("系统运行异常：", e);
        return Result.error(500, "系统内部发生未知错误，请稍后重试！");
    }

    /**
     * 捕获并处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        // 打印简短日志，避免控制台刷屏
        System.err.println("文件上传失败：上传的文件大小超出了限制");

        // ⚠️ 注意：下面的 Result.error() 需要替换成你们项目中实际使用的统一返回体对象
        // 比如你们可能用的是 R.error(), AjaxResult.error() 或者 ResponseEntity 等
        return Result.error(500, "上传的文件过大，请上传100MB以内的文件！");
    }
}
