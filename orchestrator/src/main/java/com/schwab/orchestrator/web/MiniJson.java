package com.schwab.orchestrator.web;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal JSON writer for the orchestrator's demo REST API (same rationale as the url-shortener's JsonUtil). */
public final class MiniJson {
    private MiniJson() { }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<Object, Object> e : ((Map<Object, Object>) value).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                writeValue(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<Object>) value) {
                if (!first) sb.append(',');
                first = false;
                writeValue(o, sb);
            }
            sb.append(']');
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    public static Map<String, Object> object(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) map.put(String.valueOf(kv[i]), kv[i + 1]);
        return map;
    }

    // ---------------------------------------------------------------- minimal reader

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object result = new Reader(json == null ? "" : json).parseValue();
        if (!(result instanceof Map)) throw new IllegalArgumentException("Expected a JSON object at top level");
        return (Map<String, Object>) result;
    }

    private static final class Reader {
        private final String s;
        private int pos;

        Reader(String s) { this.s = s; }

        Object parseValue() {
            skipWs();
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON input");
            char c = s.charAt(pos);
            if (c == '{') return parseObjectInternal();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') { expect("null"); return null; }
            return parseNumber();
        }

        Map<String, Object> parseObjectInternal() {
            Map<String, Object> map = new LinkedHashMap<>();
            expectChar('{');
            skipWs();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expectChar(':');
                map.put(key, parseValue());
                skipWs();
                char c = nextChar();
                if (c == '}') break;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or '}' at " + pos);
            }
            return map;
        }

        java.util.List<Object> parseArray() {
            java.util.List<Object> list = new java.util.ArrayList<>();
            expectChar('[');
            skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                char c = nextChar();
                if (c == ']') break;
                if (c != ',') throw new IllegalArgumentException("Expected ',' or ']' at " + pos);
            }
            return list;
        }

        String parseString() {
            skipWs();
            expectChar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = nextChar();
                if (c == '"') break;
                if (c == '\\') {
                    char esc = nextChar();
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: throw new IllegalArgumentException("Invalid escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("Invalid literal at " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            boolean isDouble = false;
            if (pos < s.length() && s.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            if (pos < s.length() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                isDouble = true;
                pos++;
                if (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) pos++;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            }
            String num = s.substring(start, pos);
            if (num.isEmpty() || num.equals("-")) throw new IllegalArgumentException("Invalid number at " + start);
            return isDouble ? (Number) Double.parseDouble(num) : (Number) Long.parseLong(num);
        }

        void expect(String literal) {
            if (!s.startsWith(literal, pos)) throw new IllegalArgumentException("Expected '" + literal + "' at " + pos);
            pos += literal.length();
        }

        void expectChar(char expected) {
            skipWs();
            char c = nextChar();
            if (c != expected) throw new IllegalArgumentException("Expected '" + expected + "' but got '" + c + "' at " + (pos - 1));
        }

        char nextChar() {
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON input");
            return s.charAt(pos++);
        }

        char peek() {
            skipWs();
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }
}
