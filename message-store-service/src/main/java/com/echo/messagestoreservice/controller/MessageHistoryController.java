package com.echo.messagestoreservice.controller;

import com.echo.messagestoreservice.entity.MessageDocument;
import com.echo.messagestoreservice.service.MessageHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageHistoryController {

    private final MessageHistoryService historyService;

    @GetMapping("/private")
    public ResponseEntity<Page<MessageDocument>> getPrivateHistory(
            @RequestParam String senderId,
            @RequestParam String receiverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(historyService.getPrivateHistory(senderId, receiverId, page, size));
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<Page<MessageDocument>> getGroupHistory(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(historyService.getGroupHistory(groupId, page, size));
    }
}
