package sqlancer.fucci.reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 概率表顺序选择器。
 * 基于历史成功率调整选择概率。
 */
public class FucciProbabilityTableOrderSelector<T> implements FucciOrderSelector<T> {

    /** 候选列表 */
    private List<T> candidates;

    /** 成功次数统计 */
    private Map<T, Integer> successCount;

    /** 失败次数统计 */
    private Map<T, Integer> failureCount;

    /** 初始概率 */
    private double initialProbability = 0.5;

    /**
     * 构造函数
     *
     * @param candidates 候选列表
     */
    public FucciProbabilityTableOrderSelector(List<T> candidates) {
        this.candidates = new ArrayList<>(candidates);
        this.successCount = new HashMap<>();
        this.failureCount = new HashMap<>();

        for (T candidate : candidates) {
            successCount.put(candidate, 0);
            failureCount.put(candidate, 0);
        }
    }

    @Override
    public T selectNext(List<T> excludedList) {
        List<T> availableCandidates = new ArrayList<>(candidates);
        availableCandidates.removeAll(excludedList);

        if (availableCandidates.isEmpty()) {
            return null;
        }

        // 计算每个候选的概率
        Map<T, Double> probabilities = new HashMap<>();
        double totalProbability = 0.0;

        for (T candidate : availableCandidates) {
            double prob = calculateProbability(candidate);
            probabilities.put(candidate, prob);
            totalProbability += prob;
        }

        // 按概率随机选择
        double randomValue = Math.random() * totalProbability;
        double cumulative = 0.0;

        for (T candidate : availableCandidates) {
            cumulative += probabilities.get(candidate);
            if (randomValue <= cumulative) {
                return candidate;
            }
        }

        return availableCandidates.get(availableCandidates.size() - 1);
    }

    @Override
    public T selectNext() {
        return selectNext(new ArrayList<>());
    }

    @Override
    public void updateWeight(T candidate, boolean success) {
        if (success) {
            successCount.put(candidate, successCount.getOrDefault(candidate, 0) + 1);
        } else {
            failureCount.put(candidate, failureCount.getOrDefault(candidate, 0) + 1);
        }
    }

    /**
     * 计算候选的概率
     *
     * @param candidate 候选类型
     * @return 选择概率
     */
    private double calculateProbability(T candidate) {
        int success = successCount.getOrDefault(candidate, 0);
        int failure = failureCount.getOrDefault(candidate, 0);

        if (success + failure == 0) {
            return initialProbability;
        }

        // 成功概率 + 额外探索概率
        return (success / (double) (success + failure)) + 0.1;
    }

    /**
     * 获取候选的成功率
     *
     * @param candidate 候选类型
     * @return 成功率(0-1)
     */
    public double getSuccessRate(T candidate) {
        int success = successCount.getOrDefault(candidate, 0);
        int failure = failureCount.getOrDefault(candidate, 0);

        if (success + failure == 0) {
            return initialProbability;
        }

        return success / (double) (success + failure);
    }
}