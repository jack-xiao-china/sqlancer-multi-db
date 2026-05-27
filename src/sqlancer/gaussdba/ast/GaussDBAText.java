package sqlancer.gaussdba.ast;

/**
 * Text fragment for embedding raw SQL text in AST.
 * Used for constructing complex SQL fragments like column references in outer queries.
 */
public final class GaussDBAText implements GaussDBAExpression {

    private final String text;

    public GaussDBAText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public GaussDBADataType getExpressionType() {
        return null;
    }
}