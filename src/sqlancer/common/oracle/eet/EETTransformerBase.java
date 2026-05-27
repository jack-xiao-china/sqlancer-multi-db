package sqlancer.common.oracle.eet;

import java.util.ArrayList;
import java.util.List;

import sqlancer.IgnoreMeException;
import sqlancer.Randomly;

/**
 * Shared EET transformation algorithm that delegates to a dialect-specific
 * {@link EETTransformAdapter} for AST node creation and type queries.
 *
 * The core algorithm follows the EET paper and native tool implementation:
 * 1. Check Rule 7 (no-change for scoping-critical nodes)
 * 2. Recurse into children via adapter.mapChildren
 * 3. Try constant boolean extension (ConstBoolTransform)
 * 4. If boolean-like: randomly choose BoolTransform or ValueTransform
 * 5. If value: apply ValueTransform
 * 6. Try semantic rewrite rules in registration order
 * 7. Retry on IgnoreMeException up to MAX_TRANSFORM_RETRIES
 * 8. Integrate impedance tracking for blacklisting high-failure-rate expression types
 */
public class EETTransformerBase<E> {

    private static final int MAX_TRANSFORM_RETRIES = 200;

    private final EETTransformAdapter<E> adapter;
    private final List<EETRule<E>> rules;
    private EETImpedanceTracker impedanceTracker;

    public EETTransformerBase(EETTransformAdapter<E> adapter) {
        this.adapter = adapter;
        this.rules = new ArrayList<>();
        // Default wrapping rules (always registered, matching EET native behavior)
        rules.add(new EETConstBoolTransformRule<>());
        rules.add(new EETBoolTransformRule<>());
        rules.add(new EETValueTransformRule<>());
    }

    /** Set the impedance tracker for recording success/failure rates. */
    public void setImpedanceTracker(EETImpedanceTracker tracker) {
        this.impedanceTracker = tracker;
    }

    /** Get the impedance tracker. */
    public EETImpedanceTracker getImpedanceTracker() {
        return impedanceTracker;
    }

    /** Register an additional transformation rule (e.g., semantic rewrite rules). */
    public void registerRule(EETRule<E> rule) {
        rules.add(rule);
    }

    /** Get the list of active rules. */
    public List<EETRule<E>> getRules() {
        return rules;
    }

    /** Get the adapter. */
    public EETTransformAdapter<E> getAdapter() {
        return adapter;
    }

    /**
     * Main entry point: transform an expression following the EET algorithm.
     * This replicates the exact logic from MySQLEETTransformer/PostgresEETTransformer.
     *
     * Integrates impedance tracking: checks blacklist before transforming,
     * records success/failure for expression types.
     */
    public E transformExpression(E expr) {
        if (expr == null) {
            return null;
        }
        if (adapter.isRule7NoChange(expr)) {
            return expr;
        }

        // Impedance tracking: check if expression type is blacklisted
        String typeName = adapter.getExpressionTypeName(expr);
        if (impedanceTracker != null && impedanceTracker.isBlacklisted(typeName)) {
            throw new IgnoreMeException();
        }

        // §3.2: recurse into children first (EET-main order), then transform this node.
        E exprRec = adapter.mapChildren(this::transformExpression, expr);

        // Query-level nodes (UNION, SELECT, WITH, etc.) should not be wrapped in CASE/tautology.
        // They are returned after branch recursion — their internal expressions are already transformed.
        if (adapter.isQueryLevelNode(exprRec)) {
            return exprRec;
        }

        int attempts = 0;
        while (attempts++ < MAX_TRANSFORM_RETRIES) {
            try {
                E result = tryTransformRules(exprRec);
                if (result != null) {
                    // Impedance tracking: record success
                    if (impedanceTracker != null) {
                        impedanceTracker.recordSuccess(typeName);
                    }
                    return result;
                }
            } catch (IgnoreMeException e) {
                // retry
            }
        }
        // Impedance tracking: record failure (all retries exhausted)
        if (impedanceTracker != null) {
            impedanceTracker.recordFailure(typeName);
        }
        throw new IgnoreMeException();
    }

    /**
     * Try all registered rules on the expression. Returns the first successful
     * transformation, or null if no rule could apply.
     *
     * Rule order:
     * 1. ConstBoolTransform (constant 0/1 extension) -- highest priority
     * 2. BoolTransform (tautology/contradiction wrapping) -- for boolean-like expressions
     * 3. ValueTransform (CASE WHEN wrapping) -- for all expressions
     * 4. Additional semantic rewrite rules -- registered after construction
     *
     * For boolean-like expressions, BoolTransform and ValueTransform are tried
     * randomly (50/50), matching EET native behavior.
     */
    private E tryTransformRules(E expr) throws IgnoreMeException {
        // Step 1: Try semantic rewrite rules first (before wrapping rules)
        // EET native has dual output: is_transformed (CASE wrapping) + has_equal_expr (semantic rewrite).
        // Semantic rewrite produces structurally different but semantically equivalent expressions,
        // which can expose more bugs than pure CASE wrapping.
        for (EETRule<E> rule : rules) {
            // Skip the 3 default wrapping rules — they're tried after semantic rules
            if (rule instanceof EETConstBoolTransformRule || rule instanceof EETBoolTransformRule
                    || rule instanceof EETValueTransformRule) {
                continue;
            }
            if (!adapter.supportsRule(rule.getName())) {
                continue;
            }
            if (rule.canApply(expr, adapter)) {
                try {
                    E result = rule.apply(expr, adapter);
                    if (result != null) {
                        return result;
                    }
                } catch (IgnoreMeException e) {
                    // Skip this rule, try next
                }
            }
        }

        // Step 2: Try ConstBoolTransform (constant 0/1 extension)
        EETConstBoolTransformRule<E> constRule = findConstBoolRule();
        if (constRule != null && constRule.canApply(expr, adapter)) {
            E result = constRule.apply(expr, adapter);
            if (result != null) {
                return result;
            }
        }

        // Step 3: For boolean-like expressions, randomly choose bool or value transform
        if (adapter.isBooleanLike(expr)) {
            if (Randomly.getBoolean()) {
                EETBoolTransformRule<E> boolRule = findBoolTransformRule();
                if (boolRule != null && boolRule.canApply(expr, adapter)) {
                    return boolRule.apply(expr, adapter);
                }
            } else {
                EETValueTransformRule<E> valRule = findValueTransformRule();
                if (valRule != null && valRule.canApply(expr, adapter)) {
                    return valRule.apply(expr, adapter);
                }
            }
        }

        // Step 4: For non-boolean expressions, apply value transform
        EETValueTransformRule<E> valRule = findValueTransformRule();
        if (valRule != null && valRule.canApply(expr, adapter)) {
            return valRule.apply(expr, adapter);
        }

        // No rule applied — let the retry loop handle it
        throw new IgnoreMeException();
    }

    private EETConstBoolTransformRule<E> findConstBoolRule() {
        for (EETRule<E> rule : rules) {
            if (rule instanceof EETConstBoolTransformRule) {
                return (EETConstBoolTransformRule<E>) rule;
            }
        }
        return null;
    }

    private EETBoolTransformRule<E> findBoolTransformRule() {
        for (EETRule<E> rule : rules) {
            if (rule instanceof EETBoolTransformRule) {
                return (EETBoolTransformRule<E>) rule;
            }
        }
        return null;
    }

    private EETValueTransformRule<E> findValueTransformRule() {
        for (EETRule<E> rule : rules) {
            if (rule instanceof EETValueTransformRule) {
                return (EETValueTransformRule<E>) rule;
            }
        }
        return null;
    }
}