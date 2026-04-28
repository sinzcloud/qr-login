CREATE DATABASE IF NOT EXISTS qr_login DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE qr_login;

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(100) NOT NULL,
  nickname VARCHAR(64) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

DROP TABLE IF EXISTS qr_login_record;
CREATE TABLE qr_login_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ticket VARCHAR(64) NOT NULL,
  user_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0等待扫码 1已扫码 2已确认 3已过期 4已取消',
  pc_ip VARCHAR(64) DEFAULT NULL,
  scan_ip VARCHAR(64) DEFAULT NULL,
  expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ticket (ticket),
  KEY idx_user_id (user_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- password: 123456, BCrypt
INSERT INTO sys_user(id, username, password, nickname, status, create_time, update_time)
VALUES (1, 'admin', '$2a$10$BBVrJ/GhS5SoQ6asCjluV.WDK/Xyb1C6q7cbKIzl7mKywe.Nwbr2O', '管理员', 1, NOW(), NOW());
