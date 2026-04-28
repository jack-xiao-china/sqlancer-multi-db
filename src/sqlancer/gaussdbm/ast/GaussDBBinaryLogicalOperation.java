package sqlancer.gaussdbm.ast;

import sqlancer.Randomly;

public class GaussDBBinaryLogicalOperation implements GaussDBExpression {

    public enum GaussDBBinaryLogicalOperator {
        AND("AND") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (left.isNull() && right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                } else if (left.isNull()) {
                    if (right.asBooleanNotNull()) {
                        return GaussDBConstant.createNullConstant();
                    } else {
                        return GaussDBConstant.createBooleanConstant(false);
                    }
                } else if (right.isNull()) {
                    if (left.asBooleanNotNull()) {
                        return GaussDBConstant.createNullConstant();
                    } else {
                        return GaussDBConstant.createBooleanConstant(false);
                    }
                } else {
                    return GaussDBConstant.createBooleanConstant(left.asBooleanNotNull() && right.asBooleanNotNull());
                }
            }
        },
        OR("OR") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (!left.isNull() && left.asBooleanNotNull()) {
                    return GaussDBConstant.createBooleanConstant(true);
                } else if (!right.isNull() && right.asBooleanNotNull()) {
                    return GaussDBConstant.createBooleanConstant(true);
                } else if (left.isNull() || right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                } else {
                    return GaussDBConstant.createBooleanConstant(false);
                }
            }
        },
        XOR("XOR") {
            @Override
            public GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right) {
                if (left.isNull() || right.isNull()) {
                    return GaussDBConstant.createNullConstant();
                }
                return GaussDBConstant.createBooleanConstant(left.asBooleanNotNull() ^ right.asBooleanNotNull());
            }
        };

        private final String textRepresentation;

        GaussDBBinaryLogicalOperator(String textRepresentation) {
            this.textRepresentation = textRepresentation;
        }

        public abstract GaussDBConstant apply(GaussDBConstant left, GaussDBConstant right);

        public static GaussDBBinaryLogicalOperator getRandom() {
            return Randomly.fromOptions(values());
        }

        public String getTextRepresentation() {
            return textRepresentation;
        }
    }

    private final GaussDBExpression left;
    private final GaussDBExpression right;
    private final GaussDBBinaryLogicalOperator op;

    public GaussDBBinaryLogicalOperation(GaussDBExpression left, GaussDBExpression right,
            GaussDBBinaryLogicalOperator op) {
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

    public GaussDBBinaryLogicalOperator getOp() {
        return op;
    }

    @Override
    public GaussDBConstant getExpectedValue() {
        return op.apply(left.getExpectedValue(), right.getExpectedValue());
    }
}
