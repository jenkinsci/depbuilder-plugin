package eu.royalsloth.depbuilder.dsl;

public enum TokenType {
    STRING,
    NUMBER,
    IDENTIFIER,

    PLUS,
    STAR,
    DIVIDE,
    MINUS,

    DOT,
    COMMA,
    COLON,
    SEMICOLON,
    PIPE,

    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_BRACE,
    RIGHT_BRACE,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    RIGHT_ARROW,
    LEFT_ARROW,

    UNKNOWN,
    ERROR,
    EOF,
}
