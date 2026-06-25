package com.aurora.dating.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.aurora.dating.post.controller")
public class PostDebugExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(new ErrorResponse(false, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgumentException(IllegalArgumentException e) {
        return new ErrorResponse(false, translateMessage(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleException(Exception e) {
        return new ErrorResponse(false, "服务器内部错误");
    }

    private String translateMessage(String message) {
        if (message == null || message.isBlank()) {
            return "请求参数错误";
        }

        return switch (message) {
            case "userId is required" -> "用户ID不能为空";
            case "viewerUserId is required" -> "查看用户ID不能为空";
            case "targetUserId is required" -> "目标用户ID不能为空";
            case "postId is required" -> "帖子ID不能为空";
            case "content is required" -> "内容不能为空";
            case "content length must be <= 1024" -> "帖子内容不能超过1024个字符";
            case "content length must be <= 512" -> "评论内容不能超过512个字符";
            case "image count must be <= 9" -> "图片数量不能超过9张";
            default -> message;
        };
    }

    public record ErrorResponse(
            boolean success,
            String message
    ) {
    }
}
