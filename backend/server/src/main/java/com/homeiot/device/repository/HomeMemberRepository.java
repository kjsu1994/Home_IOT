package com.homeiot.device.repository;

import com.homeiot.device.entity.HomeGroup;
import com.homeiot.device.entity.HomeMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HomeMemberRepository extends JpaRepository<HomeMember, Long> {
    List<HomeMember> findAllByHomeOrderByCreatedAtAsc(HomeGroup home);
    List<HomeMember> findAllByMember_IdOrderByCreatedAtAsc(Long memberId);
    Optional<HomeMember> findByHomeAndMember_Id(HomeGroup home, Long memberId);
}
