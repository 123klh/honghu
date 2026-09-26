package com.honghu.service;

import com.honghu.common.BusinessException;
import com.honghu.mapper.FavoriteMapper;
import com.honghu.mapper.PaperMapper;
import com.honghu.mapper.QuestionMapper;
import com.honghu.pojo.UserFavoriteFrQuestion;
import com.honghu.pojo.UserFavoritePaper;
import com.honghu.pojo.UserFavoriteQuestion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 收藏夹。三类目标（客观题、申论题、试卷）分表存储，收藏前校验目标存在且未下架，
 * 避免收藏到脏 ID。备注与分组允许为空，长度由数据库列宽兜底。
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;

    public void addQuestion(Long userId, Long questionId, String folder, String notes) {
        if (questionMapper.findActiveById(questionId) == null) {
            throw new BusinessException(404, "题目不存在或已下架");
        }
        UserFavoriteQuestion f = new UserFavoriteQuestion();
        f.setUserId(userId);
        f.setQuestionId(questionId);
        f.setFolder(folder);
        f.setNotes(notes);
        favoriteMapper.upsertQuestion(f);
    }

    public void removeQuestion(Long userId, Long questionId) {
        favoriteMapper.deleteQuestion(userId, questionId);
    }

    public List<UserFavoriteQuestion> listQuestions(Long userId) {
        return favoriteMapper.listQuestions(userId);
    }

    public void addPaper(Long userId, Long paperId, String folder, String notes) {
        if (paperMapper.findActiveById(paperId) == null) {
            throw new BusinessException(404, "试卷不存在或已下架");
        }
        UserFavoritePaper f = new UserFavoritePaper();
        f.setUserId(userId);
        f.setPaperId(paperId);
        f.setFolder(folder);
        f.setNotes(notes);
        favoriteMapper.upsertPaper(f);
    }

    public void removePaper(Long userId, Long paperId) {
        favoriteMapper.deletePaper(userId, paperId);
    }

    public List<UserFavoritePaper> listPapers(Long userId) {
        return favoriteMapper.listPapers(userId);
    }

    public void addFrQuestion(Long userId, Long frQuestionId, String folder, String notes) {
        UserFavoriteFrQuestion f = new UserFavoriteFrQuestion();
        f.setUserId(userId);
        f.setFrQuestionId(frQuestionId);
        f.setFolder(folder);
        f.setNotes(notes);
        favoriteMapper.upsertFrQuestion(f);
    }

    public void removeFrQuestion(Long userId, Long frQuestionId) {
        favoriteMapper.deleteFrQuestion(userId, frQuestionId);
    }

    public List<UserFavoriteFrQuestion> listFrQuestions(Long userId) {
        return favoriteMapper.listFrQuestions(userId);
    }
}
