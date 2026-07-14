package com.wellhouse.backend.web.dto;

/** 로그인/회원가입 응답. */
public record TokenResponse(String uid, String accessToken) {}
