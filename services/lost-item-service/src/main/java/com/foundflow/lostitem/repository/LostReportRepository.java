package com.foundflow.lostitem.repository;

import com.foundflow.lostitem.domain.LostReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LostReportRepository extends JpaRepository<LostReport, UUID> {
}