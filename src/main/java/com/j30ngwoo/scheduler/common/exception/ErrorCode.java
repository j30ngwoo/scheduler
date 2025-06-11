package com.j30ngwoo.scheduler.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT_VALUE("잘못된 입력입니다", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("인증이 필요합니다", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("권한이 없습니다", HttpStatus.FORBIDDEN),
    INTERNAL_SERVER_ERROR("서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // 유저
    USER_NOT_FOUND("사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // 토큰
    REFRESH_TOKEN_INVALID("유효하지 않은 리프레시 토큰입니다", HttpStatus.UNAUTHORIZED),

    // OAUTH
    OAUTH_COMMUNICATION_FAILED("외부 OAuth 서버와의 통신에 실패했습니다", HttpStatus.BAD_GATEWAY);

    private final String message;
    private final HttpStatus status;

}
