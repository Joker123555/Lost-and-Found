package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.entity.Claim;
import com.campus.lostfound.service.ClaimService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
public class ApiClaimController {

    private final ClaimService claimService;

    @PostMapping
    public ApiResult<Claim> create(@RequestBody ClaimBody body) {
        return ApiResult.ok(claimService.claim(body.getItemId(), body.getMessage()));
    }

    @PostMapping("/{id}/agree")
    public ApiResult<Void> agree(@PathVariable long id) {
        claimService.agree(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/reject")
    public ApiResult<Void> reject(@PathVariable long id, @RequestBody RejectBody body) {
        claimService.reject(id, body == null ? null : body.getReason());
        return ApiResult.ok();
    }

    @GetMapping("/mine")
    public ApiResult<Page<Claim>> mine(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(claimService.myClaims(page, size));
    }

    @GetMapping("/mine/rows")
    public ApiResult<List<Map<String, Object>>> mineRows(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(claimService.myClaimRows(page, size));
    }

    @GetMapping("/received/rows")
    public ApiResult<List<Map<String, Object>>> receivedRows(@RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "10") int size) {
        return ApiResult.ok(claimService.myReceivedRows(page, size));
    }

    @GetMapping("/item/{itemId}/my")
    public ApiResult<Map<String, Object>> myStatusOnItem(@PathVariable long itemId) {
        return ApiResult.ok(claimService.myClaimStatusOnItem(itemId));
    }

    @Data
    public static class ClaimBody {
        private long itemId;
        private String message;
    }

    @Data
    public static class RejectBody {
        private String reason;
    }
}
