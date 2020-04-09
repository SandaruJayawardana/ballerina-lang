/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerinalang.compiler.internal.parser;

/**
 * Contains lexer terminal nodes. Includes keywords, syntaxes, and operators.
 * 
 * @since 1.2.0
 */
public class LexerTerminals {

    // Keywords
    public static final String PUBLIC = "public";
    public static final String PRIVATE = "private";
    public static final String FUNCTION = "function";
    public static final String RETURN = "return";
    public static final String RETURNS = "returns";
    public static final String EXTERNAL = "external";
    public static final String TYPE = "type";
    public static final String RECORD = "record";
    public static final String OBJECT = "object";
    public static final String REMOTE = "remote";
    public static final String ABSTRACT = "abstract";
    public static final String CLIENT = "client";
    public static final String IF = "if";
    public static final String ELSE = "else";
    public static final String WHILE = "while";
    public static final String PANIC = "panic";
    public static final String TRUE = "true";
    public static final String FALSE = "false";
    public static final String CHECK = "check";
    public static final String CHECKPANIC = "checkpanic";
    public static final String CONTINUE = "continue";
    public static final String BREAK = "break";
    public static final String IMPORT = "import";
    public static final String VERSION = "version";
    public static final String AS = "as";
    public static final String ON = "on";
    public static final String RESOURCE = "resource";
    public static final String LISTENER = "listener";
    public static final String CONST = "const";
    public static final String FINAL = "final";
    public static final String TYPEOF = "typeof";

    // Types
    public static final String INT = "int";
    public static final String FLOAT = "float";
    public static final String STRING = "string";
    public static final String BOOLEAN = "boolean";
    public static final String DECIMAL = "decimal";
    public static final String XML = "xml";
    public static final String JSON = "json";
    public static final String HANDLE = "handle";
    public static final String ANY = "any";
    public static final String ANYDATA = "anydata";
    public static final String SERVICE = "service";
    public static final String VAR = "var";
    public static final String NEVER = "never";
    
    // Separators
    public static final char SEMICOLON = ';';
    public static final char COLON = ':';
    public static final char DOT = '.';
    public static final char COMMA = ',';
    public static final char OPEN_PARANTHESIS = '(';
    public static final char CLOSE_PARANTHESIS = ')';
    public static final char OPEN_BRACE = '{';
    public static final char CLOSE_BRACE = '}';
    public static final char OPEN_BRACKET = '[';
    public static final char CLOSE_BRACKET = ']';
    public static final char PIPE = '|';
    public static final char QUESTION_MARK = '?';
    public static final char DOUBLE_QUOTE = '"';

    // Arithmetic operators
    public static final char EQUAL = '=';
    public static final char PLUS = '+';
    public static final char MINUS = '-';
    public static final char ASTERISK = '*';
    public static final char SLASH = '/';
    public static final char PERCENT = '%';
    public static final char GT = '>';
    public static final char LT = '<';
    public static final char BACKSLASH = '\\';
    public static final char EXCLAMATION_MARK = '!';
    public static final char BITWISE_AND = '&';
    public static final char BITWISE_XOR = '^';
    public static final char NEGATION = '~';

    // Other
    public static final char NEWLINE = '\n'; // equivalent to 0xA
    public static final char CARRIAGE_RETURN = '\r'; // equivalent to 0xD
    public static final char TAB = 0x9;
    public static final char SPACE = 0x20;
    public static final char FORM_FEED = 0xC;
}
