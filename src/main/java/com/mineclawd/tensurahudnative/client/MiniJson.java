package com.mineclawd.tensurahudnative.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MiniJson {
    private final String text;
    private int index;

    private MiniJson(String text) {
        this.text = text == null ? "" : text;
    }

    static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index != parser.text.length()) {
            throw parser.error("Trailing JSON content");
        }
        return value;
    }

    private Object readValue() {
        skipWhitespace();
        if (index >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        return switch (text.charAt(index)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            result.put(key, readValue());
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> result = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return result;
        }
        while (true) {
            result.add(readValue());
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (index >= text.length()) {
                throw error("Unterminated escape");
            }
            char escaped = text.charAt(index++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> out.append(readUnicode());
                default -> throw error("Unknown escape: \\" + escaped);
            }
        }
        throw error("Unterminated string");
    }

    private char readUnicode() {
        if (index + 4 > text.length()) {
            throw error("Truncated unicode escape");
        }
        String hex = text.substring(index, index + 4);
        index += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException ex) {
            throw error("Invalid unicode escape: " + hex);
        }
    }

    private Object readNumber() {
        int start = index;
        if (peek('-')) {
            index++;
        }
        readDigits();
        boolean decimal = false;
        if (peek('.')) {
            decimal = true;
            index++;
            readDigits();
        }
        if (peek('e') || peek('E')) {
            decimal = true;
            index++;
            if (peek('+') || peek('-')) {
                index++;
            }
            readDigits();
        }
        if (start == index) {
            throw error("Expected value");
        }
        String token = text.substring(start, index);
        try {
            return decimal ? Double.parseDouble(token) : Long.parseLong(token);
        } catch (NumberFormatException ex) {
            throw error("Invalid number: " + token);
        }
    }

    private void readDigits() {
        int start = index;
        while (index < text.length() && Character.isDigit(text.charAt(index))) {
            index++;
        }
        if (start == index) {
            throw error("Expected digit");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, index)) {
            throw error("Expected " + literal);
        }
        index += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
    }

    private boolean consume(char expected) {
        if (index < text.length() && text.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            throw error("Expected '" + expected + "'");
        }
    }

    private boolean peek(char c) {
        return index < text.length() && text.charAt(index) == c;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at character " + index);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        return value instanceof List<?> list ? (List<Object>) list : List.of();
    }

    static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static int integer(Map<String, Object> map, String key, int fallback) {
        return (int) Math.round(number(map, key, fallback));
    }

    static long longNumber(Map<String, Object> map, String key, long fallback) {
        return Math.round(number(map, key, fallback));
    }

    static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }
}
