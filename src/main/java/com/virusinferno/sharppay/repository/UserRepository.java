package com.virusinferno.sharppay.repository;

import com.virusinferno.sharppay.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // This allows us to find a user by their email for login later
    Optional<User> findByEmail(String email);
}