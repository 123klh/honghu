-- V1__Initial_Schema.sql
-- 波波公考 - Initial Database Schema

-- 用户表
CREATE TABLE user (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt哈希',
    gender ENUM('男', '女') COMMENT '性别',
    birthday DATE COMMENT '生日',
    address VARCHAR(200) COMMENT '地址',
    email VARCHAR(100) COMMENT '邮箱',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    descr VARCHAR(500) COMMENT '备注',
    INDEX idx_user_name (user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 管理员表
CREATE TABLE manager (
    manager_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    manager_name VARCHAR(50) NOT NULL UNIQUE COMMENT '管理员用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt哈希',
    role VARCHAR(20) DEFAULT 'ADMIN' COMMENT '角色',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    descr VARCHAR(500) COMMENT '备注',
    INDEX idx_manager_name (manager_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 知识点表
CREATE TABLE knowledge_point (
    kp_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    kp_name VARCHAR(100) NOT NULL COMMENT '知识点名称',
    parent_id BIGINT DEFAULT 0 COMMENT '父知识点ID，0为根',
    subject VARCHAR(50) COMMENT '所属科目',
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent (parent_id),
    INDEX idx_subject (subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识点';

-- 试卷表
CREATE TABLE paper (
    paper_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_name VARCHAR(200) NOT NULL COMMENT '试卷名称',
    source VARCHAR(100) COMMENT '来源',
    debut VARCHAR(50) COMMENT '年份',
    questions_count INT DEFAULT 0 COMMENT '题目数量（触发器维护）',
    avg_score DOUBLE DEFAULT 0 COMMENT '平均分（触发器维护）',
    exam_duration INT COMMENT '考试时长（分钟）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    subject VARCHAR(50) COMMENT '科目',
    descr TEXT COMMENT '描述',
    INDEX idx_source (source),
    INDEX idx_subject (subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷表';

-- 客观题表
CREATE TABLE question (
    question_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_code VARCHAR(100) NOT NULL UNIQUE COMMENT '题目唯一编码（源哈希+序号）',
    subject VARCHAR(50) COMMENT '科目',
    question_stem TEXT NOT NULL COMMENT '题干',
    option_a TEXT COMMENT '选项A',
    option_b TEXT COMMENT '选项B',
    option_c TEXT COMMENT '选项C',
    option_d TEXT COMMENT '选项D',
    enclosure TINYINT DEFAULT 0 COMMENT '是否含附件',
    answer VARCHAR(10) NOT NULL COMMENT '答案',
    question_type VARCHAR(50) COMMENT '题型',
    avg_accuracy_rate DOUBLE DEFAULT 0 COMMENT '平均正确率（触发器维护）',
    paper_id BIGINT COMMENT '所属试卷',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
    explanation TEXT COMMENT '解析',
    INDEX idx_code (question_code),
    INDEX idx_type (question_type),
    INDEX idx_subject (subject),
    INDEX idx_paper (paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客观题表';

-- 题目-知识点关联表
CREATE TABLE question_knowledge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    kp_id BIGINT NOT NULL,
    UNIQUE KEY uk_q_kp (question_id, kp_id),
    INDEX idx_kp (kp_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目知识点关联';

-- 题目难度表
CREATE TABLE question_difficulty (
    question_id BIGINT PRIMARY KEY,
    difficulty_value DOUBLE COMMENT '难度值（0-1）',
    source_accuracy_rate DOUBLE COMMENT '来源正确率',
    common_mistake VARCHAR(10) COMMENT '易错项',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目难度';

-- 申论题表
CREATE TABLE fr_question (
    fr_question_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_code VARCHAR(100) NOT NULL UNIQUE COMMENT '题目编码',
    material MEDIUMTEXT COMMENT '材料',
    fr_question_stem TEXT NOT NULL COMMENT '题干',
    answer MEDIUMTEXT COMMENT '参考答案',
    fr_question_type VARCHAR(50) COMMENT '申论题型',
    points INT COMMENT '分值',
    avg_score DOUBLE DEFAULT 0 COMMENT '平均分',
    paper_id BIGINT COMMENT '所属试卷',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1,
    subject VARCHAR(50) COMMENT '科目',
    descr TEXT COMMENT '备注',
    INDEX idx_code (question_code),
    INDEX idx_type (fr_question_type),
    INDEX idx_paper (paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申论题表';

-- 申论评分要点表
CREATE TABLE shenlun_rubric (
    rubric_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fr_question_id BIGINT NOT NULL,
    point_order INT NOT NULL COMMENT '要点序号',
    point_text TEXT NOT NULL COMMENT '得分点原文',
    point_score DOUBLE NOT NULL COMMENT '该要点分值',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_frq (fr_question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申论评分要点';

-- 申论要点同义词表
CREATE TABLE shenlun_rubric_synonym (
    synonym_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rubric_id BIGINT NOT NULL,
    synonym_text VARCHAR(200) NOT NULL COMMENT '同义表达',
    INDEX idx_rubric (rubric_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申论要点同义词';

-- 答题记录表
CREATE TABLE answer_record (
    record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_id BIGINT COMMENT '考试ID',
    question_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_answer LONGTEXT COMMENT '用户答案',
    is_correct TINYINT COMMENT '是否正确（客观题）',
    score_rate DOUBLE COMMENT '得分率（申论题，0-100）',
    duration INT COMMENT '答题用时（秒）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    subject VARCHAR(50) COMMENT '科目',
    review_status TINYINT DEFAULT 0 COMMENT '复核状态：0未复核 1已复核',
    INDEX idx_user (user_id),
    INDEX idx_exam (exam_id),
    INDEX idx_question (question_id),
    INDEX idx_correct (is_correct)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='答题记录';

-- 考试成绩表
CREATE TABLE exam_score (
    exam_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    paper_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score DOUBLE COMMENT '得分',
    total_score DOUBLE COMMENT '总分',
    duration INT COMMENT '用时（秒）',
    start_time DATETIME COMMENT '开始时间',
    submit_time DATETIME COMMENT '提交时间',
    status VARCHAR(20) COMMENT '状态',
    descr TEXT COMMENT '备注',
    INDEX idx_user (user_id),
    INDEX idx_paper (paper_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试成绩表';

-- 用户掌握度表
CREATE TABLE user_mastery (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    kp_id BIGINT NOT NULL,
    mastery_theta DOUBLE DEFAULT 0 COMMENT '掌握度θ',
    review_due_at DATETIME COMMENT 'SM-2间隔复习到期时间',
    sm2_interval INT DEFAULT 0 COMMENT 'SM-2间隔（天）',
    sm2_ef DOUBLE DEFAULT 2.5 COMMENT 'SM-2 EF因子',
    last_answered_at DATETIME COMMENT '最后答题时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_kp (user_id, kp_id),
    INDEX idx_user (user_id),
    INDEX idx_due (review_due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户掌握度';

-- 错题本表
CREATE TABLE wrong_book (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_type VARCHAR(20) NOT NULL COMMENT 'objective/subjective',
    added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    mastered_at DATETIME COMMENT '掌握时间',
    status VARCHAR(20) DEFAULT 'wrong' COMMENT 'wrong/mastered',
    retry_count INT DEFAULT 0 COMMENT '重做次数',
    UNIQUE KEY uk_user_q (user_id, question_id, question_type),
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题本';

-- 收藏夹表
CREATE TABLE user_favorite_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    folder VARCHAR(50) COMMENT '文件夹',
    notes VARCHAR(200) COMMENT '笔记',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_q (user_id, question_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客观题收藏';

CREATE TABLE user_favorite_frquestion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    fr_question_id BIGINT NOT NULL,
    folder VARCHAR(50) COMMENT '文件夹',
    notes VARCHAR(200) COMMENT '笔记',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_frq (user_id, fr_question_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申论题收藏';

CREATE TABLE user_favorite_paper (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    paper_id BIGINT NOT NULL,
    folder VARCHAR(50) COMMENT '文件夹',
    notes VARCHAR(200) COMMENT '笔记',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_paper (user_id, paper_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试卷收藏';

-- 答疑社区
CREATE TABLE discussion (
    discussion_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_id BIGINT COMMENT '关联题目',
    question_type VARCHAR(20) COMMENT 'objective/subjective',
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '内容',
    is_pinned TINYINT DEFAULT 0 COMMENT '是否置顶',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1 COMMENT '1正常 0已删除',
    INDEX idx_question (question_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讨论帖';

CREATE TABLE discussion_reply (
    reply_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    discussion_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_accepted TINYINT DEFAULT 0 COMMENT '是否采纳',
    likes_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1,
    INDEX idx_discussion (discussion_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='讨论回复';

-- 公告表
CREATE TABLE announcement (
    announcement_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    content TEXT,
    link_url VARCHAR(500) COMMENT '链接',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    status TINYINT DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公告';

-- 考试倒计时表
CREATE TABLE exam_countdown (
    countdown_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_name VARCHAR(100) NOT NULL COMMENT '考试名称',
    exam_date DATE NOT NULL COMMENT '考试日期',
    sort_order INT DEFAULT 0,
    status TINYINT DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试倒计时';

-- 操作日志表
CREATE TABLE operate_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operate_user_id BIGINT COMMENT '操作人ID',
    operate_user_type VARCHAR(20) COMMENT 'user/manager',
    operate_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    class_name VARCHAR(200) COMMENT '类名',
    method_name VARCHAR(100) COMMENT '方法名',
    method_params VARCHAR(2000) COMMENT '参数',
    return_value VARCHAR(2000) COMMENT '返回值',
    cost_time BIGINT COMMENT '耗时（毫秒）',
    INDEX idx_user (operate_user_id),
    INDEX idx_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';
