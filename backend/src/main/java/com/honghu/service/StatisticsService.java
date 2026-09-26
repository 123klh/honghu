package com.honghu.service;

import com.honghu.mapper.StatisticsMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习统计与管理端概览。数值全部由明细表实时聚合，避免展示与真实数据脱节。
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final StatisticsMapper statisticsMapper;

    public UserStats userStats(Long userId, int days) {
        if (days < 1 || days > 365) days = 30;
        UserStats stats = new UserStats();
        long answered = statisticsMapper.totalAnswered(userId);
        long correct = statisticsMapper.totalCorrect(userId);
        stats.setTotalAnswered(answered);
        stats.setTotalCorrect(correct);
        stats.setAccuracy(answered > 0 ? Math.round(correct * 10000.0 / answered) / 10000.0 : 0);
        stats.setExamsTaken(statisticsMapper.examsTaken(userId));
        Double avg = statisticsMapper.avgExamScore(userId);
        stats.setAvgExamScore(avg == null ? 0 : avg);
        stats.setDailyActivity(statisticsMapper.dailyActivity(userId, days));
        stats.setSubjectBreakdown(statisticsMapper.subjectBreakdown(userId));
        return stats;
    }

    public AdminStats adminStats(int days) {
        if (days < 1 || days > 365) days = 30;
        AdminStats stats = new AdminStats();
        stats.setUserCount(statisticsMapper.userCount());
        stats.setNewUsersToday(statisticsMapper.newUsersToday());
        stats.setQuestionCount(statisticsMapper.questionCount());
        stats.setShenlunCount(statisticsMapper.shenlunCount());
        stats.setTodayAnswers(statisticsMapper.todayAnswers());
        stats.setWeekAnswers(statisticsMapper.weekAnswers());
        stats.setDiscussionCount(statisticsMapper.discussionCount());
        stats.setReplyCount(statisticsMapper.replyCount());
        stats.setQuestionTypeStats(statisticsMapper.questionTypeStats());
        stats.setUserGrowth(statisticsMapper.userGrowth(days));
        stats.setDailyActive(statisticsMapper.dailyActive(days));
        stats.setPopularQuestions(statisticsMapper.popularQuestions());
        stats.setRecentActivities(statisticsMapper.recentActivities());
        return stats;
    }

    @Data
    public static class UserStats {
        private long totalAnswered;
        private long totalCorrect;
        private double accuracy;
        private long examsTaken;
        private double avgExamScore;
        private List<Map<String, Object>> dailyActivity;
        private List<Map<String, Object>> subjectBreakdown;
    }

    @Data
    public static class AdminStats {
        private long userCount;
        private long newUsersToday;
        private long questionCount;
        private long shenlunCount;
        private long todayAnswers;
        private long weekAnswers;
        private long discussionCount;
        private long replyCount;
        private List<Map<String, Object>> questionTypeStats;
        private List<Map<String, Object>> userGrowth;
        private List<Map<String, Object>> dailyActive;
        private List<Map<String, Object>> popularQuestions;
        private List<Map<String, Object>> recentActivities;
    }
}
