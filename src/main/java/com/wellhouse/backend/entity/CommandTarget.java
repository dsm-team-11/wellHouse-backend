package com.wellhouse.backend.entity;

/** 명령 대상 장치. */
public enum CommandTarget {
    POWER,       // 두꺼비집(전원) 차단
    WINDOW,      // 창문 서보
    WATER_GATE,  // 물막이판
    WAKEUP       // 절전 해제
}
