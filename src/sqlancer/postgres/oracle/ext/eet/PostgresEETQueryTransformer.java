package sqlancer.postgres.oracle.ext.eet;

import java.util.ArrayList;
import java.util.List;

import sqlancer.postgres.ast.PostgresCteDefinition;
import sqlancer.postgres.ast.PostgresExceptSelect;
import sqlancer.postgres.ast.PostgresIntersectSelect;
import sqlancer.postgres.ast.PostgresDerivedTable;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresUnionSelect;
import sqlancer.postgres.ast.PostgresWithSelect;
import sqlancer.postgres.gen.PostgresExpressionGenerator;

/**
 * Traverses a Postgres query AST and applies EET transforms (copy-based, original AST unchanged).
 */
public final class PostgresEETQueryTransformer {

    private final PostgresEETTransformer transformer;

    public PostgresEETQueryTransformer(PostgresEETTransformer transformer) {
        this.transformer = transformer;
    }

    public PostgresEETQueryTransformer(PostgresExpressionGenerator gen) {
        this.transformer = new PostgresEETTransformer(gen);
    }

    public PostgresExpression eqTransformRoot(PostgresExpression root) {
        if (root instanceof PostgresUnionSelect) {
            PostgresUnionSelect u = (PostgresUnionSelect) root;
            List<PostgresSelect> newBranches = new ArrayList<>();
            for (PostgresSelect b : u.getSelects()) {
                newBranches.add(eqTransformQuery(b));
            }
            return new PostgresUnionSelect(newBranches, u.isUnionAll());
        }
        if (root instanceof PostgresIntersectSelect) {
            PostgresIntersectSelect i = (PostgresIntersectSelect) root;
            List<PostgresSelect> newBranches = new ArrayList<>();
            for (PostgresSelect b : i.getSelects()) {
                newBranches.add(eqTransformQuery(b));
            }
            return new PostgresIntersectSelect(newBranches, i.isIntersectAll());
        }
        if (root instanceof PostgresExceptSelect) {
            PostgresExceptSelect e = (PostgresExceptSelect) root;
            List<PostgresSelect> newBranches = new ArrayList<>();
            for (PostgresSelect b : e.getSelects()) {
                newBranches.add(eqTransformQuery(b));
            }
            return new PostgresExceptSelect(newBranches, e.isExceptAll());
        }
        if (root instanceof PostgresWithSelect) {
            PostgresWithSelect w = (PostgresWithSelect) root;
            List<PostgresCteDefinition> newCtes = new ArrayList<>();
            for (PostgresCteDefinition c : w.getCtes()) {
                newCtes.add(new PostgresCteDefinition(c.getName(), eqTransformQuery(c.getSelect())));
            }
            return new PostgresWithSelect(newCtes, eqTransformQuery(w.getMainSelect()));
        }
        if (root instanceof PostgresSelect) {
            return eqTransformQuery((PostgresSelect) root);
        }
        return root;
    }

    public PostgresSelect eqTransformQuery(PostgresSelect select) {
        PostgresSelect copy = shallowCopySelect(select);

        if (select.getWhereClause() != null) {
            copy.setWhereClause(transformer.transformExpression(select.getWhereClause()));
        }
        if (select.getHavingClause() != null) {
            copy.setHavingClause(transformer.transformExpression(select.getHavingClause()));
        }
        boolean hasGroup = select.getGroupByExpressions() != null && !select.getGroupByExpressions().isEmpty();
        if (!hasGroup) {
            List<PostgresExpression> newFetch = new ArrayList<>();
            for (PostgresExpression col : select.getFetchColumns()) {
                newFetch.add(transformer.transformExpression(col));
            }
            copy.setFetchColumns(newFetch);
        }

        // Transform derived tables in FROM
        List<PostgresExpression> newFrom = new ArrayList<>();
        for (PostgresExpression ref : select.getFromList()) {
            if (ref instanceof PostgresDerivedTable) {
                PostgresDerivedTable d = (PostgresDerivedTable) ref;
                newFrom.add(new PostgresDerivedTable(eqTransformQuery(d.getSelect()), d.getAlias()));
            } else {
                newFrom.add(ref);
            }
        }
        copy.setFromList(newFrom);
        return copy;
    }

    private static PostgresSelect shallowCopySelect(PostgresSelect s) {
        PostgresSelect copy = new PostgresSelect();
        copy.setSelectOption(s.getSelectOption());
        copy.setFetchColumns(new ArrayList<>(s.getFetchColumns()));
        copy.setFromList(new ArrayList<>(s.getFromList()));
        copy.setJoinClauses(new ArrayList<>(s.getJoinClauses()));
        copy.setWhereClause(s.getWhereClause());
        copy.setGroupByExpressions(new ArrayList<>(s.getGroupByExpressions()));
        copy.setHavingClause(s.getHavingClause());
        copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
        copy.setLimitClause(s.getLimitClause());
        copy.setOffsetClause(s.getOffsetClause());
        return copy;
    }
}

