package com.echo.chatservice.service;

import com.echo.chatservice.dto.GroupEvent;
import com.echo.chatservice.dto.GroupRequest;
import com.echo.chatservice.dto.GroupResponse;
import com.echo.chatservice.entity.Group;
import com.echo.chatservice.entity.GroupMember;
import com.echo.chatservice.exception.CustomException;
import com.echo.chatservice.kafka.producer.ChatKafkaProducer;
import com.echo.chatservice.repository.GroupMemberRepository;
import com.echo.chatservice.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChatKafkaProducer kafkaProducer;

    @Transactional
    public GroupResponse createGroup(GroupRequest request, String creatorId) {
        Group group = Group.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(creatorId)
                .build();

        group = groupRepository.save(group);

        // Add creator as OWNER of the group
        GroupMember member = GroupMember.builder()
                .groupId(group.getId())
                .userId(creatorId)
                .role("OWNER")
                .build();
        groupMemberRepository.save(member);

        // Publish events
        String groupCreateEventId = UUID.randomUUID().toString();
        kafkaProducer.sendGroupEvent(GroupEvent.builder()
                .eventId(groupCreateEventId)
                .eventType("GROUP_CREATE")
                .groupId(group.getId())
                .groupName(group.getName())
                .userId(creatorId)
                .role("OWNER")
                .timestamp(LocalDateTime.now())
                .build());

        kafkaProducer.sendGroupEvent(GroupEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("GROUP_JOIN")
                .groupId(group.getId())
                .groupName(group.getName())
                .userId(creatorId)
                .role("OWNER")
                .timestamp(LocalDateTime.now())
                .build());

        return mapToGroupResponse(group);
    }

    @Transactional
    public void addMember(String groupId, String userId, String role, String currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException("Group not found", HttpStatus.NOT_FOUND));

        // Check if current user is owner or admin to add new members
        GroupMember caller = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                .orElseThrow(() -> new CustomException("You are not a member of this group", HttpStatus.FORBIDDEN));

        if (!"OWNER".equals(caller.getRole()) && !"ADMIN".equals(caller.getRole())) {
            throw new CustomException("Only group Owner or Admin can add members", HttpStatus.FORBIDDEN);
        }

        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new CustomException("User is already a member", HttpStatus.BAD_REQUEST);
        }

        GroupMember newMember = GroupMember.builder()
                .groupId(groupId)
                .userId(userId)
                .role(role != null ? role : "MEMBER")
                .build();
        groupMemberRepository.save(newMember);

        // Publish event
        kafkaProducer.sendGroupEvent(GroupEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("GROUP_JOIN")
                .groupId(groupId)
                .groupName(group.getName())
                .userId(userId)
                .role(newMember.getRole())
                .timestamp(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void removeMember(String groupId, String userId, String currentUserId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new CustomException("Group not found", HttpStatus.NOT_FOUND));

        // Member can leave, or owner/admin can remove
        GroupMember target = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new CustomException("Member not found in group", HttpStatus.NOT_FOUND));

        if (!userId.equals(currentUserId)) {
            GroupMember caller = groupMemberRepository.findByGroupIdAndUserId(groupId, currentUserId)
                    .orElseThrow(() -> new CustomException("You are not a member of this group", HttpStatus.FORBIDDEN));

            if (!"OWNER".equals(caller.getRole()) && !"ADMIN".equals(caller.getRole())) {
                throw new CustomException("Only Owner or Admin can remove members", HttpStatus.FORBIDDEN);
            }
        }

        groupMemberRepository.delete(target);

        // Publish event
        kafkaProducer.sendGroupEvent(GroupEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("GROUP_LEAVE")
                .groupId(groupId)
                .groupName(group.getName())
                .userId(userId)
                .role(target.getRole())
                .timestamp(LocalDateTime.now())
                .build());
    }

    public List<GroupResponse> getMyGroups(String userId) {
        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
        List<String> groupIds = memberships.stream()
                .map(GroupMember::getGroupId)
                .collect(Collectors.toList());

        return groupRepository.findAllById(groupIds).stream()
                .map(this::mapToGroupResponse)
                .collect(Collectors.toList());
    }

    private GroupResponse mapToGroupResponse(Group group) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .build();
    }
}
