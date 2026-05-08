package sqlancer.gaussdba.transaction;

import sqlancer.common.transaction.IsolationLevel;

public class GaussDBAIsolation {

    public enum GaussDBAIsolationLevel implements IsolationLevel {
        READ_COMMITTED("READ COMMITTED"),
        REPEATABLE_READ("REPEATABLE READ"),
        SERIALIZABLE("SERIALIZABLE");

        private final String name;

        GaussDBAIsolationLevel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}