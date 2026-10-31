# MySQL Bug Report Draft: JSON comparison with function-wrapped zero TIMESTAMP violates three-valued logic

> 状态：草稿（待提交 bugs.mysql.com / Oracle 支持）
> 发现方式：SQLancer TLP oracle（docs/oracle-findings-triage-2026-10-29.md，MYSQL-BUG-1）
> 验证环境：MySQL 8.4.8 (Windows), `sql_mode = ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION`

## Synopsis

The same predicate expression evaluates to contradictory truth values depending on the
evaluation context: projected directly it yields `NULL`, but wrapped in `IS NULL` it
yields `FALSE`. This violates SQL three-valued logic (if `p` is NULL, `p IS NULL` must
be TRUE). It occurs when a JSON value is compared against a function-wrapped
zero-datetime (`LEAST()`/`GREATEST()` over a zero TIMESTAMP column).

## How to repeat

```sql
CREATE TABLE t (c0 TIMESTAMP NULL);
INSERT IGNORE INTO t VALUES ('0000-00-00 00:00:00');  -- zero date stored via IGNORE

SELECT
    (JSON_ARRAY()) IN (LEAST(c0, c0))       AS p,      -- NULL
    ((JSON_ARRAY()) IN (LEAST(c0, c0))) IS NULL        -- 0  (contradiction)
FROM t;
```

Observed:

```
p      = NULL
isnull = 0
```

Expected:

```
p      = NULL
isnull = 1
```

(The same contradiction reproduces with `=` instead of `IN (...)`, and with
`GREATEST(c0, c0)` instead of `LEAST`.)

## Boundary matrix (additional info)

| Expression | Direct evaluation | Wrapped in `IS NULL` |
|---|---|---|
| `JSON_ARRAY() IN (c0)` (bare column) | 0 | 0 — consistent |
| `JSON_ARRAY() IN (LEAST(c0,c0))` | **NULL** | **0 — contradiction** |
| `JSON_ARRAY() IN (GREATEST(c0,c0))` | **NULL** | **0 — contradiction** |
| `JSON_ARRAY() IN (CAST(c0 AS CHAR))` | 0 | 0 — consistent |
| `JSON_ARRAY() IN ('0000-00-00 00:00:00')` (string literal) | 0 | 0 — consistent |

Two distinct anomalies:

1. A function-wrapped typed datetime (`LEAST`/`GREATEST` result) compares differently
   from the semantically identical bare column value (NULL vs 0) — the JSON coercion
   path appears to differ between the two.
2. The `IS NULL` wrapper contradicts the NULL result of the same predicate — the
   wrapper's evaluation path appears to treat the coercion failure as FALSE instead
   of propagating NULL.

## Impact

Any metamorphic or partitioning query analysis that splits rows by
`p / NOT p / p IS NULL` silently loses rows that evaluate to this predicate shape,
because the row falls into none of the three branches. Row counts diverge
(10 vs 9 in our reproducer) with no error raised.

## Root-cause hypothesis (for the bug triager)

The JSON-vs-non-JSON comparison of a function-derived temporal value likely takes a
coercion path that reports failure-as-NULL in the direct projection, while the
`IS NULL` evaluation short-circuits on an operand type check (JSON and datetime are
both non-nullable types) without executing the comparison.
