package com.campus.lostfound.service;

import com.campus.lostfound.common.ItemStatus;
import com.campus.lostfound.dto.ItemRequest;
import com.campus.lostfound.entity.Category;
import com.campus.lostfound.entity.Item;
import com.campus.lostfound.entity.ItemImage;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.config.AppProperties;
import com.campus.lostfound.repository.CategoryRepository;
import com.campus.lostfound.repository.ItemImageRepository;
import com.campus.lostfound.repository.ItemRepository;
import com.campus.lostfound.repository.UserRepository;
import com.campus.lostfound.security.UserContext;
import com.campus.lostfound.support.AfterCommitRunner;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AfterCommitRunner afterCommitRunner;
    private final MatchAsyncService matchAsyncService;
    private final AppProperties appProperties;

    public Page<Item> listPublished(Integer type, String keyword, Long categoryId, int page, int size) {
        String kw = keyword == null ? null : keyword.trim();
        Page<Item> pageResult =
                itemRepository.findPublished(
                        ItemStatus.PUBLISHED, type, categoryId, kw, PageRequest.of(page, size));
        attachCoverUrls(pageResult.getContent());
        return pageResult;
    }

    private void attachCoverUrls(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<Long> ids = items.stream().map(Item::getId).toList();
        List<ItemImage> imgs = itemImageRepository.findByItemIdInOrderByItemIdAscSortOrderAsc(ids);
        Map<Long, String> firstByItem = new HashMap<>();
        for (ItemImage im : imgs) {
            firstByItem.putIfAbsent(im.getItemId(), im.getImageUrl());
        }
        for (Item it : items) {
            String url = firstByItem.get(it.getId());
            if (url != null && !url.isBlank()) {
                it.setCoverUrl(url.trim());
            }
        }
    }

    public Item detail(long id) {
        Item it = itemRepository.findById(id).orElseThrow(() -> new BusinessException("物品不存在"));
        if (it.getIsDeleted() != null && it.getIsDeleted() == 1) {
            throw new BusinessException("物品不存在");
        }
        it.setViewCount((it.getViewCount() == null ? 0 : it.getViewCount()) + 1);
        itemRepository.save(it);
        return it;
    }

    public Map<String, Object> detailWithImages(long id) {
        Item it = detail(id);
        List<ItemImage> imgs = itemImageRepository.findByItemIdOrderBySortOrderAsc(id);
        String categoryName = categoryRepository.findById(it.getCategoryId()).map(Category::getName).orElse("");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item", it);
        m.put("images", imgs);
        m.put("categoryName", categoryName);
        String ownerNickname = userRepository.findById(it.getUserId()).map(u -> u.getNickname()).orElse("");
        m.put("ownerNickname", ownerNickname);
        return m;
    }

    public Page<Item> myItems(Integer type, Integer status, int page, int size) {
        Long uid = UserContext.getUserId();
        Page<Item> pageResult =
                itemRepository.findByUserFiltered(uid, type, status, PageRequest.of(page, size));
        attachCoverUrls(pageResult.getContent());
        return pageResult;
    }

    /** 当前用户待审核数量：寻物、招领 */
    public Map<String, Long> minePendingCounts() {
        Long uid = UserContext.getUserId();
        Map<String, Long> m = new TreeMap<>();
        m.put(
                "lost",
                itemRepository.countByUserIdAndTypeAndStatusAndIsDeleted(
                        uid, 0, ItemStatus.PENDING, 0));
        m.put(
                "found",
                itemRepository.countByUserIdAndTypeAndStatusAndIsDeleted(
                        uid, 1, ItemStatus.PENDING, 0));
        return m;
    }

    @Transactional
    public Item create(ItemRequest req) {
        Long uid = UserContext.getUserId();
        Item it = Item.builder()
                .userId(uid)
                .type(req.getType())
                .categoryId(req.getCategoryId())
                .title(req.getTitle().trim())
                .description(req.getDescription().trim())
                .location(req.getLocation().trim())
                .happenedAt(req.getHappenedAt())
                .status(
                        appProperties.getItems().isRequireAudit()
                                ? ItemStatus.PENDING
                                : ItemStatus.PUBLISHED)
                .contactName(req.getContactName().trim())
                .contactPhone(req.getContactPhone().trim())
                .build();
        it = itemRepository.save(it);
        saveImages(it.getId(), req.getImageUrls());
        afterCommitRunner.runAfterCommit(matchAsyncService::recomputeAllLater);
        return it;
    }

    @Transactional
    public Item update(long id, ItemRequest req) {
        Long uid = UserContext.getUserId();
        Item it = itemRepository.findById(id).orElseThrow(() -> new BusinessException("物品不存在"));
        if (!it.getUserId().equals(uid)) throw new BusinessException("无权限");
        it.setType(req.getType());
        it.setCategoryId(req.getCategoryId());
        it.setTitle(req.getTitle().trim());
        it.setDescription(req.getDescription().trim());
        it.setLocation(req.getLocation().trim());
        it.setHappenedAt(req.getHappenedAt());
        it.setContactName(req.getContactName().trim());
        it.setContactPhone(req.getContactPhone().trim());
        it.setStatus(
                appProperties.getItems().isRequireAudit()
                        ? ItemStatus.PENDING
                        : ItemStatus.PUBLISHED);
        it.setRejectReason(null);
        it = itemRepository.save(it);
        itemImageRepository.deleteByItemId(id);
        saveImages(id, req.getImageUrls());
        afterCommitRunner.runAfterCommit(matchAsyncService::recomputeAllLater);
        return it;
    }

    @Transactional
    public void offline(long id) {
        Long uid = UserContext.getUserId();
        Item it = itemRepository.findById(id).orElseThrow(() -> new BusinessException("物品不存在"));
        if (!it.getUserId().equals(uid)) throw new BusinessException("无权限");
        it.setStatus(ItemStatus.OFFLINE);
        itemRepository.save(it);
        afterCommitRunner.runAfterCommit(matchAsyncService::recomputeAllLater);
    }

    private void saveImages(long itemId, List<String> urls) {
        if (urls == null) return;
        int order = 0;
        for (String u : urls) {
            if (u == null || u.isBlank()) continue;
            if (order >= 6) break;
            itemImageRepository.save(ItemImage.builder().itemId(itemId).imageUrl(u).sortOrder(order++).build());
        }
    }

    public List<Map<String, Object>> batchImages(List<Long> itemIds) {
        if (itemIds.isEmpty()) return List.of();
        return itemIds.stream().distinct().map(id -> {
            List<ItemImage> imgs = itemImageRepository.findByItemIdOrderBySortOrderAsc(id);
            String first = imgs.isEmpty() ? null : imgs.get(0).getImageUrl();
            return Map.<String, Object>of("itemId", id, "thumb", first);
        }).collect(Collectors.toList());
    }
}
