package sqlancer.postgres.oracle.ext.eet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import sqlancer.postgres.ast.PostgresAggregate;
import sqlancer.postgres.ast.PostgresBetweenOperation;
import sqlancer.postgres.ast.PostgresBinaryArithmeticOperation;
import sqlancer.postgres.ast.PostgresBinaryBitOperation;
import sqlancer.postgres.ast.PostgresBinaryComparisonOperation;
import sqlancer.postgres.ast.PostgresBinaryJsonOperation;
import sqlancer.postgres.ast.PostgresBinaryLogicalOperation;
import sqlancer.postgres.ast.PostgresBinaryRangeOperation;
import sqlancer.postgres.ast.PostgresCaseWhen;
import sqlancer.postgres.ast.PostgresCastOperation;
import sqlancer.postgres.ast.PostgresCollate;
import sqlancer.postgres.ast.PostgresConcatOperation;
import sqlancer.postgres.ast.PostgresColumnReference;
import sqlancer.postgres.ast.PostgresColumnValue;
import sqlancer.postgres.ast.PostgresConstant;
import sqlancer.postgres.ast.PostgresCteTableReference;
import sqlancer.postgres.ast.PostgresExists;
import sqlancer.postgres.ast.PostgresExpression;
import sqlancer.postgres.ast.PostgresFunction;
import sqlancer.postgres.ast.PostgresFunction.PostgresFunctionWithResult;
import sqlancer.postgres.ast.PostgresFunctionWithUnknownResult;
import sqlancer.postgres.ast.PostgresInOperation;
import sqlancer.postgres.ast.PostgresJsonContainOperation;
import sqlancer.postgres.ast.PostgresJoin;
import sqlancer.postgres.ast.PostgresLikeOperation;
import sqlancer.postgres.ast.PostgresLateralSubquery;
import sqlancer.postgres.ast.PostgresScalarSubquery;
import sqlancer.postgres.ast.PostgresSelect;
import sqlancer.postgres.ast.PostgresOrderByTerm;
import sqlancer.postgres.ast.PostgresPOSIXRegularExpression;
import sqlancer.postgres.ast.PostgresPostfixOperation;
import sqlancer.postgres.ast.PostgresPostfixText;
import sqlancer.postgres.ast.PostgresPrefixOperation;
import sqlancer.postgres.ast.PostgresPrintedExpression;
import sqlancer.postgres.ast.PostgresSimilarTo;
import sqlancer.postgres.ast.PostgresTableReference;
import sqlancer.postgres.ast.PostgresTemporalBinaryArithmeticOperation;
import sqlancer.postgres.ast.PostgresTemporalFunction;
import sqlancer.postgres.ast.PostgresText;
import sqlancer.postgres.ast.PostgresExceptSelect;
import sqlancer.postgres.ast.PostgresIntersectSelect;
import sqlancer.postgres.ast.PostgresUnionSelect;
import sqlancer.postgres.ast.PostgresWithSelect;
import sqlancer.postgres.ast.PostgresCteDefinition;
import sqlancer.postgres.ast.PostgresWindowFunction;
import sqlancer.postgres.ast.PostgresWindowFunction.WindowFrame;
import sqlancer.postgres.ast.PostgresWindowFunction.WindowSpecification;

/**
 * Structural map/copy/replace on Postgres AST (EET recursive traversal + reducer support).
 */
public final class PostgresEETExpressionTree {

    private PostgresEETExpressionTree() {
    }

    public static PostgresExpression copyAst(PostgresExpression e) {
        return mapChildren(PostgresEETExpressionTree::copyAst, e);
    }

    public static PostgresExpression replaceNode(PostgresExpression root, PostgresExpression target,
            PostgresExpression replacement) {
        if (root == target) {
            return replacement;
        }
        return mapChildren(c -> replaceNode(c, target, replacement), root);
    }

    public static List<PostgresExpression> collectDfs(PostgresExpression root) {
        List<PostgresExpression> out = new ArrayList<>();
        collectDfs(root, out);
        return out;
    }

