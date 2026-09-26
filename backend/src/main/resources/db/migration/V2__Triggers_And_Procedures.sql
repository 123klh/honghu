-- V2__Triggers_And_Procedures.sql
-- 触发器：维护冗余统计字段；存储过程：随机抽题与统计

-- 试卷题量：客观题插入/删除时维护 paper.questions_count
DELIMITER $$
CREATE TRIGGER trg_question_after_insert
AFTER INSERT ON question
FOR EACH ROW
BEGIN
    IF NEW.paper_id IS NOT NULL THEN
        UPDATE paper SET questions_count = questions_count + 1 WHERE paper_id = NEW.paper_id;
    END IF;
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER trg_question_after_delete
AFTER DELETE ON question
FOR EACH ROW
BEGIN
    IF OLD.paper_id IS NOT NULL THEN
        UPDATE paper SET questions_count = GREATEST(questions_count - 1, 0) WHERE paper_id = OLD.paper_id;
    END IF;
END$$
DELIMITER ;

-- 申论题题量同样计入试卷题量
DELIMITER $$
CREATE TRIGGER trg_frquestion_after_insert
AFTER INSERT ON fr_question
FOR EACH ROW
BEGIN
    IF NEW.paper_id IS NOT NULL THEN
        UPDATE paper SET questions_count = questions_count + 1 WHERE paper_id = NEW.paper_id;
    END IF;
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER trg_frquestion_after_delete
AFTER DELETE ON fr_question
FOR EACH ROW
BEGIN
    IF OLD.paper_id IS NOT NULL THEN
        UPDATE paper SET questions_count = GREATEST(questions_count - 1, 0) WHERE paper_id = OLD.paper_id;
    END IF;
END$$
DELIMITER ;

-- 答题记录插入后：维护题目平均正确率 / 申论题平均分
DELIMITER $$
CREATE TRIGGER trg_answer_record_after_insert
AFTER INSERT ON answer_record
FOR EACH ROW
BEGIN
    IF NEW.is_correct IS NOT NULL THEN
        UPDATE question q
        SET q.avg_accuracy_rate = (
            SELECT ROUND(AVG(ar.is_correct) * 100, 2)
            FROM answer_record ar
            WHERE ar.question_id = NEW.question_id AND ar.is_correct IS NOT NULL
        )
        WHERE q.question_id = NEW.question_id;
    END IF;

    IF NEW.score_rate IS NOT NULL THEN
        UPDATE fr_question f
        SET f.avg_score = (
            SELECT ROUND(AVG(ar.score_rate), 2)
            FROM answer_record ar
            WHERE ar.question_id = NEW.question_id AND ar.score_rate IS NOT NULL
        )
        WHERE f.fr_question_id = NEW.question_id;
    END IF;
END$$
DELIMITER ;

-- 成绩写入后：维护试卷平均分
DELIMITER $$
CREATE TRIGGER trg_exam_score_after_insert
AFTER INSERT ON exam_score
FOR EACH ROW
BEGIN
    IF NEW.score IS NOT NULL THEN
        UPDATE paper p
        SET p.avg_score = (
            SELECT ROUND(AVG(es.score), 2) FROM exam_score es
            WHERE es.paper_id = NEW.paper_id AND es.score IS NOT NULL
        )
        WHERE p.paper_id = NEW.paper_id;
    END IF;
END$$
DELIMITER ;

DELIMITER $$
CREATE TRIGGER trg_exam_score_after_update
AFTER UPDATE ON exam_score
FOR EACH ROW
BEGIN
    IF NEW.score IS NOT NULL THEN
        UPDATE paper p
        SET p.avg_score = (
            SELECT ROUND(AVG(es.score), 2) FROM exam_score es
            WHERE es.paper_id = NEW.paper_id AND es.score IS NOT NULL
        )
        WHERE p.paper_id = NEW.paper_id;
    END IF;
END$$
DELIMITER ;

-- 存储过程：按科目/题型随机抽题（模拟练习用）
DELIMITER $$
CREATE PROCEDURE sp_random_questions(
    IN p_subject VARCHAR(50),
    IN p_question_type VARCHAR(50),
    IN p_limit INT
)
BEGIN
    SELECT q.*
    FROM question q
    WHERE q.status = 1
      AND (p_subject IS NULL OR p_subject = '' OR q.subject = p_subject)
      AND (p_question_type IS NULL OR p_question_type = '' OR q.question_type = p_question_type)
    ORDER BY RAND()
    LIMIT p_limit;
END$$
DELIMITER ;

-- 存储过程：按难度区间抽题（自适应练习用）
DELIMITER $$
CREATE PROCEDURE sp_questions_by_difficulty(
    IN p_subject VARCHAR(50),
    IN p_min_diff DOUBLE,
    IN p_max_diff DOUBLE,
    IN p_limit INT
)
BEGIN
    SELECT q.*
    FROM question q
    LEFT JOIN question_difficulty d ON d.question_id = q.question_id
    WHERE q.status = 1
      AND (p_subject IS NULL OR p_subject = '' OR q.subject = p_subject)
      AND COALESCE(d.difficulty_value, 0.5) BETWEEN p_min_diff AND p_max_diff
    ORDER BY RAND()
    LIMIT p_limit;
END$$
DELIMITER ;

-- 存储过程：用户学情汇总
DELIMITER $$
CREATE PROCEDURE sp_user_stats(IN p_user_id BIGINT)
BEGIN
    SELECT
        COUNT(*) AS total_answered,
        SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS total_correct,
        ROUND(AVG(CASE WHEN is_correct IS NOT NULL THEN is_correct * 100 END), 2) AS accuracy_rate,
        SUM(COALESCE(duration, 0)) AS total_duration,
        COUNT(DISTINCT DATE(created_at)) AS active_days
    FROM answer_record
    WHERE user_id = p_user_id;
END$$
DELIMITER ;
