package sqlancer.mysql.transaction;

import sqlancer.common.transaction.IsolationLevel;

public class MySQLIsolation {

    public enum MySQLIsolationLevel implements IsolationLevel {

        READ_UNCOMMITTED("READ UNCOMMITTED"),
        READ_COMMITTED("READ COMMITTED"),
        REPEATABLE_READ("REPEATABLE READ"),
        SERIALIZABLE("SERIALIZABLE");

        private final String name;

        MySQLIsolationLevel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}