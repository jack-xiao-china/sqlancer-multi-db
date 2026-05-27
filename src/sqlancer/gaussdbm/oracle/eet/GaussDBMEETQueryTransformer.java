package sqlancer.gaussdbm.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.gaussdbm.ast.GaussDBCteDefinition;
import sqlancer.gaussdbm.ast.GaussDBCteTableReference;
import sqlancer.gaussdbm.ast.GaussDBDerivedTable;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBJoin;
import sqlancer.gaussdbm.ast.GaussDBSelect;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBUnionSelect;
import sqlancer.gaussdbm.ast.GaussDBWithSelect;

/**
 * Traverses a {@link GaussDBSelect} and applies EET transforms (copy-based, original AST unchanged).
 */
public class GaussDBMEETQueryTransformer {

    private final GaussDBMEETTransformer transformer;

    public GaussDBMEETQueryTransformer(GaussDBMEETTransformer transformer) {
        this.transformer = transformer;
    }

    public GaussDBExpression eqTransformRoot(GaussDBExpression root) {
        if (root instanceof GaussDBUnionSelect) {
            GaussDBUnionSelect u = (GaussDBUnionSelect) root;
            List<GaussDBSelect> newBranches = new ArrayList<>();
            for (GaussDBSelect b : u.getBranches()) {
                newBranches.add(eqTransformQuery(b));
            }
            return new GaussDBUnionSelect(newBranches, u.isUnionAll());
        }
        if (root instanceof GaussDBWithSelect) {
            GaussDBWithSelect w = (GaussDBWithSelect) root;
            List<GaussDBCteDefinition> newCtes = new ArrayList<>();
            for (GaussDBCteDefinition c : w.getCtes()) {
                newCtes.add(new GaussDBCteDefinition(c.getName(), eqTransformQuery(c.getSubquery())));
            }
            return new GaussDBWithSelect(newCtes, eqTransformQuery(w.getMainQuery()));
        }
        if (root instanceof GaussDBSelect) {
            return eqTransformQuery((GaussDBSelect) root);
        }
        return root;
    }

    public GaussDBSelect eqTransformQuery(GaussDBSelect select) {
        GaussDBSelect copy = shallowCopySelect(select);
        boolean hasGroup = select.getGroupByExpressions() != null && !select.getGroupByExpressions().isEmpty();

        if (!hasGroup) {
            List<GaussDBExpression> newFetch = new ArrayList<>();
            for (GaussDBExpression col : select.getFetchColumns()) {
                newFetch.add(transformer.transformExpression(col));
            }
            copy.setFetchColumns(newFetch);
        }

        copy.setFromList(transformFromList(select.getFromList()));
        copy.setJoinClauses(transformJoinList(select.getJoinClauses()));

        if (select.getWhereClause() != null) {
            copy.setWhereClause(transformer.transformExpression(select.getWhereClause()));
        }
        if (select.getHavingClause() != null) {
            copy.setHavingClause(transformer.transformExpression(select.getHavingClause()));
        }
        return copy;
    }

    private static GaussDBSelect shallowCopySelect(GaussDBSelect s) {
        GaussDBSelect copy = new GaussDBSelect();
        copy.setSelectType(s.getSelectType());
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

    private List<GaussDBExpression> transformFromList(List<GaussDBExpression> fromList) {
        List<GaussDBExpression> out = new ArrayList<>();
        for (GaussDBExpression ref : fromList) {
            out.add(transformTableRef(ref));
        }
        return out;
    }

    private GaussDBExpression transformTableRef(GaussDBExpression ref) {
        if (ref instanceof GaussDBTableReference) {
            return ref;
        }
        if (ref instanceof GaussDBCteTableReference) {
            return ref;
        }
        if (ref instanceof GaussDBDerivedTable) {
            GaussDBDerivedTable d = (GaussDBDerivedTable) ref;
            return new GaussDBDerivedTable(eqTransformQuery(d.getSubquery()), d.getAlias());
        }
        if (ref instanceof GaussDBSelect) {
            return eqTransformQuery((GaussDBSelect) ref);
        }
        return ref;
    }

    private List<GaussDBJoin> transformJoinList(List<GaussDBJoin> joins) {
        return joins.stream().map(this::transformJoin).collect(Collectors.toList());
    }

    private GaussDBJoin transformJoin(GaussDBJoin join) {
        GaussDBExpression on = join.getOnCondition();
        GaussDBExpression newOn = on == null ? null : transformer.transformExpression(on);
        return new GaussDBJoin(join.getTableReference(), newOn, join.getJoinType());
    }
}
