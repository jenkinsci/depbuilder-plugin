package eu.royalsloth.depbuilder.dsl;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static eu.royalsloth.depbuilder.dsl.DslLexer.Tokenizer;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestDslLexer {

    ///////////////////////////
    // Parsing DSL into tokens
    ///////////////////////////
    @Test
    public void toTokensTest() {
        String input = "A -> C;";
        Tokenizer tokenizer = new Tokenizer(input);
        List<Token> tokens = tokenizer.toTokens();

        Token A = new Token(TokenType.IDENTIFIER, "A", 0);
        Token arrow = new Token(TokenType.RIGHT_ARROW, "->", 2);
        Token C = new Token(TokenType.IDENTIFIER, "C", 5);
        Token semicolon = new Token(TokenType.SEMICOLON, ";", 6);
        assertEquals(Arrays.asList(A, arrow, C, semicolon), tokens, "Wrong tokens parsed");
    }

    @Test
    public void toTokensTest_quoted() {
        String input = "\"A-X\"->C";
        Tokenizer tokenizer = new Tokenizer(input);
        List<Token> tokens = tokenizer.toTokens();

        Token A = new Token(TokenType.STRING, "A-X", 1);
        Token arrow = new Token(TokenType.RIGHT_ARROW, "->", 5);
        Token C = new Token(TokenType.IDENTIFIER, "C", 7);
        assertEquals(Arrays.asList(A, arrow, C), tokens, "Wrong tokens parsed");
    }

    @Test
    public void toTokensTest_withoutSpace() {
        String input = "A->C";
        Tokenizer tokenizer = new Tokenizer(input);

        Token A = new Token(TokenType.IDENTIFIER, "A", 0);
        Token arrow = new Token(TokenType.RIGHT_ARROW, "->", 1);
        Token C = new Token(TokenType.IDENTIFIER, "C", 3);
        assertEquals(Arrays.asList(A, arrow, C), tokenizer.toTokens(), "Wrong tokens parsed");
    }

    @Test
    public void toTokenTest_withNewline() {
        String input = "A -> B\nA->C";
        List<Token> tokens = new Tokenizer(input).toTokens();

        Token A = new Token(TokenType.IDENTIFIER, "A", 0);
        Token arrow = new Token(TokenType.RIGHT_ARROW, "->", 2);
        Token B = new Token(TokenType.IDENTIFIER, "B", 5);
        Token semicolon = new Token(TokenType.SEMICOLON, "\\n", 6);
        Token A2 = new Token(TokenType.IDENTIFIER, "A", 7);
        Token arrow2 = new Token(TokenType.RIGHT_ARROW, "->", 8);
        Token C = new Token(TokenType.IDENTIFIER, "C", 10);
        assertEquals(Arrays.asList(A, arrow, B, semicolon, A2, arrow2, C), tokens, "Wrong tokens parsed");
    }

    @Test
    public void toTokenTest_loneNode() {
        String input = "A -> B; C";
        List<Token> tokens = new Tokenizer(input).toTokens();

        Token A = new Token(TokenType.IDENTIFIER, "A", 0);
        Token arrow = new Token(TokenType.RIGHT_ARROW, "->", 2);
        Token B = new Token(TokenType.IDENTIFIER, "B", 5);
        Token semicolon = new Token(TokenType.SEMICOLON, ";", 6);
        Token C = new Token(TokenType.IDENTIFIER, "C", 8);
        assertEquals(Arrays.asList(A, arrow, B, semicolon, C), tokens, "Wrong tokens parsed");
    }

    @Test
    public void toTokenTest_unknown() {
        String input = "#!A";
        List<Token> tokens = new Tokenizer(input).toTokens();
        Token hash = new Token(TokenType.UNKNOWN, "#", 0);
        Token excl = new Token(TokenType.UNKNOWN, "!", 1);
        Token A = new Token(TokenType.IDENTIFIER, "A", 2);
        assertEquals(Arrays.asList(hash, excl, A), tokens, "Wrong tokens parsed");
    }

    @Test
    public void toTokens_numbers() {
        String input = "AB: 123.45678901";
        List<Token> tokens = new Tokenizer(input).toTokens();
        Token id = new Token(TokenType.IDENTIFIER, "AB", 0);
        Token colon = new Token(TokenType.COLON, ":", 2);
        Token num1 = new Token(TokenType.NUMBER, "123.45678901", 4);
        assertEquals(Arrays.asList(id, colon, num1), tokens, "Wrong tokens parsed");
    }

    @Test
    public void toTokens_testingComments() {
        String input = "// first comment \n"
                + "A -> B // my C -> D comment\n"
                + "// third comment";
        List<Token> tokens = new Tokenizer(input).toTokens();
        Token newline1 = new Token(TokenType.SEMICOLON, "\\n", 17);
        Token A = new Token(TokenType.IDENTIFIER, "A", 18);
        Token arrow = new Token(TokenType.RIGHT_ARROW, "->", 20);
        Token B = new Token(TokenType.IDENTIFIER, "B", 23);
        Token newline2 = new Token(TokenType.SEMICOLON, "\\n", 45);
        assertEquals(Arrays.asList(newline1, A, arrow, B, newline2), tokens, "Wrong tokens parsed");
    }
}
