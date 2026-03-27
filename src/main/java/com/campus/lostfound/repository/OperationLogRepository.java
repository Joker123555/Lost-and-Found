package com.campus.lostfound.repository;

import com.campus.lostfound.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    @Query("SELECT o FROM OperationLog o WHERE (:type IS NULL OR :type = '' OR o.type = :type) " +
            "AND (:keyword IS NULL OR :keyword = '' OR o.content LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY o.createdAt DESC")
    Page<OperationLog> search(
            @Param("type") String type,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
