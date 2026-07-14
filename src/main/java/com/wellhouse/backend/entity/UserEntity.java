package com.wellhouse.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/** 사용자 계정 + 집 정보. */
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserEntity {

    @Id
    private String uid;

    @Column(unique = true)
    private String email;

    /** 비밀번호 재설정 인증코드를 받을 실제 이메일(가입 시 수집). */
    private String recoveryEmail;

    @JsonIgnore
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_fcm_tokens", joinColumns = @JoinColumn(name = "uid"))
    @Column(name = "token")
    @Builder.Default
    private Set<String> fcmTokens = new HashSet<>();

    // 집 정보 (대비점수/골든타임 입력)
    private Double homeAreaM2;
    private Boolean isSemiBasement;
    private Double homeLat;
    private Double homeLng;
    private Integer windowCount;
}
