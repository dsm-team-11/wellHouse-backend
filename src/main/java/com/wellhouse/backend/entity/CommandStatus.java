package com.wellhouse.backend.entity;

/** 명령 실행 상태. */
public enum CommandStatus {
    PENDING,   // 발행됨, ACK 대기
    ACK_OK,    // 성공 ACK
    ACK_FAIL,  // 실패 ACK
    TIMEOUT    // 10초 내 무응답
}
