package org.jsut.dto;

/**
 * POST 请求数据对象。
 *
 * 前端发送：
 *
 * {
 *     "message": "hello"
 * }
 *
 * Spring 会自动把 JSON 转换成 EchoRequest。
 */
public record EchoRequest(
        String message
) {
}