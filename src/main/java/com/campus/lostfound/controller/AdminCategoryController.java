package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.entity.Category;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.CategoryRepository;
import com.campus.lostfound.repository.ItemRepository;
import com.campus.lostfound.service.AdminGuard;
import com.campus.lostfound.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final AdminGuard adminGuard;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResult<List<Category>> list() {
        adminGuard.requireAdmin();
        List<Category> list = categoryRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Category::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Category::getId))
                .toList();
        return ApiResult.ok(list);
    }

    @PostMapping
    public ApiResult<Category> save(@RequestBody SaveBody body, HttpServletRequest request) {
        adminGuard.requireAdmin();
        String name = body == null || body.getName() == null ? "" : body.getName().trim();
        if (name.isEmpty()) throw new BusinessException("分类名称不能为空");
        Category c;
        boolean created = body.getId() == null;
        if (created) {
            c = Category.builder().build();
        } else {
            c = categoryRepository.findById(body.getId()).orElseThrow(() -> new BusinessException("分类不存在"));
        }
        c.setName(name);
        c.setSortOrder(body.getSortOrder() == null ? 0 : body.getSortOrder());
        Category saved = categoryRepository.save(c);
        operationLogService.log("CATEGORY_" + (created ? "CREATE" : "UPDATE"),
                (created ? "新增分类：" : "更新分类：") + saved.getName() + " (id=" + saved.getId() + ")", request);
        return ApiResult.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        adminGuard.requireAdmin();
        Category c = categoryRepository.findById(id).orElseThrow(() -> new BusinessException("分类不存在"));
        long cnt = itemRepository.countByCategoryIdAndIsDeleted(id, 0);
        if (cnt > 0) throw new BusinessException("该分类下仍有物品，无法删除");
        categoryRepository.deleteById(id);
        operationLogService.log("CATEGORY_DELETE", "删除分类：" + c.getName() + " (id=" + id + ")", request);
        return ApiResult.ok();
    }

    @Data
    public static class SaveBody {
        private Long id;
        private String name;
        private Integer sortOrder;
    }
}
