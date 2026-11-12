-- CampusQuest 建库建表脚本（MySQL 8.0）
-- 执行：mysql -u root -p < E:\MyApp\server\sql\01_schema.sql

CREATE DATABASE IF NOT EXISTS campus_quest
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE campus_quest;

DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS appeals;
DROP TABLE IF EXISTS credit_logs;
DROP TABLE IF EXISTS point_logs;
DROP TABLE IF EXISTS reviews;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS conversations;
DROP TABLE IF EXISTS applications;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS admins;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  phone CHAR(11) NOT NULL,
  nickname VARCHAR(32) NOT NULL,
  password_hash CHAR(64) NOT NULL,
  points INT NOT NULL DEFAULT 100,
  credit INT NOT NULL DEFAULT 100,
  avatar_path VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admins (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(32) NOT NULL,
  password_hash CHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tasks (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_no VARCHAR(20) NOT NULL,
  category VARCHAR(16) NOT NULL,
  title VARCHAR(60) NOT NULL,
  description TEXT NOT NULL,
  points INT NOT NULL,
  location_desc VARCHAR(255) NOT NULL,
  time_desc VARCHAR(255) NOT NULL,
  publisher_id INT UNSIGNED NOT NULL,
  acceptor_id INT UNSIGNED NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  accepted_at DATETIME NULL,
  delivered_at DATETIME NULL,
  confirm_deadline DATETIME NULL,
  finished_at DATETIME NULL,
  offline TINYINT NOT NULL DEFAULT 0,
  offline_reason VARCHAR(255) NULL,
  offline_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_no (task_no),
  KEY idx_status_created (status, created_at),
  KEY idx_publisher (publisher_id, status),
  KEY idx_acceptor (acceptor_id, status),
  KEY idx_category (category),
  KEY idx_deadline (status, confirm_deadline),
  KEY idx_offline (offline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE applications (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id INT UNSIGNED NOT NULL,
  applicant_id INT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  abandoned_at DATETIME NULL,
  penalty_applied TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_applicant (task_id, applicant_id),
  KEY idx_applicant (applicant_id, status),
  KEY idx_task_status (task_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE conversations (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id INT UNSIGNED NOT NULL,
  publisher_id INT UNSIGNED NOT NULL,
  acceptor_id INT UNSIGNED NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE messages (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  conversation_id INT UNSIGNED NOT NULL,
  sender_id INT UNSIGNED NOT NULL,
  msg_type VARCHAR(8) NOT NULL,
  content TEXT NULL,
  image_path VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_conv (conversation_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id INT UNSIGNED NOT NULL,
  from_user_id INT UNSIGNED NOT NULL,
  to_user_id INT UNSIGNED NOT NULL,
  rating VARCHAR(8) NOT NULL,
  comment VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_from (task_id, from_user_id),
  KEY idx_to (to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE point_logs (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  change_amount INT NOT NULL,
  balance_after INT NOT NULL,
  type VARCHAR(16) NOT NULL,
  related_task_id INT UNSIGNED NULL,
  remark VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE credit_logs (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  change_amount INT NOT NULL,
  reason VARCHAR(16) NOT NULL,
  related_task_id INT UNSIGNED NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appeals (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  task_id INT UNSIGNED NOT NULL,
  content VARCHAR(500) NOT NULL,
  screenshot_path VARCHAR(255) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  reject_reason VARCHAR(255) NULL,
  handled_by INT UNSIGNED NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_task (user_id, task_id),
  KEY idx_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notifications (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  type VARCHAR(32) NOT NULL,
  title VARCHAR(64) NOT NULL,
  content VARCHAR(255) NOT NULL,
  related_task_id INT UNSIGNED NULL,
  is_read TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, is_read, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
