package sqlancer.gaussdbm.oracle.eet;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;
import sqlancer.gaussdbm.GaussDBMSchema.GaussDBDataType;
import sqlancer.gaussdbm.ast.GaussDBBinaryComparisonOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation;
import sqlancer.gaussdbm.ast.GaussDBBinaryLogicalOperation.GaussDBBinaryLogicalOperator;
import sqlancer.gaussdbm.ast.GaussDBCaseWhen;
import sqlancer.gaussdbm.ast.GaussDBColumnReference;
import sqlancer.gaussdbm.ast.GaussDBConstant;
import sqlancer.gaussdbm.ast.GaussDBExpression;
import sqlancer.gaussdbm.ast.GaussDBPrintedExpression;
import sqlancer.gaussdbm.ast.GaussDBTableReference;
import sqlancer.gaussdbm.ast.GaussDBText;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPostfixOperation.UnaryPostfixOperator;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation;
import sqlancer.gaussdbm.ast.GaussDBUnaryPrefixOperation.UnaryPrefixOperator;
import sqlancer.gaussdbm.gen.GaussDBMExpressionGenerator;

/**
 * EET equivalent transformations on GaussDB AST nodes.
 */
public class GaussDBMEETTransformer {

    private static final int MAX_TRANSFORM_RETRIES = 200;

    private final GaussDBMExpressionGenerator gen;

    public GaussDBMEETTransformer(GaussDBMExpressionGenerator gen) {
        this.gen = gen;
    }

    public GaussDBExpression transformExpression(GaussDBExpression expr) {
        if (expr == null) {
            return null;
        }
        if (isRule7NoChange(expr)) {
            return expr;
        }
        GaussDBExpression exprRec = GaussDBMEETExpressionTree.mapChildren(this::transformExpression, expr);
        GaussDBExpression constHandled = tryConstBoolTransform(exprRec);
        if (constHandled != null) {
            return constHandled;
        }
        int attempts = 0;
        while (attempts++ < MAX_TRANSFORM_RETRIES) {
            try {
                if (isBooleanLike(exprRec)) {
                    if (Randomly.getBoolean()) {
                        return transformBoolExpr(exprRec);
                    } else {
                        return transformValueExpr(exprRec);
                    }
                } else {
                    return transformValueExpr(exprRec);
                }
            } catch (IgnoreMeException e) {
                // retry
            }
        }
        throw new IgnoreMeException();
    }

    private static boolean isRule7NoChange(GaussDBExpression expr) {
        return expr instanceof GaussDBTableReference || expr instanceof GaussDBText;
    }

    static boolean isBooleanLike(GaussDBExpression expr) {
        return expr instanceof GaussDBBinaryLogicalOperation || expr instanceof GaussDBBinaryComparisonOperation
                || expr instanceof GaussDBUnaryPrefixOperation || expr instanceof GaussDBUnaryPostfixOperation;
    }

    GaussDBExpression tryConstBoolTransform(GaussDBExpression expr) {
        if (!(expr instanceof GaussDBConstant)) {
            return null;
        }
        GaussDBConstant c = (GaussDBConstant) expr;
        if (c.isNull()) {
            return null;
        }
        if (!isZeroOrOneInt(c)) {
            return null;
        }
        GaussDBExpression extend;
        try {
            extend = gen.generateBooleanExpression();
        } catch (IgnoreMeException e) {
            return null;
        }
        if (c.asBooleanNotNull()) {
            return new GaussDBBinaryLogicalOperation(c, extend, GaussDBBinaryLogicalOperator.OR);
        } else {
            return new GaussDBBinaryLogicalOperation(c, extend, GaussDBBinaryLogicalOperator.AND);
        }
    }

    private static boolean isZeroOrOneInt(GaussDBConstant c) {
        if (!c.isInt()) {
            return false;
        }
        long v = c.asIntNotNull();
        return v == 0 || v == 1;
    }

    public GaussDBExpression transformBoolExpr(GaussDBExpression expr) {
        GaussDBExpression randomBool = gen.generateBooleanExpression();
        int choice = (int) Randomly.getNotCachedInteger(0, 6);
        boolean useTrueExpr = choice <= 2;
        GaussDBExpression notRand = new GaussDBUnaryPrefixOperation(randomBool, UnaryPrefixOperator.NOT);
        GaussDBExpression randIsNull = new GaussDBUnaryPostfixOperation(randomBool, UnaryPostfixOperator.IS_NULL);
        GaussDBExpression randIsNotNull = new GaussDBUnaryPostfixOperation(randomBool,
                UnaryPostfixOperator.IS_NOT_NULL);

        if (useTrueExpr) {
            GaussDBExpression part1 = new GaussDBBinaryLogicalOperation(randomBool, notRand,
                    GaussDBBinaryLogicalOperator.OR);
            GaussDBExpression base = new GaussDBBinaryLogicalOperation(part1, randIsNull,
                    GaussDBBinaryLogicalOperator.OR);
            return new GaussDBBinaryLogicalOperation(base, expr, GaussDBBinaryLogicalOperator.AND);
        } else {
            GaussDBExpression part1 = new GaussDBBinaryLogicalOperation(randomBool, notRand,
                    GaussDBBinaryLogicalOperator.AND);
            GaussDBExpression base = new GaussDBBinaryLogicalOperation(part1, randIsNotNull,
                    GaussDBBinaryLogicalOperator.AND);
            return new GaussDBBinaryLogicalOperation(base, expr, GaussDBBinaryLogicalOperator.OR);
        }
    }

