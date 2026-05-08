package sqlancer.postgres.transaction;

import sqlancer.common.transaction.IsolationLevel;

public class PostgresIsolation {

    public enum PostgresIsolationLevel implements IsolationLevel {

        READ_COMMITTED("READ COMMITTED"),
        REPEATABLE_READ("REPEATABLE READ"),
        SERIALIZABLE("SERIALIZABLE");

        private final String name;

        PostgresIsolationLevel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}