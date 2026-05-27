package sqlancer.fucci.reducer;

import java.util.List;

/**
 * 简化顺序选择器接口。
 * 用于四层简化框架中每层的选择策略。
 */
public interface FucciOrderSelector<T> {

    /**
     * 选择下一个候选类型(排除指定列表)
     *
     * @param excludedList 排除列表
     * @return 下一个候选类型
     */
    T selectNext(List<T> excludedList);

    /**
     * 选择下一个候选类型
     *
     * @return 下一个候选类型
     */
    T selectNext();

    /**
     * 更新权重(用于概率表和epsilon-greedy策略)
     *
     * @param candidate 候选类型
     * @param success 是否成功简化
     */
    void updateWeight(T candidate, boolean success);
}