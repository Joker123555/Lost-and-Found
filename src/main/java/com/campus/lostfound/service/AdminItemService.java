package com.campus.lostfound.service;

import com.campus.lostfound.common.ItemStatus;
import com.campus.lostfound.entity.Category;
import com.campus.lostfound.entity.Item;
import com.campus.lostfound.entity.ItemImage;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.CategoryRepository;
import com.campus.lostfound.repository.ItemImageRepository;
import com.campus.lostfound.repository.ItemRepository;
import com.campus.lostfound.support.AfterCommitRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final ItemImageRepository itemImageRepository;
    private final AfterCommitRunner afterCommitRunner;
    private final MatchAsyncService matchAsyncService;

    public Page<Item> pending(Integer type, LocalDateTime from, LocalDateTime to, int page, int size) {
        return itemRepository.findPending(type, from, to, PageRequest.of(page, size));
    }

    public Page<Map<String, Object>> browse(Integer type, String keyword, String contactName, Long categoryId, Integer status, int page, int size) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        String contact = contactName == null || contactName.isBlank() ? null : contactName.trim();
        Page<Item> p = itemRepository.adminBrowse(type, kw, contact, categoryId, status, PageRequest.of(page, size));
        Map<Long, String> cats = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
        return p.map(item -> toAdminRow(item, cats));
    }

    private Map<String, Object> toAdminRow(Item item, Map<Long, String> cats) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", item.getId());
        m.put("userId", item.getUserId());
        m.put("type", item.getType());
        m.put("title", item.getTitle());
        m.put("categoryId", item.getCategoryId());
        m.put("categoryName", cats.getOrDefault(item.getCategoryId(), ""));
        m.put("location", item.getLocation());
        m.put("happenedAt", item.getHappenedAt());
        m.put("contactName", item.getContactName());
        m.put("contactPhone", item.getContactPhone());
        m.put("status", item.getStatus());
        m.put("createdAt", item.getCreatedAt());
        String thumb = itemImageRepository.findByItemIdOrderBySortOrderAsc(item.getId()).stream()
                .findFirst()
                .map(ItemImage::getImageUrl)
                .orElse(null);
        m.put("thumbUrl", thumb);
        return m;
    }

    @Transactional
    public void softDelete(long itemId) {
        Item it = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException("物品不存在"));
        if (it.getIsDeleted() != null && it.getIsDeleted() == 1) {
            return;
        }
        it.setIsDeleted(1);
        itemRepository.save(it);
    }

    @Transactional
    public void approve(long itemId) {
        Item it = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException("物品不存在"));
        if (it.getStatus() != ItemStatus.PENDING) throw new BusinessException("当前状态不可审核");
        it.setStatus(ItemStatus.PUBLISHED);
        it.setRejectReason(null);
        itemRepository.save(it);
        afterCommitRunner.runAfterCommit(matchAsyncService::recomputeAllLater);
    }

    @Transactional
    public void reject(long itemId, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("请填写驳回原因");
        Item it = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException("物品不存在"));
        if (it.getStatus() != ItemStatus.PENDING) throw new BusinessException("当前状态不可审核");
        it.setStatus(ItemStatus.REJECTED);
        it.setRejectReason(reason);
        itemRepository.save(it);
    }
}
