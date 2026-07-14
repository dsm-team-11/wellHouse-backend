package com.wellhouse.backend.web.dto;

/** 페어링 응답. deviceToken 은 앱이 펌웨어에 전달(BLE 등). */
public record PairResponse(boolean ok, String deviceId, String deviceToken) {}
