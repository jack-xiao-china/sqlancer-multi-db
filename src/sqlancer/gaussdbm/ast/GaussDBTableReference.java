package sqlancer.gaussdbm.ast;

import sqlancer.gaussdbm.GaussDBMSchema.GaussDBTable;

public class GaussDBTableReference implements GaussDBExpression {

    private final GaussDBTable table;

    public GaussDBTableReference(GaussDBTable table) {
        this.table = table;
    }

    public static GaussDBTableReference create(GaussDBTable table) {
        return new GaussDBTableReference(table);
    }

    public GaussDBTable getTable() {
        return table;
    }
}
