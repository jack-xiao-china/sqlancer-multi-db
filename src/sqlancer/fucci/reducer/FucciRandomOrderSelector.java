package sqlancer.fucci.reducer;

import java.util.ArrayList;
import java.util.List;

import sqlancer.Randomly;

/**
 * 随机顺序选择器。
 * 从候选列表中随机选择,不考虑权重。
 */
public class FucciRandomOrderSelector<T> implements FucciOrderSelector<T> {

    /** 候选列表 */
    private List<T> candidates;

    /**
     * 构造函数
     *
     * @param candidates 候选列表
     */
    public FucciRandomOrderSelector(List<T> candidates) {
        this.candidates = new ArrayList<>(candidates);
    }

    @Override
    public T selectNext(List<T> excludedList) {
        List<T> candidatesCopy = new ArrayList<>(candidates);
        candidatesCopy.removeAll(excludedList);
        if (candidatesCopy.isEmpty()) {
            return null;
        }
        return Randomly.fromList(candidatesCopy);
    }

    @Override
    public T selectNext() {
        if (candidates.isEmpty()) {
            return null;
        }
        return Randomly.fromList(candidates);
    }

    @Override
    public void updateWeight(T candidate, boolean success) {
        // 随机选择器不更新权重
    }

    /**
     * 添加候选
     *
     * @param candidate 候选类型
     */
    public void addCandidate(T candidate) {
        candidates.add(candidate);
    }

    /**
     * 获取候选列表
     *
     * @return 候选列表
     */
    public List<T> getCandidates() {
        return new ArrayList<>(candidates);
    }
}