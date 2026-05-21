package com.homeiot.device.entity;

import com.homeiot.member.entity.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;

@Entity
@Table(name = "home_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_home_members_home_member", columnNames = {"home_id", "member_id"})
})
public class HomeMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_id", nullable = false)
    private HomeGroup home;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(nullable = false, length = 30)
    private String role = DeviceShare.ROLE_CONTROLLER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected HomeMember() {
    }

    public HomeMember(HomeGroup home, Member member, String role) {
        this.home = home;
        this.member = member;
        this.role = DeviceShare.normalizeRole(role);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    public HomeGroup getHome() {
        return home;
    }

    public Member getMember() {
        return member;
    }

    public String getRole() {
        return DeviceShare.normalizeRole(role);
    }
}
