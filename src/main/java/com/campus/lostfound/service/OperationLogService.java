package com.campus.lostfound.service;

import com.campus.lostfound.entity.OperationLog;
import com.campus.lostfound.repository.OperationLogRepository;
import com.campus.lostfound.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public void log(String type, String content, HttpServletRequest request) {
        Long adminId = UserContext.getUserId();
        if (adminId == null) return;
        operationLogRepository.save(OperationLog.builder()
                .adminId(adminId)
                .type(type == null ? "OTHER" : type)
                .content(content == null ? "" : content)
                .ipAddress(resolveIp(request))
                .build());
    }

    public Page<OperationLog> list(String type, String keyword, int page, int size) {
        return operationLogRepository.search(type, keyword, PageRequest.of(page, size));
    }

    private String resolveIp(HttpServletRequest req) {
        if (req == null) return "";
        String h = req.getHeader("X-Forwarded-For");
        if (h != null && !h.isBlank()) {
            int idx = h.indexOf(',');
            return idx >= 0 ? h.substring(0, idx).trim() : h.trim();
        }
        String rip = req.getRemoteAddr();
        return rip == null ? "" : rip;
    }
}
