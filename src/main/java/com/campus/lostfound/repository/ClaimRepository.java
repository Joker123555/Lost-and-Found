package com.campus.lostfound.repository;

import com.campus.lostfound.entity.Claim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    Page<Claim> findByClaimantIdAndIsDeleted(Long claimantId, int isDeleted, Pageable pageable);

    List<Claim> findByItemIdAndIsDeleted(Long itemId, int isDeleted);

    @Query("SELECT c FROM Claim c JOIN Item i ON c.itemId = i.id WHERE c.isDeleted = 0 AND i.isDeleted = 0 AND i.userId = :ownerId ORDER BY c.createdAt DESC")
    Page<Claim> findByItemOwner(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT c FROM Claim c WHERE c.itemId = :itemId AND c.claimantId = :claimantId AND c.isDeleted = 0 AND c.status = :status")
    Optional<Claim> findActiveByItemAndClaimantAndStatus(
            @Param("itemId") Long itemId,
            @Param("claimantId") Long claimantId,
            @Param("status") Integer status);

    @Query("SELECT c FROM Claim c WHERE c.itemId = :itemId AND c.claimantId = :claimantId AND c.isDeleted = 0 ORDER BY c.createdAt DESC")
    List<Claim> findHistoryByItemAndClaimant(
            @Param("itemId") Long itemId,
            @Param("claimantId") Long claimantId);

    @Query("SELECT c FROM Claim c WHERE c.status = 0 AND c.createdAt < :before")
    List<Claim> findPendingClaimsOlderThan(@Param("before") java.time.LocalDateTime before);

    @Query("SELECT COUNT(c.id) FROM Claim c WHERE c.isDeleted = 0 AND c.status IN :statuses")
    long countSuccessByStatuses(@Param("statuses") List<Integer> statuses);
}
