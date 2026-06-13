package com.foundflow.matching.repository;

import com.foundflow.matching.domain.MatchEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchEmailLogRepository extends JpaRepository<MatchEmailLog, UUID> {

    List<MatchEmailLog> findByRecipientOrderBySentAtDesc(String recipient);

    List<MatchEmailLog> findByVenueIdOrderBySentAtDesc(UUID venueId);

    List<MatchEmailLog> findByVenueIdAndRecipientOrderBySentAtDesc(UUID venueId, String recipient);

    boolean existsByMatchId(UUID matchId);

    Optional<MatchEmailLog> findFirstByMatchIdOrderBySentAtDesc(UUID matchId);
}
