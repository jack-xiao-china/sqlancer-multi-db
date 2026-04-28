package sqlancer.gaussdbm.ast;

import sqlancer.LikeImplementationHelper;
import sqlancer.Randomly;

public class GaussDBBinaryComparisonOperation implements GaussDBExpression {

    public enum BinaryComparisonOperator {
        EQUALS("=") {
            @Override
            public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
                return l.sqlEquals(r);
            }
        },
        NOT_EQUALS("<>") {
            @Override
            public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
                GaussDBConstant eq = l.sqlEquals(r);
                if (eq.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                return GaussDBConstant.createBooleanConstant(!eq.asBooleanNotNull());
            }
        },
        GREATER(">") {
            @Override
            public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
                GaussDBConstant eq = l.sqlEquals(r);
                if (!eq.isNull() && eq.asBooleanNotNull()) {
                    return GaussDBConstant.createBooleanConstant(false);
                }
                GaussDBConstant lt = l.sqlLessThan(r);
                if (lt.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                return GaussDBConstant.createBooleanConstant(!lt.asBooleanNotNull());
            }
        },
        GREATER_EQUALS(">=") {
            @Override
            public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
                GaussDBConstant eq = l.sqlEquals(r);
                if (!eq.isNull() && eq.asBooleanNotNull()) {
                    return GaussDBConstant.createBooleanConstant(true);
                }
                GaussDBConstant lt = l.sqlLessThan(r);
                if (lt.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                return GaussDBConstant.createBooleanConstant(!lt.asBooleanNotNull());
            }
        },
        SMALLER("<") {
            @Override
            public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
                return l.sqlLessThan(r);
            }
        },
        SMALLER_EQUALS("<=") {
            @Override
            public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
                GaussDBConstant lt = l.sqlLessThan(r);
                if (lt.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                if (lt.asBooleanNotNull()) {
                    return GaussDBConstant.createBooleanConstant(true);
                }
                return l.sqlEquals(r);
            }
        },
        LIKE("LIKE") {
            @Override
            public GaussDBConstant getExpectedValue(GaussDBConstant l, GaussDBConstant r) {
                if (l.isNull() || r.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                String leftStr;
                String rightStr;
                if (l instanceof GaussDBConstant.GaussDBStringConstant) {
                    leftStr = ((GaussDBConstant.GaussDBStringConstant) l).getValue();
                } else {
                    leftStr = l.getTextRepresentation();
                }
                if (r instanceof GaussDBConstant.GaussDBStringConstant) {
                    rightStr = ((GaussDBConstant.GaussDBStringConstant) r).getValue();
                } else {
                    rightStr = r.getTextRepresentation();
                }
                boolean matches = LikeImplementationHelper.match(leftStr, rightStr, 0, 0, false);
                return GaussDBConstant.createBooleanConstant(matches);
            }
        };

        private final String textRepr;

        BinaryComparisonOperator(String textRepr) {
            this.textRepr = textRepr;
        }

        public String getTextRepr() {
            return textRepr;
        }

        public abstract GaussDBConstant getExpectedValue(GaussDBConstant leftVal, GaussDBConstant rightVal);

        public static BinaryComparisonOperator getRandom() {
            return Randomly.fromOptions(values());
        }
    }

    private final GaussDBExpression left;
    private final GaussDBExpression right;
    private final BinaryComparisonOperator op;

    public GaussDBBinaryComparisonOperation(GaussDBExpression left, GaussDBExpression right,
            BinaryComparisonOperator op) {
        this.left = left;
        this.right = right;
        this.op = op;
    }

    public GaussDBExpression getLeft() {
        return left;
    }

    public GaussDBExpression getRight() {
        return right;
    }

    public BinaryComparisonOperator getOp() {
        return op;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        return op.getExpectedValue(left.getExpectedValue(), right.getExpectedValue());
    }
}
