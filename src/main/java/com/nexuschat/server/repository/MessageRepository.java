package com.nexuschat.server.repository;

import com.nexuschat.server.model.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    // Get latest messages in a channel (for loading chat history)
    List<Message> findByChannelIdOrderByCreatedAtDesc(
            String channelId, Pageable pageable
    );

    // Get messages before a certain message (for scroll-up pagination)
    List<Message> findByChannelIdAndIdLessThanOrderByCreatedAtDesc(
            String channelId, String beforeId, Pageable pageable
    );
}