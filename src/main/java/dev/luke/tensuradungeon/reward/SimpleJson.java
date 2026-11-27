package dev.luke.tensuradungeon.reward;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimpleJson {
    private SimpleJson() {}

    static Object parse(Reader reader) throws IOException {
        StringBuilder source = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) source.append(buffer, 0, read);
        Parser parser = new Parser(source.toString());
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.error("Unexpected trailing content");
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> array(Object value) {
        if (value instanceof List<?> list) return (List<Object>) list;
        return List.of();
    }

    static String string(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String text ? text : fallback;
    }

    static int integer(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    static double decimal(Map<String, Object> object, String key, double fallback) {
        Object value = object.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    static boolean bool(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) {
            this.source = source;
        }

        private boolean atEnd() {
            return index >= source.length();
        }

        private RuntimeException error(String message) {
            return new IllegalArgumentException(message + " at character " + index);
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char current = source.charAt(index);
                if (current == ' ' || current == '\n' || current == '\r' || current == '\t') index++;
                else break;
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (atEnd()) throw error("Expected a value");
            return switch (source.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) return result;
            while (true) {
                skipWhitespace();
                if (atEnd() || source.charAt(index) != '"') throw error("Expected an object key");
                String key = parseString();
                skipWhitespace();
                expect(':');
                result.put(key, parseValue());
                skipWhitespace();
                if (consume('}')) return result;
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            ArrayList<Object> result = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) return result;
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (consume(']')) return result;
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char current = source.charAt(index++);
                if (current == '"') return result.toString();
                if (current != '\\') {
                    result.append(current);
                    continue;
                }
                if (atEnd()) throw error("Unterminated string escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"' -> result.append('"');
                    case '\\' -> result.append('\\');
                    case '/' -> result.append('/');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw error("Incomplete unicode escape");
                        String hex = source.substring(index, index + 4);
                        try {
                            result.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException invalid) {
                            throw error("Invalid unicode escape");
                        }
                        index += 4;
                    }
                    default -> throw error("Unknown string escape");
                }
            }
            throw error("Unterminated string");
        }

        private Object parseNumber() {
            int start = index;
            if (!atEnd() && source.charAt(index) == '-') index++;
            while (!atEnd() && Character.isDigit(source.charAt(index))) index++;
            boolean decimal = false;
            if (!atEnd() && source.charAt(index) == '.') {
                decimal = true;
                index++;
                while (!atEnd() && Character.isDigit(source.charAt(index))) index++;
            }
            if (!atEnd() && (source.charAt(index) == 'e' || source.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (!atEnd() && (source.charAt(index) == '+' || source.charAt(index) == '-')) index++;
                while (!atEnd() && Character.isDigit(source.charAt(index))) index++;
            }
            if (start == index) throw error("Expected a number");
            String token = source.substring(start, index);
            try {
                if (decimal) return Double.parseDouble(token);
                long value = Long.parseLong(token);
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return (int) value;
                return value;
            } catch (NumberFormatException invalid) {
                throw error("Invalid number");
            }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!source.startsWith(literal, index)) throw error("Expected " + literal);
            index += literal.length();
            return value;
        }

        private boolean consume(char expected) {
            skipWhitespace();
            if (!atEnd() && source.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (atEnd() || source.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }
    }
}
