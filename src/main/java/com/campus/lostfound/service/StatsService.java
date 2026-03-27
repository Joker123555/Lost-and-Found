package com.campus.lostfound.service;

import com.campus.lostfound.common.ItemStatus;
import com.campus.lostfound.repository.CategoryRepository;
import com.campus.lostfound.repository.ClaimRepository;
import com.campus.lostfound.repository.ItemRepository;
import com.campus.lostfound.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final ClaimRepository claimRepository;
    private final CategoryRepository categoryRepository;

    public Map<String, Object> overview() {
        LocalDate today = LocalDate.now();
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userCount", userRepository.count());
        m.put("itemCount", itemRepository.countByIsDeleted(0));
        m.put("itemPublished", itemRepository.countByIsDeletedAndStatus(0, ItemStatus.PUBLISHED));
        m.put("itemPending", itemRepository.countByIsDeletedAndStatus(0, ItemStatus.PENDING));
        m.put("claimSuccessCount", claimRepository.countSuccessByStatuses(List.of(1, 3)));
        m.put("todayNewUsers", userRepository.countByCreatedAtBetweenAndIsDeleted(from, to, 0));
        m.put("todayNewItems", itemRepository.countByCreatedAtBetweenAndIsDeleted(from, to, 0));
        return m;
    }

    public Map<String, Object> trend(int days) {
        int dayCount = (days == 30) ? 30 : 7;
        List<String> labels = new ArrayList<>();
        List<Long> values = new ArrayList<>();
        LocalDate today = LocalDate.now();
        Map<String, Long> map = new HashMap<>();
        List<Object[]> raw = itemRepository.trendSince(today.minusDays(dayCount - 1).atStartOfDay());
        for (Object[] row : raw) {
            String key = String.valueOf(row[0]);
            long v = row[1] == null ? 0L : ((Number) row[1]).longValue();
            map.put(key, v);
        }
        for (int i = dayCount - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String key = date.toString();
            labels.add(key);
            values.add(map.getOrDefault(key, 0L));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", labels);
        out.put("values", values);
        out.put("days", dayCount);
        return out;
    }

    public List<Map<String, Object>> categoryRatio() {
        Map<Long, String> nameMap = new HashMap<>();
        categoryRepository.findAll().forEach(c -> nameMap.put(c.getId(), c.getName()));
        List<Object[]> raw = itemRepository.countByCategoryGroup();
        long total = 0L;
        for (Object[] row : raw) {
            total += row[1] == null ? 0L : ((Number) row[1]).longValue();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] row : raw) {
            Long cid = row[0] == null ? null : ((Number) row[0]).longValue();
            long count = row[1] == null ? 0L : ((Number) row[1]).longValue();
            double percent = total <= 0 ? 0D : (count * 100.0 / total);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("categoryId", cid);
            r.put("categoryName", cid == null ? "未分类" : nameMap.getOrDefault(cid, "未分类"));
            r.put("count", count);
            r.put("percent", Math.round(percent * 10.0) / 10.0);
            rows.add(r);
        }
        rows.sort((a, b) -> Long.compare(
                ((Number) b.get("count")).longValue(),
                ((Number) a.get("count")).longValue()
        ));
        return rows;
    }
}
