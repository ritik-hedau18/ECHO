package com.echo.messagestoreservice.service;

import com.echo.messagestoreservice.entity.MessageDocument;
import com.echo.messagestoreservice.repository.MessageRepository;
import com.echo.messagestoreservice.security.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageHistoryService {

    private final MessageRepository messageRepository;
    private final EncryptionUtil encryptionUtil;

    public Page<MessageDocument> getPrivateHistory(String senderId, String receiverId, int page, int size) {
        log.info("Fetching private chat history between {} and {}", senderId, receiverId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<MessageDocument> encryptedDocs = messageRepository.findPrivateHistory(senderId, receiverId, pageable);
        
        // Decrypt on the fly
        return encryptedDocs.map(this::decryptDocument);
    }

    public Page<MessageDocument> getGroupHistory(String groupId, int page, int size) {
        log.info("Fetching group chat history for group {}", groupId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("timestamp").descending());
        Page<MessageDocument> encryptedDocs = messageRepository.findByTypeAndGroupId("GROUP", groupId, pageable);
        
        // Decrypt on the fly
        return encryptedDocs.map(this::decryptDocument);
    }

    private MessageDocument decryptDocument(MessageDocument doc) {
        try {
            String decrypted = encryptionUtil.decrypt(doc.getContent());
            doc.setContent(decrypted);
        } catch (Exception e) {
            log.error("Failed to decrypt message content for ID: {}", doc.getMessageId(), e);
            doc.setContent("[Decryption Failed]");
        }
        return doc;
    }
}
