package com.echo.chatservice.repository;

import com.echo.chatservice.entity.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, String> {
    List<GroupMember> findByGroupId(String groupId);
    List<GroupMember> findByUserId(String userId);
    Optional<GroupMember> findByGroupIdAndUserId(String groupId, String userId);
    boolean existsByGroupIdAndUserId(String groupId, String userId);
}
