package sqlancer.gaussdbm.transaction;

import sqlancer.common.transaction.IsolationLevel;

public class GaussDBMIsolation {

    public enum GaussDBMIsolationLevel implements IsolationLevel {
        READ_UNCOMMITTED("READ UNCOMMITTED"),
        READ_COMMITTED("READ COMMITTED"),
        REPEATABLE_READ("REPEATABLE READ"),
        SERIALIZABLE("SERIALIZABLE");

        private final String name;

        GaussDBMIsolationLevel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}