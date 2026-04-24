package sqlancer.postgres.gen;

import sqlancer.Randomly;
import sqlancer.common.query.ExpectedErrors;
import sqlancer.common.query.SQLQueryAdapter;
import sqlancer.postgres.PostgresGlobalState;

public final class PostgresSequenceGenerator {

    private PostgresSequenceGenerator() {
    }

    public static SQLQueryAdapter createSequence(PostgresGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        StringBuilder sb = new StringBuilder("CREATE");
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("TEMPORARY", "TEMP"));
        }
        sb.append(" SEQUENCE");
        // TODO keep track of sequences
        sb.append(" IF NOT EXISTS");
        // TODO generate sequence names
        sb.append(" seq");
        if (Randomly.getBoolean()) {
            sb.append(" AS ");
            sb.append(Randomly.fromOptions("smallint", "integer", "bigint"));
        }
        if (Randomly.getBoolean()) {
            sb.append(" INCREMENT");
            if (Randomly.getBoolean()) {
                sb.append(" BY");
            }
            sb.append(" ");
            sb.append(globalState.getRandomly().getInteger());
            errors.add("INCREMENT must not be zero");
        }
        if (Randomly.getBoolean()) {
            if (Randomly.getBoolean()) {
                sb.append(" MINVALUE");
                sb.append(" ");
                sb.append(globalState.getRandomly().getInteger());
            } else {
                sb.append(" NO MINVALUE");
            }
            errors.add("must be less than MAXVALUE");
        }
        if (Randomly.getBoolean()) {
            if (Randomly.getBoolean()) {
                sb.append(" MAXVALUE");
                sb.append(" ");
                sb.append(globalState.getRandomly().getInteger());
            } else {
                sb.append(" NO MAXVALUE");
            }
            errors.add("must be less than MAXVALUE");
        }
        if (Randomly.getBoolean()) {
            sb.append(" START");
            if (Randomly.getBoolean()) {
                sb.append(" WITH");
            }
            sb.append(" ");
            sb.append(globalState.getRandomly().getInteger());
            errors.add("cannot be less than MINVALUE");
            errors.add("cannot be greater than MAXVALUE");
        }
        if (Randomly.getBoolean()) {
            sb.append(" CACHE ");
            sb.append(globalState.getRandomly().getPositiveIntegerNotNull());
        }
        errors.add("is out of range");
        if (Randomly.getBoolean()) {
            if (Randomly.getBoolean()) {
                sb.append(" NO");
            }
            sb.append(" CYCLE");
        }
        if (Randomly.getBoolean()) {
            sb.append(" OWNED BY ");
            // if (Randomly.getBoolean()) {
            sb.append("NONE");
            // } else {
            // sb.append(s.getRandomTable().getRandomColumn().getFullQualifiedName());
            // }
        }
        return new SQLQueryAdapter(sb.toString(), errors);
    }

    public static SQLQueryAdapter dropSequence(PostgresGlobalState globalState) {
        StringBuilder sb = new StringBuilder("DROP SEQUENCE ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }
        sb.append("seq");
        if (Randomly.getBoolean()) {
            sb.append(" ");
            sb.append(Randomly.fromOptions("CASCADE", "RESTRICT"));
        }
        return new SQLQueryAdapter(sb.toString(),
                ExpectedErrors.from("does not exist",
                        "cannot drop desired object(s) because other objects depend on them", "is not a sequence",
                        "because other objects depend on it"),
                true);
    }

    public static SQLQueryAdapter alterSequence(PostgresGlobalState globalState) {
        ExpectedErrors errors = new ExpectedErrors();
        StringBuilder sb = new StringBuilder("ALTER SEQUENCE ");
        if (Randomly.getBoolean()) {
            sb.append("IF EXISTS ");
        }
        sb.append("seq");
        switch (Randomly.fromOptions("INCREMENT", "MINVALUE", "MAXVALUE", "START", "RESTART", "CACHE", "CYCLE",
                "OWNED_BY")) {
        case "INCREMENT":
            sb.append(" INCREMENT");
            if (Randomly.getBoolean()) {
                sb.append(" BY");
            }
            sb.append(" ");
            sb.append(globalState.getRandomly().getInteger());
            errors.add("INCREMENT must not be zero");
            break;
        case "MINVALUE":
            if (Randomly.getBoolean()) {
                sb.append(" MINVALUE ");
                sb.append(globalState.getRandomly().getInteger());
            } else {
                sb.append(" NO MINVALUE");
            }
            errors.add("must be less than MAXVALUE");
            break;
        case "MAXVALUE":
            if (Randomly.getBoolean()) {
                sb.append(" MAXVALUE ");
                sb.append(globalState.getRandomly().getInteger());
            } else {
                sb.append(" NO MAXVALUE");
            }
            errors.add("must be greater than MINVALUE");
            break;
        case "START":
            sb.append(" START");
            if (Randomly.getBoolean()) {
                sb.append(" WITH");
            }
            sb.append(" ");
            sb.append(globalState.getRandomly().getInteger());
            errors.add("cannot be less than MINVALUE");
            errors.add("cannot be greater than MAXVALUE");
            break;
        case "RESTART":
            sb.append(" RESTART");
            if (Randomly.getBoolean()) {
                sb.append(" WITH ");
                sb.append(globalState.getRandomly().getInteger());
            }
            errors.add("cannot be less than MINVALUE");
            errors.add("cannot be greater than MAXVALUE");
            break;
        case "CACHE":
            sb.append(" CACHE ");
            sb.append(globalState.getRandomly().getPositiveIntegerNotNull());
            break;
        case "CYCLE":
            if (Randomly.getBoolean()) {
                sb.append(" NO");
            }
            sb.append(" CYCLE");
            break;
        case "OWNED_BY":
            sb.append(" OWNED BY NONE");
            break;
        default:
            throw new AssertionError();
        }
        errors.add("does not exist");
        errors.add("is not a sequence");
        errors.add("is out of range");
        return new SQLQueryAdapter(sb.toString(), errors, true);
    }

}