    public GaussDBExpression transformValueExpr(GaussDBExpression expr) {
        GaussDBExpression randomBool = gen.generateBooleanExpression();
        int choice = (int) Randomly.getNotCachedInteger(0, 9);
        GaussDBExpression randVal = generateSameTypeAs(expr);

        if (choice <= 2) {
            GaussDBExpression trueExpr = buildTrueExpr(randomBool);
            return new GaussDBCaseWhen(trueExpr, expr, randVal);
        } else if (choice <= 5) {
            GaussDBExpression falseExpr = buildFalseExpr(randomBool);
            return new GaussDBCaseWhen(falseExpr, randVal, expr);
        } else {
            GaussDBExpression copy = copyExpr(expr);
            if (Randomly.getBoolean()) {
                return new GaussDBCaseWhen(randomBool, copy, expr);
            } else {
                return new GaussDBCaseWhen(randomBool, expr, copy);
            }
        }
    }

    public GaussDBExpression buildTrueExpr(GaussDBExpression randomBool) {
        GaussDBExpression notRand = new GaussDBUnaryPrefixOperation(randomBool, UnaryPrefixOperator.NOT);
        GaussDBExpression randIsNull = new GaussDBUnaryPostfixOperation(randomBool, UnaryPostfixOperator.IS_NULL);
        GaussDBExpression part1 = new GaussDBBinaryLogicalOperation(randomBool, notRand,
                GaussDBBinaryLogicalOperator.OR);
        return new GaussDBBinaryLogicalOperation(part1, randIsNull, GaussDBBinaryLogicalOperator.OR);
    }

    public GaussDBExpression buildFalseExpr(GaussDBExpression randomBool) {
        GaussDBExpression notRand = new GaussDBUnaryPrefixOperation(randomBool, UnaryPrefixOperator.NOT);
        GaussDBExpression randIsNotNull = new GaussDBUnaryPostfixOperation(randomBool,
                UnaryPostfixOperator.IS_NOT_NULL);
        GaussDBExpression part1 = new GaussDBBinaryLogicalOperation(randomBool, notRand,
                GaussDBBinaryLogicalOperator.AND);
        return new GaussDBBinaryLogicalOperation(part1, randIsNotNull, GaussDBBinaryLogicalOperator.AND);
    }

    public GaussDBExpression copyExpr(GaussDBExpression expr) {
        return new GaussDBPrintedExpression(expr);
    }

    private GaussDBExpression generateSameTypeAs(GaussDBExpression expr) {
        GaussDBDataType t = inferDataType(expr);
        if (t == null) {
            return gen.generateExpression();
        }
        for (int i = 0; i < MAX_TRANSFORM_RETRIES; i++) {
            try {
                GaussDBExpression e = gen.generateExpression();
                GaussDBDataType t2 = inferDataType(e);
                if (t2 != null && t2 == t) {
                    return e;
                }
            } catch (IgnoreMeException e) {
                // retry
            }
        }
        return sameTypeFallback(t);
    }

    private static GaussDBExpression sameTypeFallback(GaussDBDataType t) {
        switch (t) {
        case INT:
            return GaussDBConstant.createIntConstant(0);
        case VARCHAR:
            return GaussDBConstant.createStringConstant("eet");
        case FLOAT:
        case DOUBLE:
            return GaussDBConstant.createStringConstant("0e0");
        case DECIMAL:
            return GaussDBConstant.createIntConstant(0);
        case DATE:
            return GaussDBConstant.createStringConstant("2000-01-01");
        case TIME:
            return GaussDBConstant.createStringConstant("00:00:00");
        case DATETIME:
        case TIMESTAMP:
            return GaussDBConstant.createStringConstant("2000-01-01 00:00:00");
        case YEAR:
            return GaussDBConstant.createStringConstant("2000");
        default:
            throw new AssertionError(t);
        }
    }

    private static GaussDBDataType inferDataType(GaussDBExpression expr) {
        try {
            if (expr instanceof GaussDBColumnReference) {
                return ((GaussDBColumnReference) expr).getColumn().getType();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
