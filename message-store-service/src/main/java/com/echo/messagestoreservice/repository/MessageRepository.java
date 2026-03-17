package com.echo.messagestoreservice.repository;

import com.echo.messagestoreservice.entity.MessageDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends MongoRepository<MessageDocument, String> {

    @Query("{ 'type': 'PRIVATE', '$or': [ { 'senderId': ?0, 'receiverId': ?1 }, { 'senderId': ?1, 'receiverId': ?0 } ] }")
    Page<MessageDocument> findPrivateHistory(String user1, String user2, Pageable pageable);

    Page<MessageDocument> findByTypeAndGroupId(String type, String groupId, Pageable pageable);
}
