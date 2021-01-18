package eu.royalsloth.depbuilder.dsl;

public class Token {
    public TokenType type = TokenType.UNKNOWN;
    public String text = "";
    public int startPos = 0;

    public Token() {
    }

    public Token(TokenType type, String text, int startPos) {
        this.type = type;
        this.text = text;
        this.startPos = startPos;
    }

    public boolean isIdentifier() {
        boolean isIdentifier = this.type == TokenType.IDENTIFIER || this.type == TokenType.STRING;
        return isIdentifier;
    }

    public boolean isNumber() {
        boolean isNumber = this.type == TokenType.NUMBER;
        return isNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Token token = (Token) o;

        if (startPos != token.startPos) {
            return false;
        }
        if (type != token.type) {
            return false;
        }
        return text.equals(token.text);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + text.hashCode();
        result = 31 * result + startPos;
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s(%s, %d)", type, text, startPos);
    }
}
