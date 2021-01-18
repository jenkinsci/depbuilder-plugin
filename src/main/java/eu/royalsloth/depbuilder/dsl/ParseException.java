package eu.royalsloth.depbuilder.dsl;

public class ParseException extends Exception {
    int line = -1;
    int characterInLine = -1;

    public ParseException() {
    }

    public ParseException(String msg, Exception e) {
        super(msg, e);
    }

    public ParseException(String msg) {
        super(msg);
    }

    public ParseException(String msg, Exception e, int line, int charInLine) {
        super(msg, e);
        this.line = line;
        this.characterInLine = charInLine;
    }

    public int getLine() {
        return line;
    }

    public int getCharacterInLine() {
        return characterInLine;
    }

    public static ParseException create(DslLexer.Tokenizer tokenizer, Token problematicToken, String msg) {
        return create(tokenizer, problematicToken, msg, null);
    }

    public static ParseException create(DslLexer.Tokenizer tokenizer, Token problematicToken, String msg, Exception e) {
        int line = tokenizer.getLine(problematicToken);
        int numberInChar = problematicToken.startPos;
        if (line > 1) {
            numberInChar = tokenizer.getCharacterNumberInLine(problematicToken);
        }

        String newMsg = String.format("Line(%d): %s", line, msg);
        return new ParseException(newMsg, e, line, numberInChar);
    }
}
