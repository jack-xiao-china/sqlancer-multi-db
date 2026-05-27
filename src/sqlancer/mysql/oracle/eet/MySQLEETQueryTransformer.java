package sqlancer.mysql.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.mysql.ast.MySQLCteDefinition;
import sqlancer.mysql.ast.MySQLCteTableReference;
import sqlancer.mysql.ast.MySQLDerivedTable;
import sqlancer.mysql.ast.MySQLExpression;
import sqlancer.mysql.ast.MySQLJoin;
import sqlancer.mysql.ast.MySQLSelect;
import sqlancer.mysql.ast.MySQLStringExpression;
import sqlancer.mysql.ast.MySQLTableReference;
import sqlancer.mysql.ast.MySQLUnionSelect;
import sqlancer.mysql.ast.MySQLWithSelect;
import sqlancer.mysql.gen.MySQLExpressionGenerator;

/**
 * Traverses a {@link MySQLSelect} and applies EET transforms (copy-based, original AST unchanged).
 */
public class MySQLEETQueryTransformer {

    private final MySQLEETTransformer transformer;

    public MySQLEETQueryTransformer(MySQLEETTransformer transformer) {
        this.transformer = transformer;
    }

    public MySQLEETQueryTransformer(MySQLExpressionGenerator gen) {
        this.transformer = new MySQLEETTransformer(gen);
    }

    /**
     * Applies EET to the root query: plain {@link MySQLSelect}, {@link MySQLUnionSelect}, or {@link MySQLWithSelect}.
     * {@link MySQLSelect} with derived tables in FROM is handled via {@link #eqTransformQuery(MySQLSelect)}.
     */
    public MySQLExpression eqTransformRoot(MySQLExpression root) {
        if (root instanceof MySQLUnionSelect) {
            MySQLUnionSelect u = (MySQLUnionSelect) root;
            List<MySQLSelect> newBranches = new ArrayList<>();
            for (MySQLSelect b : u.getBranches()) {
                newBranches.add(eqTransformQuery(b));
            }
            return new MySQLUnionSelect(newBranches, u.isUnionAll());
        }
        if (root instanceof MySQLWithSelect) {
            MySQLWithSelect w = (MySQLWithSelect) root;
            List<MySQLCteDefinition> newCtes = new ArrayList<>();
            for (MySQLCteDefinition c : w.getCtes()) {
                newCtes.add(new MySQLCteDefinition(c.getName(), eqTransformQuery(c.getSubquery())));
            }
            return new MySQLWithSelect(newCtes, eqTransformQuery(w.getMainQuery()));
        }
        if (root instanceof MySQLSelect) {
            return eqTransformQuery((MySQLSelect) root);
        }
        return root;
    }

    public MySQLSelect eqTransformQuery(MySQLSelect select) {
        MySQLSelect copy = shallowCopySelect(select);
        boolean hasGroup = select.getGroupByExpressions() != null && !select.getGroupByExpressions().isEmpty();

        if (!hasGroup) {
            List<MySQLExpression> newFetch = new ArrayList<>();
            for (MySQLExpression col : select.getFetchColumns()) {
                newFetch.add(transformer.transformExpression(col));
            }
            copy.setFetchColumns(newFetch);
        }

        copy.setFromList(transformFromList(select.getFromList()));
        copy.setJoinList(transformJoinList(select.getJoinClauses()));

        if (select.getWhereClause() != null) {
            copy.setWhereClause(transformer.transformExpression(select.getWhereClause()));
        }
        if (select.getHavingClause() != null) {
            copy.setHavingClause(transformer.transformExpression(select.getHavingClause()));
        }
        return copy;
    }

    private static MySQLSelect shallowCopySelect(MySQLSelect s) {
        MySQLSelect copy = new MySQLSelect();
        copy.setFromOptions(s.getFromOptions());
        copy.setModifiers(s.getModifiers());
        copy.setHint(s.getHint());
        copy.setFetchColumns(new ArrayList<>(s.getFetchColumns()));
        copy.setFromList(new ArrayList<>(s.getFromList()));
        copy.setJoinList(new ArrayList<>(s.getJoinList()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        copy.setOffsetClause(s.getOffsetClause());
        return copy;
    }

    private List<MySQLExpression> transformFromList(List<MySQLExpression> fromList) {
        List<MySQLExpression> out = new ArrayList<>();
        for (MySQLExpression ref : fromList) {
            out.add(transformTableRef(ref));
        }
        return out;
    }

    private MySQLExpression transformTableRef(MySQLExpression ref) {
        if (ref instanceof MySQLTableReference) {
            return ref;
        }
        if (ref instanceof MySQLCteTableReference) {
            return ref;
        }
        if (ref instanceof MySQLDerivedTable) {
            MySQLDerivedTable d = (MySQLDerivedTable) ref;
            return new MySQLDerivedTable(eqTransformQuery(d.getSubquery()), d.getAlias());
        }
        if (ref instanceof MySQLSelect) {
            return eqTransformQuery((MySQLSelect) ref);
        }
        if (ref instanceof MySQLStringExpression) {
            return ref;
        }
        return ref;
    }

    private List<MySQLExpression> transformJoinList(List<MySQLJoin> joins) {
        return joins.stream().map(this::transformJoin).map(j -> (MySQLExpression) j).collect(Collectors.toList());
    }

    private MySQLJoin transformJoin(MySQLJoin join) {
        MySQLExpression on = join.getOnClause();
        MySQLExpression newOn = on == null ? null : transformer.transformExpression(on);
        return new MySQLJoin(join.getTable(), newOn, join.getType());
    }
}
