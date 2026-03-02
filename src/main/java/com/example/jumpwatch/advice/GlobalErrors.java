package com.example.jumpwatch.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
/**
 * 项目名称: jumpwatch-remote-restore
 * 类名称: MonitorApplication
 *
 * @author blx
 * @date 2026-03-02
 * @version 7.0
 * @description 服务监控启动类
 */
@RestControllerAdvice
public class GlobalErrors {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> bad(IllegalArgumentException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
