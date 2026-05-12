package com.foundflow.matching.repository;

import com.foundflow.matching.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
}