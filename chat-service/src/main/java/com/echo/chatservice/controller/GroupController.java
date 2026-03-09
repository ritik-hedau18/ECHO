package com.echo.chatservice.controller;

import com.echo.chatservice.dto.AddMemberRequest;
import com.echo.chatservice.dto.GroupRequest;
import com.echo.chatservice.dto.GroupResponse;
import com.echo.chatservice.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@RequestBody GroupRequest request) {
        String currentUserId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.createGroup(request, currentUserId));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Void> addMember(@PathVariable String id, @RequestBody AddMemberRequest request) {
        String currentUserId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        groupService.addMember(id, request.getUserId(), request.getRole(), currentUserId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable String id, @PathVariable String userId) {
        String currentUserId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        groupService.removeMember(id, userId, currentUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/my")
    public ResponseEntity<List<GroupResponse>> getMyGroups() {
        String currentUserId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(groupService.getMyGroups(currentUserId));
    }
}
