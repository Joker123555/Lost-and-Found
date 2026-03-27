package com.campus.lostfound.service;

import com.campus.lostfound.common.ItemStatus;
import com.campus.lostfound.entity.ChatSession;
import com.campus.lostfound.entity.Claim;
import com.campus.lostfound.entity.Item;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.ChatSessionRepository;
import com.campus.lostfound.repository.ClaimRepository;
import com.campus.lostfound.repository.ItemRepository;
import com.campus.lostfound.repository.UserRepository;
import com.campus.lostfound.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;

    @Transactional
    public Claim claim(long itemId, String message) {
        Long uid = UserContext.getUserId();
        Item it = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException("物品不存在"));
        if (it.getIsDeleted() != null && it.getIsDeleted() == 1) throw new BusinessException("物品不存在");
        if (it.getType() == null || it.getType() != 1) {
            throw new BusinessException("仅可对失物招领信息发起认领");
        }
        if (it.getStatus() == null || it.getStatus() != ItemStatus.PUBLISHED) {
            throw new BusinessException("抱歉，该物品已被认领或不可认领");
        }
        if (it.getUserId().equals(uid)) throw new BusinessException("不能认领自己的发布");
        if (claimRepository.findActiveByItemAndClaimantAndStatus(itemId, uid, 0).isPresent()) {
            throw new BusinessException("您已认领过该物品，请等待对方回复");
        }
        String msg = message == null ? "" : message.trim();
        if (msg.length() > 200) throw new BusinessException("认领说明最多200字");
        Claim c = Claim.builder()
                .itemId(itemId)
                .claimantId(uid)
                .message(msg)
                .status(0)
                .build();
        return claimRepository.save(c);
    }

    @Transactional
    public void agree(long claimId) {
        Long uid = UserContext.getUserId();
        Claim c = claimRepository.findById(claimId).orElseThrow(() -> new BusinessException("记录不存在"));
        Item it = itemRepository.findById(c.getItemId()).orElseThrow();
        if (!it.getUserId().equals(uid)) throw new BusinessException("无权限");
        if (c.getStatus() == null || c.getStatus() != 0) throw new BusinessException("该申请状态不可操作");
        if (it.getStatus() == null || it.getStatus() != ItemStatus.PUBLISHED) {
            throw new BusinessException("物品状态已变化，无法同意");
        }
        long a = Math.min(uid, c.getClaimantId());
        long b = Math.max(uid, c.getClaimantId());
        ChatSession s = chatSessionRepository.findByParticipantAAndParticipantB(a, b)
                .orElseGet(() -> chatSessionRepository.save(ChatSession.builder()
                        .participantA(a)
                        .participantB(b)
                        .createdAt(LocalDateTime.now())
                        .build()));
        c.setStatus(1);
        c.setProcessedBy(uid);
        c.setProcessedAt(LocalDateTime.now());
        c.setChatSessionId(s.getId());
        c.setRejectReason(null);
        claimRepository.save(c);
        it.setStatus(ItemStatus.CLAIMED);
        itemRepository.save(it);
    }

    @Transactional
    public void reject(long claimId, String reason) {
        Long uid = UserContext.getUserId();
        Claim c = claimRepository.findById(claimId).orElseThrow(() -> new BusinessException("记录不存在"));
        Item it = itemRepository.findById(c.getItemId()).orElseThrow();
        if (!it.getUserId().equals(uid)) throw new BusinessException("无权限");
        if (c.getStatus() == null || c.getStatus() != 0) throw new BusinessException("该申请状态不可操作");
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw new BusinessException("请填写拒绝原因");
        if (r.length() > 200) throw new BusinessException("拒绝原因最多200字");
        c.setStatus(2);
        c.setRejectReason(r);
        c.setProcessedBy(uid);
        c.setProcessedAt(LocalDateTime.now());
        claimRepository.save(c);
    }

    public Page<Claim> myClaims(int page, int size) {
        return claimRepository.findByClaimantIdAndIsDeleted(UserContext.getUserId(), 0, PageRequest.of(page, size));
    }

    public List<Map<String, Object>> myClaimRows(int page, int size) {
        Long uid = UserContext.getUserId();
        return claimRepository.findByClaimantIdAndIsDeleted(uid, 0, PageRequest.of(page, size))
                .getContent().stream().map(this::toRowForClaimant).toList();
    }

    public List<Map<String, Object>> myReceivedRows(int page, int size) {
        Long uid = UserContext.getUserId();
        Page<Claim> p = claimRepository.findByItemOwner(uid, PageRequest.of(page, size));
        return p.getContent().stream()
                .map(this::toRowForOwner)
                .toList();
    }

    public Map<String, Object> myClaimStatusOnItem(long itemId) {
        Long uid = UserContext.getUserId();
        List<Claim> history = claimRepository.findHistoryByItemAndClaimant(itemId, uid);
        if (history.isEmpty()) return Map.of("hasClaim", false);
        Claim latest = history.get(0);
        return Map.of(
                "hasClaim", true,
                "status", latest.getStatus(),
                "claimId", latest.getId()
        );
    }

    private Map<String, Object> toRowForClaimant(Claim c) {
        Item it = itemRepository.findById(c.getItemId()).orElse(null);
        if (it == null) return Map.of("id", c.getId(), "status", c.getStatus());
        User owner = userRepository.findById(it.getUserId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("itemId", it.getId());
        m.put("itemTitle", it.getTitle());
        m.put("itemType", it.getType());
        m.put("status", c.getStatus());
        m.put("message", c.getMessage());
        m.put("rejectReason", c.getRejectReason());
        m.put("createdAt", c.getCreatedAt());
        m.put("otherUserId", owner != null ? owner.getId() : null);
        m.put("otherNickname", owner != null ? owner.getNickname() : "");
        m.put("chatSessionId", c.getChatSessionId());
        return m;
    }

    private Map<String, Object> toRowForOwner(Claim c) {
        Item it = itemRepository.findById(c.getItemId()).orElse(null);
        if (it == null) return Map.of("id", c.getId(), "status", c.getStatus());
        User claimant = userRepository.findById(c.getClaimantId()).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("itemId", it.getId());
        m.put("itemTitle", it.getTitle());
        m.put("itemType", it.getType());
        m.put("status", c.getStatus());
        m.put("message", c.getMessage());
        m.put("rejectReason", c.getRejectReason());
        m.put("createdAt", c.getCreatedAt());
        m.put("claimantId", claimant != null ? claimant.getId() : c.getClaimantId());
        m.put("claimantNickname", claimant != null ? claimant.getNickname() : "");
        m.put("chatSessionId", c.getChatSessionId());
        return m;
    }
}
