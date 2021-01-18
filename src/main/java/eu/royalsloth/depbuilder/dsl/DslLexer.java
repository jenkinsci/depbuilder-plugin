package eu.royalsloth.depbuilder.dsl;

import java.util.ArrayList;
import java.util.List;

public class DslLexer {
    private static final char CHAR_EOF = '\0';

    public static class Tokenizer {

        public int position = 0;
        public int line = 1;
        public final String input;

        public Tokenizer(String input) {
            this.input = input;
        }

        public Token peekNextToken() {
            int currentPos = position;
            int currentLine = line;
            Token token = getNextToken();
            position = currentPos; // reset position
            line = currentLine;
            return token;
        }

        public Token getNextToken() {
            char c = getCurrentChar();
            // chomp on white space
            while (true) {
                if (Character.isWhitespace(c)) {
                    if (c == '\n') {
                        // new line is turned into a separator between two lines
                        // this only happens if we are chomping white space and does
                        // not happen if we contain \n within the string
                        line++;
                        Token token = new Token();
                        token.type = TokenType.SEMICOLON;
                        token.text = "\\n"; // should we set text in such case?
                        token.startPos = this.position;
                        advance();
                        return token;
                    }
                    advance();
                    c = getCurrentChar();
                } else if (c == '/' && peekNextChar() == '/') {
                    // parse comment
                    while (c != '\n' && c != CHAR_EOF) {
                        advance();
                        c = getCurrentChar();
                    }
                } else {
                    // stop chomping whitespace, we found a non white space character
                    break;
                }
            }

            Token token = new Token();
            token.startPos = this.position;
            if (c == CHAR_EOF) {
                token.type = TokenType.EOF;
                return token;
            }

            token.text = Character.toString(c);
            switch (c) {
                case '-': {
                    if (peekNextChar() == '>') {
                        token.type = TokenType.RIGHT_ARROW;
                        token.text = "->";
                        advance();
                        advance();
                        return token;
                    }
                    // lonely minus sign
                    token.type = TokenType.MINUS;
                }
                break;
                case '*':
                    token.type = TokenType.STAR;
                    break;
                case '+':
                    token.type = TokenType.PLUS;
                    break;
                case '/':
                    char nextChar = peekNextChar();
                    if (nextChar == '/') {
                        // skip the comment
                        while (c != CHAR_EOF && !isNewline(c)) {
                            advance();
                            c = getCurrentChar();
                        }
                        return this.getNextToken();
                    }

                    // parsing multi line comment
                    if (nextChar == '*') {
                        advance(); // move iterator to peeked '*'
                        c = getCurrentChar(); // c = '*'
                        advance(); // move past '*'
                        while (c != CHAR_EOF) {
                            c = getCurrentChar();
                            if (c == '*') {
                                nextChar = peekNextChar();
                                if (nextChar == '/') {
                                    advance(); // move to '/'
                                    advance(); // skip past '/'
                                    break;
                                }
                            }
                            advance();
                        }
                        return this.getNextToken();
                    } else {
                        token.type = TokenType.DIVIDE;
                    }
                    break;
                case '.':
                    token.type = TokenType.DOT;
                    break;
                case ',':
                    token.type = TokenType.COMMA;
                    break;
                case ';':
                    token.type = TokenType.SEMICOLON;
                    break;
                case '|':
                    token.type = TokenType.PIPE;
                    break;
                case ':':
                    token.type = TokenType.COLON;
                    break;
                case '(':
                    token.type = TokenType.LEFT_PAREN;
                    break;
                case ')':
                    token.type = TokenType.RIGHT_PAREN;
                    break;
                case '{':
                    token.type = TokenType.LEFT_BRACE;
                    break;
                case '}':
                    token.type = TokenType.RIGHT_BRACE;
                    break;
                case '[':
                    token.type = TokenType.LEFT_BRACKET;
                    break;
                case ']':
                    token.type = TokenType.RIGHT_BRACKET;
                    break;
                case '"': {
                    advance(); // skip first "
                    token.startPos = this.position;
                    c = getCurrentChar();
                    while (c != '"' && c != CHAR_EOF) {
                        // skip escaped " token
                        if (getCurrentChar() == '\\' && peekNextChar() != CHAR_EOF) {
                            advance();
                        }
                        advance();
                        c = getCurrentChar();
                    }

                    int end = this.position;
                    token.text = substring(token.startPos, end);
                    token.type = TokenType.STRING;
                    break;
                }
                default: {
                    c = getCurrentChar();
                    if (isDigit(c)) {
                        token = parseDigitToken(this, token);
                        return token;
                    }

                    if (isAlpha(c)) {
                        token = parseIdentifier(this, token);
                        return token;
                    }

                    // in default case, we have to advance the tokenizer
                    advance();
                    token.type = TokenType.UNKNOWN;
                    return token;
                }
            }

            advance();
            return token;
        }

