package sqlancer.fucci.reducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sqlancer.Randomly;

/**
 * Epsilon-Greedy顺序选择器。
 * 以epsilon概率随机探索,以1-epsilon概率选择最优。
 */
public class FucciEpsilonGreedyOrderSelector<T> implements FucciOrderSelector<T> {

    /** 候选列表 */
    private List<T> candidates;

    /** 成功次数统计 */
    private Map<T, Integer> successCount;

    /** 失败次数统计 */
    private Map<T, Integer> failureCount;

    /** 探索概率epsilon */
    private double epsilon = 0.1;

    /**
     * 构造函数
     *
     * @param candidates 候选列表
     */
    public FucciEpsilonGreedyOrderSelector(List<T> candidates) {
        this.candidates = new ArrayList<>(candidates);
        this.successCount = new HashMap<>();
        this.failureCount = new HashMap<>();

        for (T candidate : candidates) {
            successCount.put(candidate, 0);
            failureCount.put(candidate, 0);
        }
    }

    /**
     * 构造函数(指定epsilon)
     *
     * @param candidates 候选列表
     * @param epsilon 探索概率
     */
    public FucciEpsilonGreedyOrderSelector(List<T> candidates, double epsilon) {
        this(candidates);
        this.epsilon = epsilon;
    }

    @Override
    public T selectNext(List<T> excludedList) {
        List<T> availableCandidates = new ArrayList<>(candidates);
        availableCandidates.removeAll(excludedList);

        if (availableCandidates.isEmpty()) {
            return null;
        }

        // epsilon概率随机探索
        if (Math.random() < epsilon) {
            return Randomly.fromList(availableCandidates);
        }

        // 1-epsilon概率选择最优
        return selectBest(availableCandidates);
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
     * 选择成功率最高的候选
     *
     * @param availableCandidates 可用候选列表
     * @return 最优候选
     */
    private T selectBest(List<T> availableCandidates) {
        T bestCandidate = null;
        double bestRate = -1;

        for (T candidate : availableCandidates) {
            double rate = getSuccessRate(candidate);
            if (rate > bestRate) {
                bestRate = rate;
                bestCandidate = candidate;
            }
        }

        return bestCandidate != null ? bestCandidate : Randomly.fromList(availableCandidates);
    }

    /**
     * 获取候选的成功率
     *
     * @param candidate 候选类型
     * @return 成功率(0-1)
     */
    private double getSuccessRate(T candidate) {
        int success = successCount.getOrDefault(candidate, 0);
        int failure = failureCount.getOrDefault(candidate, 0);

        if (success + failure == 0) {
            return 0.5; // 未知候选给予中等概率
        }

        return success / (double) (success + failure);
    }

    /**
     * 设置epsilon值
     *
     * @param epsilon 探索概率
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /**
     * 获取epsilon值
     *
     * @return 探索概率
     */
    public double getEpsilon() {
        return epsilon;
    }
}