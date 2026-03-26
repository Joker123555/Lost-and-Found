package com.campus.lostfound.service;

import com.campus.lostfound.common.ItemStatus;
import com.campus.lostfound.entity.Item;
import com.campus.lostfound.entity.ItemMatch;
import com.campus.lostfound.repository.ItemMatchRepository;
import com.campus.lostfound.repository.ItemRepository;
import com.campus.lostfound.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MatchComputeService {

    private final ItemRepository itemRepository;
    private final ItemMatchRepository itemMatchRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final MatchNotifyService matchNotifyService;

    private static final List<String> CAMPUS_TOKENS = List.of("东校区", "西校区", "南校区", "北校区", "新校区", "老校区");
    private static final List<String> COLORS = List.of("黑", "白", "蓝", "红", "绿", "黄", "紫", "灰", "银", "金", "粉", "棕");
    private static final List<String> BRANDS = List.of("苹果", "apple", "华为", "小米", "oppo", "vivo", "联想", "戴尔", "索尼", "耐克", "阿迪");
    private static final List<String> MARKERS = List.of("贴纸", "挂绳", "钥匙扣", "划痕", "刻字", "卡套", "铃铛", "logo");

    @Transactional
    public void recomputeAll() {
        itemMatchRepository.deleteAll();
        List<Item> lost = itemRepository.findByIsDeletedAndStatusAndType(0, ItemStatus.PUBLISHED, 0);
        List<Item> found = itemRepository.findByIsDeletedAndStatusAndType(0, ItemStatus.PUBLISHED, 1);
        double baseThreshold = thresholdBase();
        double highThreshold = thresholdHigh();
        int saved = 0;
        int high = 0;
        for (Item l : lost) {
            for (Item f : found) {
                if (l.getUserId().equals(f.getUserId())) continue;
                if (!hardFilter(l, f)) continue;
                double score = score(l, f);
                if (score >= baseThreshold) {
                    itemMatchRepository.save(
                            ItemMatch.builder()
                                    .lostItemId(l.getId())
                                    .foundItemId(f.getId())
                                    .score(
                                            BigDecimal.valueOf(score)
                                                    .setScale(2, RoundingMode.HALF_UP))
                                    .isNotified(0)
                                    .build());
                    saved++;
                    if (score >= highThreshold) high++;
                }
            }
        }
        matchNotifyService.onRecomputeFinished(saved);
    }

    /** 100 分评分：标题25 + 地点25 + 特征标签30 + 分类10 + 时间10 */
    private double score(Item lost, Item found) {
        double title = scoreTitle(lost, found);            // 25
        double location = scoreLocation(lost, found);      // 25
        double feature = scoreFeatureTags(lost, found);    // 30
        double category = scoreCategory(lost, found);      // 10
        double time = scoreTimeWindow(lost, found);        // 10
        return Math.min(100, title + location + feature + category + time);
    }

    /** 硬过滤：分类不一致或跨明显校区直接过滤 */
    private boolean hardFilter(Item lost, Item found) {
        if (!Objects.equals(lost.getCategoryId(), found.getCategoryId())) return false;
        String c1 = campusOf(lost.getLocation());
        String c2 = campusOf(found.getLocation());
        return c1.isEmpty() || c2.isEmpty() || c1.equals(c2);
    }

    private double scoreTitle(Item lost, Item found) {
        String t1 = normalizeText(lost.getTitle());
        String t2 = normalizeText(found.getTitle());
        if (t1.isBlank() || t2.isBlank()) return 0;
        double base = Math.max(jaccard(tokenize(t1), tokenize(t2)), containsRatio(t1, t2));
        double synonymBonus = synonymHitScore(t1, t2);
        return Math.min(25, base * 22 + synonymBonus);
    }

    private double scoreLocation(Item lost, Item found) {
        String a = normalizeText(lost.getLocation());
        String b = normalizeText(found.getLocation());
        if (a.isBlank() || b.isBlank()) return 0;
        double score = 0;
        String campusA = campusOf(a);
        String campusB = campusOf(b);
        if (!campusA.isEmpty() && campusA.equals(campusB)) score += 5;
        String buildingA = extractByRegex(a, "[A-Za-z]?\\d+号?楼|教学楼[A-Za-z]?座|宿舍楼\\d+栋");
        String buildingB = extractByRegex(b, "[A-Za-z]?\\d+号?楼|教学楼[A-Za-z]?座|宿舍楼\\d+栋");
        if (!buildingA.isEmpty() && buildingA.equals(buildingB)) score += 10;
        String floorA = extractByRegex(a, "\\d+楼");
        String floorB = extractByRegex(b, "\\d+楼");
        if (!floorA.isEmpty() && floorA.equals(floorB)) score += 5;
        String roomA = extractByRegex(a, "[A-Za-z]?\\d{3,4}|\\d+室");
        String roomB = extractByRegex(b, "[A-Za-z]?\\d{3,4}|\\d+室");
        if (!roomA.isEmpty() && roomA.equals(roomB)) score += 5;
        if (score < 12) score = Math.max(score, jaccard(tokenize(a), tokenize(b)) * 25);
        return Math.min(25, score);
    }

    private double scoreFeatureTags(Item lost, Item found) {
        Set<String> a = featureTags(lost);
        Set<String> b = featureTags(found);
        if (a.isEmpty() || b.isEmpty()) return 0;
        return jaccard(a, b) * 30;
    }

    private double scoreCategory(Item lost, Item found) {
        return Objects.equals(lost.getCategoryId(), found.getCategoryId()) ? 10 : 0;
    }

    private double scoreTimeWindow(Item lost, Item found) {
        if (lost.getHappenedAt() == null || found.getHappenedAt() == null) return 0;
        long days = Math.abs(ChronoUnit.DAYS.between(lost.getHappenedAt(), found.getHappenedAt()));
        if (days <= 7) return 10;
        if (days <= 14) return 6;
        if (days <= 30) return 2;
        return 0;
    }

    /**
     * 空格分词 + 拉丁词；含中日韩字符的片段使用二字元组（适合无空格中文），提升标题/描述匹配效果。
     */
    private Set<String> tokenize(String s) {
        if (s == null || s.isBlank()) return Set.of();
        String norm = normalizeText(s);
        if (norm.isBlank()) return Set.of();
        Set<String> tokens = new HashSet<>();
        for (String part : norm.split("\\s+")) {
            if (part.isBlank()) continue;
            if (part.codePoints().anyMatch(MatchComputeService::isCjkScript)) {
                addCjkBigrams(part, tokens);
            } else {
                tokens.add(part.toLowerCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    private String normalizeText(String s) {
        if (s == null) return "";
        String norm = s
                .replace('（', ' ')
                .replace('）', ' ')
                .replace('，', ' ')
                .replace('。', ' ')
                .replace('：', ' ')
                .replace('；', ' ')
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .trim();
        if (norm.isBlank()) return "";
        // 常见描述词去噪，减少“捡到/丢失/求助”等无效词对相似度的干扰
        return norm
                .replace("捡到", " ")
                .replace("丢失", " ")
                .replace("遗失", " ")
                .replace("寻找", " ")
                .replace("招领", " ")
                .replace("启事", " ")
                .replace("求助", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private double containsBoost(String a, String b, double boost) {
        String x = normalizeText(a);
        String y = normalizeText(b);
        if (x.isBlank() || y.isBlank()) return 0;
        return (x.contains(y) || y.contains(x)) ? boost : 0;
    }

    private double containsRatio(String a, String b) {
        if (a.isBlank() || b.isBlank()) return 0;
        return (a.contains(b) || b.contains(a)) ? 1.0 : 0.0;
    }

    private Set<String> featureTags(Item item) {
        String all = normalizeText((item.getTitle() == null ? "" : item.getTitle()) + " " + (item.getDescription() == null ? "" : item.getDescription()));
        if (all.isBlank()) return Set.of();
        Set<String> tags = new HashSet<>();
        pickTags(all, COLORS, "色:", tags);
        pickTags(all, BRANDS, "牌:", tags);
        pickTags(all, MARKERS, "征:", tags);
        return tags;
    }

    private void pickTags(String text, List<String> dict, String prefix, Set<String> out) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String w : dict) {
            if (lower.contains(w.toLowerCase(Locale.ROOT))) out.add(prefix + w.toLowerCase(Locale.ROOT));
        }
    }

    private double synonymHitScore(String a, String b) {
        List<Set<String>> groups = List.of(
                Set.of("耳机", "airpods", "蓝牙耳机"),
                Set.of("钥匙", "钥匙串", "门禁"),
                Set.of("学生证", "一卡通", "校园卡"),
                Set.of("身份证", "证件"),
                Set.of("雨伞", "折叠伞"),
                Set.of("书", "教材", "资料")
        );
        double score = 0;
        String x = a.toLowerCase(Locale.ROOT);
        String y = b.toLowerCase(Locale.ROOT);
        for (Set<String> g : groups) {
            boolean hx = g.stream().anyMatch(x::contains);
            boolean hy = g.stream().anyMatch(y::contains);
            if (hx && hy) score += 1.2;
        }
        return Math.min(3, score);
    }

    private String campusOf(String location) {
        String s = location == null ? "" : location;
        for (String c : CAMPUS_TOKENS) {
            if (s.contains(c)) return c;
        }
        return "";
    }

    private String extractByRegex(String s, String regex) {
        if (s == null || s.isBlank()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(s);
        return m.find() ? m.group() : "";
    }

    private double thresholdBase() {
        String v =
                systemConfigRepository
                        .findById("match.threshold.base")
                        .map(c -> c.getConfigValue())
                        .orElseGet(
                                () ->
                                        systemConfigRepository
                                                .findById("match.score.threshold")
                                                .map(c -> c.getConfigValue())
                                                .orElse("60"));
        return new BigDecimal(v).doubleValue();
    }

    private double thresholdHigh() {
        String v =
                systemConfigRepository
                        .findById("match.threshold.high")
                        .map(c -> c.getConfigValue())
                        .orElse("80");
        return new BigDecimal(v).doubleValue();
    }

    private static boolean isCjkScript(int cp) {
        Character.UnicodeScript sc = Character.UnicodeScript.of(cp);
        return sc == Character.UnicodeScript.HAN
                || sc == Character.UnicodeScript.HIRAGANA
                || sc == Character.UnicodeScript.KATAKANA
                || sc == Character.UnicodeScript.HANGUL;
    }

    private void addCjkBigrams(String s, Set<String> out) {
        int n = s.length();
        if (n <= 2) {
            out.add(s);
            return;
        }
        for (int i = 0; i < n - 1; i++) {
            out.add(s.substring(i, i + 2));
        }
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }
}
