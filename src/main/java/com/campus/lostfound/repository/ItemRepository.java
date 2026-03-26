package com.campus.lostfound.repository;

import com.campus.lostfound.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    /** 已上架、未删除的我的物品数量（用于匹配 tab 前置提示） */
    long countByUserIdAndIsDeletedAndStatus(Long userId, int isDeleted, int status);

    long countByIsDeletedAndStatus(int isDeleted, int status);

    long countByUserIdAndTypeAndStatusAndIsDeleted(Long userId, Integer type, int status, int isDeleted);

    @Query("SELECT i FROM Item i WHERE i.isDeleted = 0 AND i.status = :status AND (:type IS NULL OR i.type = :type) AND (:categoryId IS NULL OR i.categoryId = :categoryId) AND (:keyword IS NULL OR :keyword = '' OR i.title LIKE CONCAT('%', :keyword, '%'))")
    Page<Item> findPublished(
            @Param("status") int status,
            @Param("type") Integer type,
            @Param("categoryId") Long categoryId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.isDeleted = 0 AND i.userId = :uid ORDER BY i.createdAt DESC")
    Page<Item> findByUserId(@Param("uid") Long userId, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.isDeleted = 0 AND i.userId = :uid AND (:type IS NULL OR i.type = :type) AND (:status IS NULL OR i.status = :status)")
    Page<Item> findByUserFiltered(@Param("uid") Long userId, @Param("type") Integer type, @Param("status") Integer status, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.isDeleted = 0 AND i.status = 0 AND (:type IS NULL OR i.type = :type) AND (:from IS NULL OR i.createdAt >= :from) AND (:to IS NULL OR i.createdAt <= :to)")
    Page<Item> findPending(@Param("type") Integer type, @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to, Pageable pageable);

    List<Item> findByIsDeletedAndStatusAndType(int isDeleted, int status, int type);

    @Query("SELECT i FROM Item i WHERE i.isDeleted = 0 AND (:type IS NULL OR i.type = :type) AND (:keyword IS NULL OR :keyword = '' OR i.title LIKE CONCAT('%', :keyword, '%')) AND (:contactName IS NULL OR :contactName = '' OR i.contactName LIKE CONCAT('%', :contactName, '%')) AND (:categoryId IS NULL OR i.categoryId = :categoryId) AND (:status IS NULL OR i.status = :status)")
    Page<Item> adminBrowse(@Param("type") Integer type, @Param("keyword") String keyword, @Param("contactName") String contactName, @Param("categoryId") Long categoryId, @Param("status") Integer status, Pageable pageable);

    @Query("SELECT i FROM Item i WHERE i.isDeleted = 0 AND i.status = 1 AND i.createdAt < :before")
    List<Item> findPublishedBefore(@Param("before") java.time.LocalDateTime before);
}
