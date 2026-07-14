package com.wellhouse.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 비상 연락망 항목 (사용자별 목록). */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EmergencyContact {

    private String name;

    /** 관계(예: 가족, 이웃). "relation"은 일부 DB 예약어라 컬럼명은 relationship. */
    @Column(name = "relationship")
    private String relation;

    private String phone;
}
