USE campus_lost_found;

-- =========================================================
-- 1) claims 表增强：支持拒绝原因、处理人、处理时间、完成时间、聊天会话关联
-- =========================================================
ALTER TABLE claims
  ADD COLUMN IF NOT EXISTS reject_reason VARCHAR(512) NULL COMMENT '拒绝原因',
  ADD COLUMN IF NOT EXISTS processed_by BIGINT NULL COMMENT '处理人(拾物者)',
  ADD COLUMN IF NOT EXISTS processed_at DATETIME NULL COMMENT '同意/拒绝处理时间',
  ADD COLUMN IF NOT EXISTS completed_at DATETIME NULL COMMENT '双方确认完成时间',
  ADD COLUMN IF NOT EXISTS chat_session_id BIGINT NULL COMMENT '同意后自动创建的会话ID';

ALTER TABLE claims
  ADD INDEX IF NOT EXISTS idx_claims_item_status (item_id, status),
  ADD INDEX IF NOT EXISTS idx_claims_claimant_status (claimant_id, status),
  ADD INDEX IF NOT EXISTS idx_claims_processed_by_status (processed_by, status);

-- status 约定：
-- 0=待确认 1=已同意 2=已拒绝 3=已完成

-- =========================================================
-- 2) 防重复申请（AC1）：同一 item + claimant 在“待确认/已同意”阶段只能有一条
--    注：MySQL 不支持部分唯一索引，使用触发器实现
-- =========================================================
DROP TRIGGER IF EXISTS trg_claim_before_insert_no_duplicate_active;
DELIMITER $$
CREATE TRIGGER trg_claim_before_insert_no_duplicate_active
BEFORE INSERT ON claims
FOR EACH ROW
BEGIN
  DECLARE v_count INT DEFAULT 0;
  DECLARE v_item_status INT DEFAULT NULL;

  -- 仅允许对“已发布”的物品提交认领
  SELECT i.status INTO v_item_status
  FROM items i
  WHERE i.id = NEW.item_id AND i.is_deleted = 0
  LIMIT 1;

  IF v_item_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '物品不存在或已删除';
  END IF;

  IF v_item_status IN (2, 3) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '该物品已归还或已下架，无法认领';
  END IF;

  SELECT COUNT(1) INTO v_count
  FROM claims c
  WHERE c.item_id = NEW.item_id
    AND c.claimant_id = NEW.claimant_id
    AND c.is_deleted = 0
    AND c.status IN (0, 1);

  IF v_count > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '您已认领过该物品，请等待对方回复';
  END IF;
END$$
DELIMITER ;

-- =========================================================
-- 3) 审批结果同步物品状态（AC1）
--    同意(claims.status=1) => items.status=2(已认领)
-- =========================================================
DROP TRIGGER IF EXISTS trg_claim_after_update_sync_item_status;
DELIMITER $$
CREATE TRIGGER trg_claim_after_update_sync_item_status
AFTER UPDATE ON claims
FOR EACH ROW
BEGIN
  -- 同意归还：将物品置为已认领，其他用户不可再认领
  IF NEW.status = 1 AND OLD.status <> 1 THEN
    UPDATE items
    SET status = 2, updated_at = NOW()
    WHERE id = NEW.item_id AND is_deleted = 0;
  END IF;

  -- 已完成：保持 items.status=2（业务可扩展为独立完成状态）
  IF NEW.status = 3 AND OLD.status <> 3 THEN
    UPDATE items
    SET status = 2, updated_at = NOW()
    WHERE id = NEW.item_id AND is_deleted = 0;
  END IF;
END$$
DELIMITER ;

-- =========================================================
-- 4) 管理员操作日志表（2.2.2）
-- =========================================================
CREATE TABLE IF NOT EXISTS operation_logs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  admin_id BIGINT NOT NULL COMMENT '管理员ID(users.id)',
  type VARCHAR(32) NOT NULL COMMENT '操作类型',
  content TEXT NOT NULL COMMENT '操作详情',
  ip_address VARCHAR(64) NULL COMMENT '操作IP',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_oplog_admin_time (admin_id, created_at),
  INDEX idx_oplog_type_time (type, created_at),
  INDEX idx_oplog_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作日志';

-- =========================================================
-- 5) 系统参数（P2）
-- =========================================================
INSERT INTO system_config(config_key, config_value, remark)
VALUES
  ('match.threshold.base', '60', '匹配基础阈值'),
  ('match.threshold.high', '80', '匹配高置信阈值'),
  ('item.auto.offline.days', '30', '自动下架天数'),
  ('claim.timeout.days', '7', '认领待确认超时天数')
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  remark = VALUES(remark);

-- =========================================================
-- 6) 数据统计常用视图（管理员端）
-- =========================================================
CREATE OR REPLACE VIEW v_stats_overview AS
SELECT
  (SELECT COUNT(1) FROM users WHERE is_deleted = 0) AS total_users,
  (SELECT COUNT(1) FROM items WHERE is_deleted = 0) AS total_items,
  (SELECT COUNT(1) FROM claims WHERE is_deleted = 0 AND status IN (1,3)) AS total_claim_success,
  (SELECT COUNT(1) FROM users WHERE is_deleted = 0 AND DATE(created_at) = CURDATE()) AS today_new_users,
  (SELECT COUNT(1) FROM items WHERE is_deleted = 0 AND DATE(created_at) = CURDATE()) AS today_new_items;

CREATE OR REPLACE VIEW v_stats_category_distribution AS
SELECT
  c.id AS category_id,
  c.name AS category_name,
  COUNT(i.id) AS item_count
FROM categories c
LEFT JOIN items i ON i.category_id = c.id AND i.is_deleted = 0
GROUP BY c.id, c.name;

-- 近30天发布量（前端可按 7/30 天截取）
CREATE OR REPLACE VIEW v_stats_publish_trend_30d AS
SELECT
  DATE(i.created_at) AS dt,
  COUNT(1) AS publish_count
FROM items i
WHERE i.is_deleted = 0
  AND i.created_at >= DATE_SUB(CURDATE(), INTERVAL 29 DAY)
GROUP BY DATE(i.created_at)
ORDER BY dt;

