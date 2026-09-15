/*
 * Aberrant Mobs - a protocol for monsters.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.aberrantmobs.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A JSON reader small enough to live in the pure layer: objects become
 * {@code Map<String, Object>} in file order, arrays {@code List<Object>},
 * numbers {@code Double}, and the rest what they are. Enough to read a
 * Blockbench project; not a validator.
 */
public final class Json {
    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    /**
     * effects: returns the value {@code text} holds<br>
     * throws: {@link IllegalArgumentException} naming the offset where the text stops being JSON
     */
    public static Object parse(String text) {
        Json j = new Json(text);
        Object v = j.value();
        j.ws();
        if (j.i != text.length()) {
            throw j.fail("trailing text");
        }
        return v;
    }

    private Object value() {
        ws();
        if (i >= s.length()) {
            throw fail("value expected");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': return literal("true", Boolean.TRUE);
            case 'f': return literal("false", Boolean.FALSE);
            case 'n': return literal("null", null);
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> out = new LinkedHashMap<>();
        i++;
        ws();
        if (peek() == '}') {
            i++;
            return out;
        }
        while (true) {
            ws();
            if (peek() != '"') {
                throw fail("key expected");
            }
            String key = string();
            ws();
            expect(':');
            out.put(key, value());
            ws();
            char c = next();
            if (c == '}') {
                return out;
            }
            if (c != ',') {
                throw fail("',' or '}' expected");
            }
        }
    }

    private List<Object> array() {
        List<Object> out = new ArrayList<>();
        i++;
        ws();
        if (peek() == ']') {
            i++;
            return out;
        }
        while (true) {
            out.add(value());
            ws();
            char c = next();
            if (c == ']') {
                return out;
            }
            if (c != ',') {
                throw fail("',' or ']' expected");
            }
        }
    }

    private String string() {
        i++;
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw fail("unterminated string");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
            i++;
        }
        if (start == i) {
            throw fail("value expected");
        }
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw fail("bad number");
        }
    }

    private Object literal(String word, Object v) {
        if (!s.startsWith(word, i)) {
            throw fail("'" + word + "' expected");
        }
        i += word.length();
        return v;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private char next() {
        return i < s.length() ? s.charAt(i++) : '\0';
    }

    private void expect(char c) {
        if (next() != c) {
            throw fail("'" + c + "' expected");
        }
    }

    private IllegalArgumentException fail(String what) {
        return new IllegalArgumentException(what + " at offset " + i);
    }

    // --- typed reads over the parsed tree, lenient about what a field holds -----

    /** effects: returns the field, or null if absent or {@code o} is not an object */
    @SuppressWarnings("unchecked")
    public static Object get(Object o, String key) {
        return o instanceof Map<?, ?> m ? ((Map<String, Object>) m).get(key) : null;
    }

    public static double number(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    public static String string(Object o, String fallback) {
        return o instanceof String str ? str : fallback;
    }

    public static boolean bool(Object o, boolean fallback) {
        return o instanceof Boolean b ? b : fallback;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** effects: returns the numbers of a list, {@code fallback} for anything else */
    public static double[] numbers(Object o, double... fallback) {
        List<Object> l = list(o);
        if (l.isEmpty()) {
            return fallback;
        }
        double[] out = new double[l.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = number(l.get(k), 0.0);
        }
        return out;
    }
}
