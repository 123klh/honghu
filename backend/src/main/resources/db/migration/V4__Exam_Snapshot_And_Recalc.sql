-- V4：考试快照表、修正试卷均分触发器、维护字段重算存储过程
--
-- 背景（V1-V3 已应用，不可修改，故在此以 V4 补正）：
--   1. exam_score 在开考时写入 score=0，而 trg_exam_score_after_insert 对
--      paper.avg_score 取全部非空 score 的平均，导致「开考即拉低均分」。
--      本迁移把触发器限定为只统计 status='SUBMITTED' 的成绩。
--   2. 整卷考试缺少题目快照：开考后题目被管理员改动或下架，判分依据就变了。
--      新增 exam_question 记录开考瞬间的题目与顺序，提交时以快照为准。
--   3. 冗余维护字段（questions_count / avg_score / avg_accuracy_rate）一旦因
--      异常中断而漂移，没有校正手段。新增三个重算过程，均为集合运算，
--      不使用 ORDER BY RAND()。

-- ---------------------------------------------------------------- 考试快照

CREATE TABLE exam_question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    exam_id BIGINT NOT NULL COMMENT '所属考试',
    question_id BIGINT NOT NULL COMMENT '题目ID',
    question_order INT NOT NULL COMMENT '题号（开考时确定，提交时按此顺序判分）',
    answer_snapshot VARCHAR(10) COMMENT '开考瞬间的正确答案，防止中途改题影响判分',
    UNIQUE KEY uk_exam_question (exam_id, question_id),
    KEY idx_exam_order (exam_id, question_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试题目快照';

-- -------------------------------------------------- 修正试卷均分触发器

DROP TRIGGER IF EXISTS trg_exam_score_after_insert;
DROP TRIGGER IF EXISTS trg_exam_score_after_update;

DELIMITER $$
CREATE TRIGGER trg_exam_score_after_insert
AFTER INSERT ON exam_score
FOR EACH ROW
BEGIN
    IF NEW.status = 'SUBMITTED' AND NEW.score IS NOT NULL THEN
        UPDATE paper p
        SET p.avg_score = (
            SELECT ROUND(AVG(es.score), 2) FROM exam_score es
            WHERE es.paper_id = NEW.paper_id
              AND es.status = 'SUBMITTED' AND es.score IS NOT NULL
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
    IF NEW.status = 'SUBMITTED' AND NEW.score IS NOT NULL THEN
        UPDATE paper p
        SET p.avg_score = (
            SELECT ROUND(AVG(es.score), 2) FROM exam_score es
            WHERE es.paper_id = NEW.paper_id
              AND es.status = 'SUBMITTED' AND es.score IS NOT NULL
        )
        WHERE p.paper_id = NEW.paper_id;
    END IF;
END$$
DELIMITER ;

-- --------------------------------------------------------- 重算存储过程
--
-- 冗余维护字段一旦因异常中断而漂移，需要一个可重复执行的校正手段。
-- 三个过程都是整表集合运算，不使用 ORDER BY RAND()。

DROP PROCEDURE IF EXISTS sp_recalc_paper_questions_count;
DROP PROCEDURE IF EXISTS sp_recalc_paper_avg_score;
DROP PROCEDURE IF EXISTS sp_recalc_question_accuracy;

-- 试卷题量：客观题 + 申论题，只算上架的
DELIMITER $$
CREATE PROCEDURE sp_recalc_paper_questions_count()
BEGIN
    UPDATE paper p
    SET p.questions_count = (
        SELECT COUNT(*) FROM question q
        WHERE q.paper_id = p.paper_id AND q.status = 1
    ) + (
        SELECT COUNT(*) FROM fr_question f
        WHERE f.paper_id = p.paper_id AND f.status = 1
    );
END$$
DELIMITER ;

-- 试卷平均分：只统计已提交的成绩；无成绩时归零而不是留下旧值
DELIMITER $$
CREATE PROCEDURE sp_recalc_paper_avg_score()
BEGIN
    UPDATE paper p
    SET p.avg_score = COALESCE((
        SELECT ROUND(AVG(es.score), 2) FROM exam_score es
        WHERE es.paper_id = p.paper_id
          AND es.status = 'SUBMITTED' AND es.score IS NOT NULL
    ), 0);
END$$
DELIMITER ;

-- 题目平均正确率：只看客观题记录（is_correct 非空），无记录的保持 0
DELIMITER $$
CREATE PROCEDURE sp_recalc_question_accuracy()
BEGIN
    UPDATE question q
    SET q.avg_accuracy_rate = COALESCE((
        SELECT ROUND(AVG(ar.is_correct), 4) FROM answer_record ar
        WHERE ar.question_id = q.question_id AND ar.is_correct IS NOT NULL
    ), 0);
END$$
DELIMITER ;