    private static void collectDfs(PostgresExpression e, List<PostgresExpression> out) {
        if (e == null) {
            return;
        }
        out.add(e);
        forEachChild(e, c -> collectDfs(c, out));
    }

    public static PostgresExpression mapChildren(Function<PostgresExpression, PostgresExpression> mapChild,
            PostgresExpression e) {
        if (e == null) {
            return null;
        }
        if (e instanceof PostgresText || e instanceof PostgresTableReference || e instanceof PostgresCteTableReference) {
            return e;
        }
        if (e instanceof PostgresColumnReference || e instanceof PostgresColumnValue || e instanceof PostgresConstant) {
            return e;
        }
        if (e instanceof PostgresBinaryLogicalOperation) {
            PostgresBinaryLogicalOperation b = (PostgresBinaryLogicalOperation) e;
            return new PostgresBinaryLogicalOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()), b.getOp());
        }
        if (e instanceof PostgresBinaryComparisonOperation) {
            PostgresBinaryComparisonOperation b = (PostgresBinaryComparisonOperation) e;
            return new PostgresBinaryComparisonOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof PostgresPrefixOperation) {
            PostgresPrefixOperation u = (PostgresPrefixOperation) e;
            return new PostgresPrefixOperation(mapChild.apply(u.getExpression()), u.getOperator());
        }
        if (e instanceof PostgresPostfixOperation) {
            PostgresPostfixOperation u = (PostgresPostfixOperation) e;
            return new PostgresPostfixOperation(mapChild.apply(u.getExpression()), u.getOperator());
        }
        if (e instanceof PostgresCaseWhen) {
            PostgresCaseWhen c = (PostgresCaseWhen) e;
            return new PostgresCaseWhen(mapChild.apply(c.getWhenExpr()), mapChild.apply(c.getThenExpr()),
                    mapChild.apply(c.getElseExpr()));
        }
        if (e instanceof PostgresBetweenOperation) {
            PostgresBetweenOperation b = (PostgresBetweenOperation) e;
            return new PostgresBetweenOperation(mapChild.apply(b.getExpr()), mapChild.apply(b.getLeft()),
                    mapChild.apply(b.getRight()), b.isSymmetric());
        }
        if (e instanceof PostgresPrintedExpression) {
            PostgresPrintedExpression p = (PostgresPrintedExpression) e;
            return new PostgresPrintedExpression(mapChild.apply(p.getOriginal()));
        }
        if (e instanceof PostgresCastOperation) {
            PostgresCastOperation c = (PostgresCastOperation) e;
            return new PostgresCastOperation(mapChild.apply(c.getExpression()), c.getCompoundType());
        }
        if (e instanceof PostgresInOperation) {
            PostgresInOperation i = (PostgresInOperation) e;
            List<PostgresExpression> list = new ArrayList<>();
            for (PostgresExpression x : i.getListElements()) {
                list.add(mapChild.apply(x));
            }
            return new PostgresInOperation(mapChild.apply(i.getExpr()), list, i.isTrue());
        }
        if (e instanceof PostgresLikeOperation) {
            PostgresLikeOperation l = (PostgresLikeOperation) e;
            return new PostgresLikeOperation(mapChild.apply(l.getLeft()), mapChild.apply(l.getRight()));
        }
        if (e instanceof PostgresSimilarTo) {
            PostgresSimilarTo s = (PostgresSimilarTo) e;
            return new PostgresSimilarTo(mapChild.apply(s.getString()), mapChild.apply(s.getSimilarTo()),
                    mapChild.apply(s.getEscapeCharacter()));
        }
        if (e instanceof PostgresPOSIXRegularExpression) {
            PostgresPOSIXRegularExpression r = (PostgresPOSIXRegularExpression) e;
            return new PostgresPOSIXRegularExpression(mapChild.apply(r.getString()), mapChild.apply(r.getRegex()),
                    r.getOp());
        }
        if (e instanceof PostgresCollate) {
            PostgresCollate c = (PostgresCollate) e;
            return new PostgresCollate(mapChild.apply(c.getExpr()), c.getCollate());
        }
        if (e instanceof PostgresExists) {
            PostgresExists ex = (PostgresExists) e;
            return new PostgresExists(mapChild.apply(ex.getSubquery()));
        }
        if (e instanceof PostgresScalarSubquery) {
            PostgresScalarSubquery ss = (PostgresScalarSubquery) e;
            return new PostgresScalarSubquery((PostgresSelect) mapChild.apply(ss.getSubquery()));
        }
        if (e instanceof PostgresLateralSubquery) {
            PostgresLateralSubquery ls = (PostgresLateralSubquery) e;
            return new PostgresLateralSubquery(mapChild.apply(ls.getSubquery()));
        }
        if (e instanceof PostgresBinaryArithmeticOperation) {
            PostgresBinaryArithmeticOperation b = (PostgresBinaryArithmeticOperation) e;
            return new PostgresBinaryArithmeticOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp());
        }
        if (e instanceof PostgresBinaryBitOperation) {
            PostgresBinaryBitOperation b = (PostgresBinaryBitOperation) e;
            return new PostgresBinaryBitOperation(b.getOp(), mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()));
        }
        if (e instanceof PostgresConcatOperation) {
            PostgresConcatOperation c = (PostgresConcatOperation) e;
            return new PostgresConcatOperation(mapChild.apply(c.getLeft()), mapChild.apply(c.getRight()));
        }
        if (e instanceof PostgresBinaryRangeOperation) {
            PostgresBinaryRangeOperation b = (PostgresBinaryRangeOperation) e;
            String opStr = b.getOperatorRepresentation();
            PostgresBinaryRangeOperation.PostgresBinaryRangeComparisonOperator cmpOp = null;
            for (PostgresBinaryRangeOperation.PostgresBinaryRangeComparisonOperator c : PostgresBinaryRangeOperation.PostgresBinaryRangeComparisonOperator
                    .values()) {
                if (c.getTextRepresentation().equals(opStr)) {
                    cmpOp = c;
                    break;
                }
            }
            if (cmpOp != null) {
                return new PostgresBinaryRangeOperation(cmpOp, mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()));
            }
            PostgresBinaryRangeOperation.PostgresBinaryRangeOperator rangeOp = null;
            for (PostgresBinaryRangeOperation.PostgresBinaryRangeOperator r : PostgresBinaryRangeOperation.PostgresBinaryRangeOperator
                    .values()) {
                if (r.getTextRepresentation().equals(opStr)) {
                    rangeOp = r;
                    break;
                }
            }
            if (rangeOp != null) {
                return new PostgresBinaryRangeOperation(rangeOp, mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()));
            }
            return e;
        }
        if (e instanceof PostgresBinaryJsonOperation) {
            PostgresBinaryJsonOperation b = (PostgresBinaryJsonOperation) e;
            return new PostgresBinaryJsonOperation(b.getOp(), mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()));
        }
        if (e instanceof PostgresJsonContainOperation) {
            PostgresJsonContainOperation b = (PostgresJsonContainOperation) e;
            return new PostgresJsonContainOperation(b.getOp(), mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()));
        }
        if (e instanceof PostgresTemporalBinaryArithmeticOperation) {
            PostgresTemporalBinaryArithmeticOperation b = (PostgresTemporalBinaryArithmeticOperation) e;
            return new PostgresTemporalBinaryArithmeticOperation(mapChild.apply(b.getLeft()), mapChild.apply(b.getRight()),
                    b.getOp(), b.getExpressionType());
        }
        if (e instanceof PostgresAggregate) {
            PostgresAggregate a = (PostgresAggregate) e;
            List<PostgresExpression> args = new ArrayList<>();
            for (PostgresExpression x : a.getArgs()) {
                args.add(mapChild.apply(x));
            }
            return new PostgresAggregate(args, a.getFunction());
        }
        if (e instanceof PostgresFunction) {
            PostgresFunction f = (PostgresFunction) e;
            PostgresExpression[] origArgs = f.getArguments();
            PostgresExpression[] mappedArgs = new PostgresExpression[origArgs.length];
            for (int i = 0; i < origArgs.length; i++) {
                mappedArgs[i] = mapChild.apply(origArgs[i]);
            }
            PostgresFunctionWithResult knownResult = f.getFunctionWithKnownResult();
            if (knownResult != null) {
                return new PostgresFunction(knownResult, f.getExpressionType(), mappedArgs);
            } else {
                PostgresFunctionWithUnknownResult unknownFunc = findUnknownFuncByName(f.getFunctionName());
                return new PostgresFunction(unknownFunc, f.getExpressionType(), mappedArgs);
            }
        }
        if (e instanceof PostgresTemporalFunction) {
            PostgresTemporalFunction t = (PostgresTemporalFunction) e;
            PostgresExpression[] origArgs = t.getArguments();
            PostgresExpression[] mappedArgs = new PostgresExpression[origArgs.length];
            for (int i = 0; i < origArgs.length; i++) {
                mappedArgs[i] = mapChild.apply(origArgs[i]);
            }
            return new PostgresTemporalFunction(t.getKind(), t.getExpressionType(), t.getModifier(),
                    true, mappedArgs);
        }
        if (e instanceof PostgresPostfixText) {
            PostgresPostfixText p = (PostgresPostfixText) e;
            return new PostgresPostfixText(mapChild.apply(p.getExpr()), p.getText(), p.getExpectedValue(),
                    p.getExpressionCompoundType());
        }
        if (e instanceof PostgresOrderByTerm) {
            PostgresOrderByTerm o = (PostgresOrderByTerm) e;
            return new PostgresOrderByTerm(mapChild.apply(o.getExpr()), o.isAscending());
        }
        if (e instanceof PostgresWindowFunction) {
            PostgresWindowFunction w = (PostgresWindowFunction) e;
            List<PostgresExpression> mappedArgs = new ArrayList<>();
            for (PostgresExpression arg : w.getArguments()) {
                mappedArgs.add(mapChild.apply(arg));
            }
            WindowSpecification origSpec = w.getWindowSpec();
            List<PostgresExpression> mappedPartitionBy = new ArrayList<>();
            if (origSpec.getPartitionBy() != null) {
                for (PostgresExpression pb : origSpec.getPartitionBy()) {
                    mappedPartitionBy.add(mapChild.apply(pb));
                }
            }
            List<PostgresOrderByTerm> mappedOrderBy = new ArrayList<>();
            if (origSpec.getOrderBy() != null) {
                for (PostgresOrderByTerm ob : origSpec.getOrderBy()) {
                    mappedOrderBy.add((PostgresOrderByTerm) mapChild.apply(ob));
                }
            }
            WindowFrame mappedFrame = null;
            if (origSpec.getFrame() != null) {
                PostgresExpression mappedStart = origSpec.getFrame().getStartExpr() != null
                        ? mapChild.apply(origSpec.getFrame().getStartExpr()) : null;
                PostgresExpression mappedEnd = origSpec.getFrame().getEndExpr() != null
                        ? mapChild.apply(origSpec.getFrame().getEndExpr()) : null;
                mappedFrame = new WindowFrame(origSpec.getFrame().getType(), mappedStart, mappedEnd);
            }
            WindowSpecification mappedSpec = new WindowSpecification(mappedPartitionBy, mappedOrderBy, mappedFrame);
            return new PostgresWindowFunction(w.getFunctionName(), mappedArgs, mappedSpec, w.getExpressionType());
        }
        if (e instanceof PostgresJoin) {
            PostgresJoin j = (PostgresJoin) e;
            PostgresExpression mappedLeft = j.getLeftTable() != null ? mapChild.apply(j.getLeftTable()) : null;
            PostgresExpression mappedRight = j.getRightTable() != null ? mapChild.apply(j.getRightTable()) : null;
            PostgresExpression mappedOn = j.getOnClause() != null ? mapChild.apply(j.getOnClause()) : null;
            PostgresExpression mappedTableRef = j.getTableReference() != null ? mapChild.apply(j.getTableReference()) : null;
            PostgresJoin newJoin = new PostgresJoin(mappedTableRef, mappedOn, j.getType());
            if (mappedLeft != null && mappedRight != null) {
                newJoin = PostgresJoin.createJoin(mappedLeft, mappedRight, j.getType(), mappedOn);
            }
            return newJoin;
        }
        // Query-level nodes: recurse into branches, node itself not wrapped
        if (e instanceof PostgresUnionSelect) {
            PostgresUnionSelect u = (PostgresUnionSelect) e;
            List<PostgresSelect> newSelects = new ArrayList<>();
            for (PostgresSelect s : u.getSelects()) {
                newSelects.add((PostgresSelect) mapChild.apply(s));
            }
            return new PostgresUnionSelect(newSelects, u.isUnionAll());
        }
        if (e instanceof PostgresIntersectSelect) {
            PostgresIntersectSelect i = (PostgresIntersectSelect) e;
            List<PostgresSelect> newSelects = new ArrayList<>();
            for (PostgresSelect s : i.getSelects()) {
                newSelects.add((PostgresSelect) mapChild.apply(s));
            }
            return new PostgresIntersectSelect(newSelects, i.isIntersectAll());
        }
        if (e instanceof PostgresExceptSelect) {
            PostgresExceptSelect ex = (PostgresExceptSelect) e;
            List<PostgresSelect> newSelects = new ArrayList<>();
            for (PostgresSelect s : ex.getSelects()) {
                newSelects.add((PostgresSelect) mapChild.apply(s));
            }
            return new PostgresExceptSelect(newSelects, ex.isExceptAll());
        }
        if (e instanceof PostgresSelect) {
            PostgresSelect s = (PostgresSelect) e;
            List<PostgresExpression> newFetch = new ArrayList<>();
            for (PostgresExpression col : s.getFetchColumns()) {
                newFetch.add(mapChild.apply(col));
            }
            PostgresExpression newWhere = s.getWhereClause() == null ? null : mapChild.apply(s.getWhereClause());
            PostgresExpression newHaving = s.getHavingClause() == null ? null : mapChild.apply(s.getHavingClause());
            List<PostgresExpression> newGroupBy = new ArrayList<>();
            if (s.getGroupByExpressions() != null) {
                for (PostgresExpression gb : s.getGroupByExpressions()) {
                    newGroupBy.add(mapChild.apply(gb));
                }
            }
            PostgresSelect copy = new PostgresSelect();
            copy.setSelectOption(s.getSelectOption());
            copy.setFetchColumns(newFetch);
            copy.setFromList(new ArrayList<>(s.getFromList()));
            copy.setJoinClauses(new ArrayList<>(s.getJoinClauses()));
            copy.setWhereClause(newWhere);
            copy.setGroupByExpressions(newGroupBy);
            copy.setHavingClause(newHaving);
            copy.setOrderByClauses(new ArrayList<>(s.getOrderByClauses()));
            copy.setLimitClause(s.getLimitClause());
            copy.setOffsetClause(s.getOffsetClause());
            return copy;
        }
        if (e instanceof PostgresWithSelect) {
            PostgresWithSelect w = (PostgresWithSelect) e;
            List<PostgresCteDefinition> newCtes = new ArrayList<>();
            for (PostgresCteDefinition c : w.getCtes()) {
                newCtes.add(new PostgresCteDefinition(c.getName(), (PostgresSelect) mapChild.apply(c.getSelect())));
            }
            return new PostgresWithSelect(newCtes, (PostgresSelect) mapChild.apply(w.getMainSelect()));
        }
        return e;
    }

    private static void forEachChild(PostgresExpression e, java.util.function.Consumer<PostgresExpression> sink) {
        if (e == null) {
            return;
        }
        if (e instanceof PostgresBinaryLogicalOperation) {
            PostgresBinaryLogicalOperation b = (PostgresBinaryLogicalOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresBinaryComparisonOperation) {
            PostgresBinaryComparisonOperation b = (PostgresBinaryComparisonOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresPrefixOperation) {
            sink.accept(((PostgresPrefixOperation) e).getExpression());
        } else if (e instanceof PostgresPostfixOperation) {
            sink.accept(((PostgresPostfixOperation) e).getExpression());
        } else if (e instanceof PostgresCaseWhen) {
            PostgresCaseWhen c = (PostgresCaseWhen) e;
            sink.accept(c.getWhenExpr());
            sink.accept(c.getThenExpr());
            sink.accept(c.getElseExpr());
        } else if (e instanceof PostgresBetweenOperation) {
            PostgresBetweenOperation b = (PostgresBetweenOperation) e;
            sink.accept(b.getExpr());
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresPrintedExpression) {
            sink.accept(((PostgresPrintedExpression) e).getOriginal());
        } else if (e instanceof PostgresCastOperation) {
            sink.accept(((PostgresCastOperation) e).getExpression());
        } else if (e instanceof PostgresInOperation) {
            PostgresInOperation i = (PostgresInOperation) e;
            sink.accept(i.getExpr());
            for (PostgresExpression x : i.getListElements()) {
                sink.accept(x);
            }
        } else if (e instanceof PostgresLikeOperation) {
            PostgresLikeOperation l = (PostgresLikeOperation) e;
            sink.accept(l.getLeft());
            sink.accept(l.getRight());
        } else if (e instanceof PostgresSimilarTo) {
            PostgresSimilarTo s = (PostgresSimilarTo) e;
            sink.accept(s.getString());
            sink.accept(s.getSimilarTo());
            sink.accept(s.getEscapeCharacter());
        } else if (e instanceof PostgresPOSIXRegularExpression) {
            PostgresPOSIXRegularExpression r = (PostgresPOSIXRegularExpression) e;
            sink.accept(r.getString());
            sink.accept(r.getRegex());
        } else if (e instanceof PostgresCollate) {
            sink.accept(((PostgresCollate) e).getExpr());
        } else if (e instanceof PostgresExists) {
            sink.accept(((PostgresExists) e).getSubquery());
        } else if (e instanceof PostgresScalarSubquery) {
            sink.accept(((PostgresScalarSubquery) e).getSubquery());
        } else if (e instanceof PostgresLateralSubquery) {
            sink.accept(((PostgresLateralSubquery) e).getSubquery());
        } else if (e instanceof PostgresBinaryArithmeticOperation) {
            PostgresBinaryArithmeticOperation b = (PostgresBinaryArithmeticOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresBinaryBitOperation) {
            PostgresBinaryBitOperation b = (PostgresBinaryBitOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresConcatOperation) {
            PostgresConcatOperation c = (PostgresConcatOperation) e;
            sink.accept(c.getLeft());
            sink.accept(c.getRight());
        } else if (e instanceof PostgresBinaryRangeOperation) {
            PostgresBinaryRangeOperation b = (PostgresBinaryRangeOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresBinaryJsonOperation) {
            PostgresBinaryJsonOperation b = (PostgresBinaryJsonOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresJsonContainOperation) {
            PostgresJsonContainOperation b = (PostgresJsonContainOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresTemporalBinaryArithmeticOperation) {
            PostgresTemporalBinaryArithmeticOperation b = (PostgresTemporalBinaryArithmeticOperation) e;
            sink.accept(b.getLeft());
            sink.accept(b.getRight());
        } else if (e instanceof PostgresAggregate) {
            for (PostgresExpression x : ((PostgresAggregate) e).getArgs()) {
                sink.accept(x);
            }
        } else if (e instanceof PostgresFunction) {
            for (PostgresExpression x : ((PostgresFunction) e).getArguments()) {
                sink.accept(x);
            }
        } else if (e instanceof PostgresTemporalFunction) {
            for (PostgresExpression x : ((PostgresTemporalFunction) e).getArguments()) {
                sink.accept(x);
            }
        } else if (e instanceof PostgresPostfixText) {
            sink.accept(((PostgresPostfixText) e).getExpr());
        } else if (e instanceof PostgresOrderByTerm) {
            sink.accept(((PostgresOrderByTerm) e).getExpr());
        } else if (e instanceof PostgresWindowFunction) {
            PostgresWindowFunction w = (PostgresWindowFunction) e;
            for (PostgresExpression arg : w.getArguments()) {
                sink.accept(arg);
            }
            WindowSpecification spec = w.getWindowSpec();
            if (spec != null) {
                if (spec.getPartitionBy() != null) {
                    for (PostgresExpression pb : spec.getPartitionBy()) {
                        sink.accept(pb);
                    }
                }
                if (spec.getOrderBy() != null) {
                    for (PostgresOrderByTerm ob : spec.getOrderBy()) {
                        sink.accept(ob.getExpr());
                    }
                }
                if (spec.getFrame() != null) {
                    if (spec.getFrame().getStartExpr() != null) {
                        sink.accept(spec.getFrame().getStartExpr());
                    }
                    if (spec.getFrame().getEndExpr() != null) {
                        sink.accept(spec.getFrame().getEndExpr());
                    }
                }
            }
        } else if (e instanceof PostgresJoin) {
            PostgresJoin j = (PostgresJoin) e;
            if (j.getLeftTable() != null) {
                sink.accept(j.getLeftTable());
            }
            if (j.getRightTable() != null) {
                sink.accept(j.getRightTable());
            }
            if (j.getOnClause() != null) {
                sink.accept(j.getOnClause());
            }
            if (j.getTableReference() != null) {
                sink.accept(j.getTableReference());
            }
        } else if (e instanceof PostgresUnionSelect) {
            for (PostgresSelect s : ((PostgresUnionSelect) e).getSelects()) {
                sink.accept(s);
            }
        } else if (e instanceof PostgresIntersectSelect) {
            for (PostgresSelect s : ((PostgresIntersectSelect) e).getSelects()) {
                sink.accept(s);
            }
        } else if (e instanceof PostgresExceptSelect) {
            for (PostgresSelect s : ((PostgresExceptSelect) e).getSelects()) {
                sink.accept(s);
            }
        } else if (e instanceof PostgresSelect) {
            PostgresSelect s = (PostgresSelect) e;
            for (PostgresExpression col : s.getFetchColumns()) {
                sink.accept(col);
            }
            if (s.getWhereClause() != null) {
                sink.accept(s.getWhereClause());
            }
            if (s.getHavingClause() != null) {
                sink.accept(s.getHavingClause());
            }
            if (s.getGroupByExpressions() != null) {
                for (PostgresExpression gb : s.getGroupByExpressions()) {
                    sink.accept(gb);
                }
            }
        } else if (e instanceof PostgresWithSelect) {
            PostgresWithSelect w = (PostgresWithSelect) e;
            for (PostgresCteDefinition c : w.getCtes()) {
                sink.accept(c.getSelect());
            }
            sink.accept(w.getMainSelect());
        }
    }

    public static boolean isEetReductionLeaf(PostgresExpression e) {
        return e instanceof PostgresText || e instanceof PostgresTableReference || e instanceof PostgresCteTableReference
                || e instanceof PostgresColumnReference || e instanceof PostgresColumnValue || e instanceof PostgresConstant;
    }

    private static PostgresFunctionWithUnknownResult findUnknownFuncByName(String name) {
        for (PostgresFunctionWithUnknownResult f : PostgresFunctionWithUnknownResult.values()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }
}

