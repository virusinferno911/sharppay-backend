package com.virusinferno.sharppay.repository;

import com.virusinferno.sharppay.model.User;
import com.virusinferno.sharppay.model.VirtualCard;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface VirtualCardRepository extends JpaRepository<VirtualCard, UUID> {
    Optional<VirtualCard> findByUser(User user);
}