        public char getCurrentChar() {
            if (position == input.length()) {
                return CHAR_EOF;
            }
            return input.charAt(position);
        }

        public void advance() {
            if (position < input.length()) {
                position++;
            }
        }

        public void back() {
            if (position > 0) {
                position--;
            }
        }

        public String substring(int start, int end) {
            return input.substring(start, end);
        }

        public char peekNextChar() {
            int nextPos = position + 1;
            if (nextPos == input.length()) {
                return CHAR_EOF;
            }

            return input.charAt(nextPos);
        }

        /**
         * @return get current tokenizer line on which we are parsing the tokens
         */
        public int getLine() {
            return line;
        }

        /**
         * Get the line in which the token appears. This should be only used for reporting error messages, as
         * this method is quite slow (iterating over the entire text).
         */
        public int getLine(Token token) {
            // check for invalid position
            if (token.startPos < 0) {
                return 1;
            }

            // startPos number couldn't be more than the length of text
            int startPos = Math.min(token.startPos, input.length() - 1);
            int line = 1; // line should start at 1
            for (int i = 0; i < startPos; i++) {
                char c = input.charAt(i);
                if (isNewline(c)) {
                    line++;
                }
            }
            return line;
        }

        public int getCharacterNumberInLine(Token token) {
            if (token.startPos < 0 || token.startPos > input.length() - 1) {
                return -1;
            }

            for (int i = token.startPos; i > 0; i--) {
                char c = input.charAt(i);
                if (isNewline(c)) {
                    // found the new line, get the char in line number
                    int charInLine = token.startPos - i;
                    return charInLine;
                }
            }

            // there was only one line, char number is token start pos
            return token.startPos;
        }

        // if string is big this method shouldn't be used

        public List<Token> toTokens() {
            List<Token> tokens = new ArrayList<>();
            while (true) {
                Token token = this.getNextToken();
                if (token.type == TokenType.EOF) {
                    break;
                }
                tokens.add(token);
            }
            return tokens;
        }

        public static Token parseDigitToken(Tokenizer tokenizer, Token token) {
            int start = tokenizer.position;
            token.type = TokenType.NUMBER;
            token.startPos = start;

            boolean foundDot = false;
            while (true) {
                char c = tokenizer.getCurrentChar();
                if (isDigit(c)) {
                    tokenizer.advance();
                    continue;
                }

                // character is not a digit
                if (c == '.') {
                    if (!foundDot) {
                        foundDot = true;
                        tokenizer.advance();
                        continue;
                    }

                    // found 2 or more dots while parsing number, stop parsing number
                    // as this is not a valid number, e.g:
                    // 123.000.000
                    // 123...
                    //
                    // TODO: return parse error token? Should we report errors in lexing or in parsing stage?
                    break;
                }

                // we have an identifier that starts with a number e.g: '2myProject'
                if (isAlpha(c)) {
                    Token restOfToken = parseIdentifier(tokenizer, token);
                    token.startPos = start;
                    token.text = tokenizer.substring(start, tokenizer.position);
                    token.type = restOfToken.type;
                    return token;
                }

                // found something that is neither the integer nor an identifier
                // e.g: operator (+). Break the loop.
                break;
            }
            token.text = tokenizer.substring(start, tokenizer.position);
            return token;
        }

        public static Token parseIdentifier(Tokenizer tokenizer, Token token) {
            char c = tokenizer.getCurrentChar();
            int start = tokenizer.position;
            token.type = TokenType.IDENTIFIER;

            while (isAlpha(c) || isDigit(c)) {
                // parsing right arrow
                if (c == '-' && tokenizer.peekNextChar() == '>') {
                    final boolean wasParsingIdentifier = tokenizer.position - start >= 1;
                    if (wasParsingIdentifier) {
                        // we end the identifier parsing. This ensures the identifiers could
                        // contain '-' characters while still allowing '->' without spaces
                        // between identifier and arrow.
                        break;
                    }
                }

                tokenizer.advance();
                c = tokenizer.getCurrentChar();
            }

            token.text = tokenizer.substring(start, tokenizer.position);
            return token;
        }
    }

    public static boolean isNewline(char c) {
        boolean isNewline = c == '\n';
        return isNewline;
    }

    public static boolean isSeparator(char c) {
        boolean separator = c == ';' || c == '\n' || c == '\r';
        return separator;
    }

    public static boolean isAlpha(char c) {
        boolean isAlpha = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c == '-';
        return isAlpha;
    }

    public static boolean isDigit(char c) {
        boolean isDigit = c >= '0' && c <= '9';
        return isDigit;
    }
}
