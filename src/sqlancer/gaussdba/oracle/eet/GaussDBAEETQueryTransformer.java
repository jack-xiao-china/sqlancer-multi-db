package sqlancer.gaussdba.oracle.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import sqlancer.gaussdba.ast.GaussDBACteDefinition;
import sqlancer.gaussdba.ast.GaussDBADerivedTable;
import sqlancer.gaussdba.ast.GaussDBAExpression;
import sqlancer.gaussdba.ast.GaussDBAJoin;
import sqlancer.gaussdba.ast.GaussDBAMinusSelect;
import sqlancer.gaussdba.ast.GaussDBASelect;
import sqlancer.gaussdba.ast.GaussDBATableReference;
import sqlancer.gaussdba.ast.GaussDBAUnionSelect;
import sqlancer.gaussdba.ast.GaussDBAWithSelect;

/**
 * Traverses a {@link GaussDBASelect} and applies EET transforms (copy-based, original AST unchanged).
 */
public class GaussDBAEETQueryTransformer {

    private final GaussDBAEETTransformer transformer;

    public GaussDBAEETQueryTransformer(GaussDBAEETTransformer transformer) {
        this.transformer = transformer;
    }

    public GaussDBAExpression eqTransformRoot(GaussDBAExpression root) {
        if (root instanceof GaussDBAUnionSelect) {
            GaussDBAUnionSelect u = (GaussDBAUnionSelect) root;
            List<GaussDBASelect> newSelects = new ArrayList<>();
            for (GaussDBASelect s : u.getSelects()) {
                newSelects.add(eqTransformQuery(s));
            }
            return new GaussDBAUnionSelect(newSelects, u.isUnionAll());
        }
        if (root instanceof GaussDBAMinusSelect) {
            GaussDBAMinusSelect m = (GaussDBAMinusSelect) root;
            List<GaussDBASelect> newSelects = new ArrayList<>();
            for (GaussDBASelect s : m.getSelects()) {
                newSelects.add(eqTransformQuery(s));
            }
            return new GaussDBAMinusSelect(newSelects, m.isMinusAll());
        }
        if (root instanceof GaussDBAWithSelect) {
            GaussDBAWithSelect w = (GaussDBAWithSelect) root;
            List<GaussDBACteDefinition> newCtes = new ArrayList<>();
            for (GaussDBACteDefinition c : w.getCtes()) {
                newCtes.add(new GaussDBACteDefinition(c.getName(), eqTransformQuery(c.getSelect())));
            }
            return new GaussDBAWithSelect(newCtes, eqTransformQuery(w.getMainSelect()));
        }
        if (root instanceof GaussDBASelect) {
            return eqTransformQuery((GaussDBASelect) root);
        }
        return root;
    }

    public GaussDBASelect eqTransformQuery(GaussDBASelect select) {
        GaussDBASelect copy = shallowCopySelect(select);
        boolean hasGroup = select.getGroupByClause() != null && !select.getGroupByClause().isEmpty();

        if (!hasGroup) {
            List<GaussDBAExpression> newFetch = new ArrayList<>();
            for (GaussDBAExpression col : select.getFetchColumns()) {
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

    private static GaussDBASelect shallowCopySelect(GaussDBASelect s) {
        GaussDBASelect copy = new GaussDBASelect();
        copy.setSelectType(s.getSelectType());
        copy.setFetchColumns(new ArrayList<>(s.getFetchColumns()));
        copy.setFromList(new ArrayList<>(s.getFromList()));
        copy.setJoinList(new ArrayList<>(s.getJoinList()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByClause(new ArrayList<>(s.getGroupByClause()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        copy.setOffsetClause(s.getOffsetClause());
        return copy;
    }

    private List<GaussDBAExpression> transformFromList(List<GaussDBAExpression> fromList) {
        List<GaussDBAExpression> out = new ArrayList<>();
        for (GaussDBAExpression ref : fromList) {
            out.add(transformTableRef(ref));
        }
        return out;
    }

    private GaussDBAExpression transformTableRef(GaussDBAExpression ref) {
        if (ref instanceof GaussDBATableReference) {
            return ref;
        }
        if (ref instanceof GaussDBADerivedTable) {
            GaussDBADerivedTable d = (GaussDBADerivedTable) ref;
            return new GaussDBADerivedTable(eqTransformQuery(d.getSelect()), d.getAlias());
        }
        if (ref instanceof GaussDBASelect) {
            return eqTransformQuery((GaussDBASelect) ref);
        }
        return ref;
    }

    private List<GaussDBAJoin> transformJoinList(List<GaussDBAJoin> joins) {
        return joins.stream().map(this::transformJoin).collect(Collectors.toList());
    }

    private GaussDBAJoin transformJoin(GaussDBAJoin join) {
        GaussDBAExpression on = join.getOnCondition();
        GaussDBAExpression newOn = on == null ? null : transformer.transformExpression(on);
        return new GaussDBAJoin(join.getTableReference(), newOn, join.getJoinType());
    }
}