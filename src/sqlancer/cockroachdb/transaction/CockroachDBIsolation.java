package sqlancer.cockroachdb.transaction;

import sqlancer.common.transaction.IsolationLevel;

public class CockroachDBIsolation {

    public enum CockroachDBIsolationLevel implements IsolationLevel {

        SERIALIZABLE("SERIALIZABLE");

        private final String name;

        CockroachDBIsolationLevel(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

}