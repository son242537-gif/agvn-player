/* Copyright (c) 2026 agvn.io.vn — MIT License (see LICENSE). */
package com.winlator.cmod.agvn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough of Python's pickle to read a Ren'Py archive index, a dict of name → [(offset, length, prefix)], as
 * Ren'Py 6/7 (Python 2, protocol 2) and Ren'Py 8 (Python 3, protocol 2 to 5) write it. Python str → String, bytes and
 * Python 2 str → byte[], int → Long, tuple and list → List, dict → Map with String keys. It never runs code: the only
 * calls it knows rebuild bytes (_codecs.encode(text, "latin1") and bytes()); anything else stops it. Pure Java.
 */
final class AgvnPickle {
    private static final Object MARK = new Object();

    /** A Python global (module.name) named in the pickle; only a REDUCE of the two known ones gives a value. */
    private static final class Global {
        final String module, name;
        Global(String module, String name) { this.module = module; this.name = name; }
    }

    private final byte[] d;
    private int p;
    private final List<Object> stack = new ArrayList<>();
    private final Map<Integer, Object> memo = new HashMap<>();

    private AgvnPickle(byte[] data) {
        d = data;
    }

    /** The object pickled in {@code data}; IOException (or a RuntimeException on broken data) when it cannot be read. */
    static Object load(byte[] data) throws IOException {
        return new AgvnPickle(data).run();
    }

    private Object run() throws IOException {
        while (p < d.length) {
            int op = d[p++] & 0xFF;
            switch (op) {
                case 0x80: p += 1; break; // PROTO
                case 0x95: p += 8; break; // FRAME
                case '.': return pop(); // STOP
                case '(': stack.add(MARK); break;
                case ')': case ']': stack.add(new ArrayList<>()); break;
                case '}': stack.add(new LinkedHashMap<String, Object>()); break;
                case 'N': stack.add(null); break;
                case 0x88: stack.add(Boolean.TRUE); break;
                case 0x89: stack.add(Boolean.FALSE); break;
                case 'J': stack.add((long) (int) le(4)); break;
                case 'K': stack.add(le(1)); break;
                case 'M': stack.add(le(2)); break;
                case 0x8a: stack.add(signed((int) le(1))); break; // LONG1
                case 0x8b: stack.add(signed((int) le(4))); break; // LONG4
                case 'I': case 'L': stack.add(number(line())); break;
                case 'X': stack.add(text((int) le(4))); break;
                case 0x8c: stack.add(text((int) le(1))); break;
                case 0x8d: stack.add(text(size8())); break;
                case 'U': case 'C': stack.add(bytes((int) le(1))); break;
                case 'T': case 'B': stack.add(bytes((int) le(4))); break;
                case 0x8e: stack.add(bytes(size8())); break;
                case 0x85: stack.add(new ArrayList<>(Arrays.asList(pop()))); break;
                case 0x86: { Object b = pop(), a = pop(); stack.add(new ArrayList<>(Arrays.asList(a, b))); break; }
                case 0x87: { Object c = pop(), b = pop(), a = pop(); stack.add(new ArrayList<>(Arrays.asList(a, b, c))); break; }
                case 't': case 'l': stack.add(popMark()); break;
                case 'd': stack.add(putAll(new LinkedHashMap<>(), popMark())); break;
                case 'a': { Object v = pop(); list(top()).add(v); break; }
                case 'e': { List<Object> items = popMark(); list(top()).addAll(items); break; }
                case 's': { Object v = pop(), k = pop(); map(top()).put(key(k), v); break; }
                case 'u': { List<Object> items = popMark(); putAll(map(top()), items); break; }
                case 'q': memo.put((int) le(1), top()); break;
                case 'r': memo.put((int) le(4), top()); break;
                case 'p': memo.put(Integer.parseInt(line().trim()), top()); break;
                case 0x94: memo.put(memo.size(), top()); break; // MEMOIZE
                case 'h': stack.add(memo((int) le(1))); break;
                case 'j': stack.add(memo((int) le(4))); break;
                case 'g': stack.add(memo(Integer.parseInt(line().trim()))); break;
                case 'c': { String module = line(); stack.add(new Global(module, line())); break; }
                case 0x93: { Object name = pop(), module = pop(); stack.add(new Global((String) module, (String) name)); break; }
                case 'R': { Object args = pop(), fn = pop(); stack.add(call(fn, list(args))); break; }
                default: throw new IOException("pickle opcode " + op + " is not supported");
            }
        }
        throw new IOException("pickle ends without STOP");
    }

    /** The two calls Python writes for bytes in protocol 2: _codecs.encode(text, "latin1") and bytes(). */
    private static Object call(Object fn, List<Object> args) throws IOException {
        if (fn instanceof Global) {
            Global g = (Global) fn;
            if (g.module.equals("_codecs") && g.name.equals("encode") && args.size() == 2 && args.get(0) instanceof String)
                return ((String) args.get(0)).getBytes(StandardCharsets.ISO_8859_1);
            if ((g.module.equals("__builtin__") || g.module.equals("builtins")) && g.name.equals("bytes") && args.isEmpty())
                return new byte[0];
            throw new IOException("pickle call " + g.module + "." + g.name + " is not supported");
        }
        throw new IOException("pickle call of a non-function");
    }

    private Map<String, Object> putAll(Map<String, Object> m, List<Object> items) {
        for (int i = 0; i + 1 < items.size(); i += 2) m.put(key(items.get(i)), items.get(i + 1));
        return m;
    }

    /** Dict keys as text: a Python 2 str key (bytes) is UTF-8, as Ren'Py reads it. */
    private static String key(Object k) {
        return k instanceof byte[] ? new String((byte[]) k, StandardCharsets.UTF_8) : String.valueOf(k);
    }

    private long le(int n) {
        need(n);
        long v = 0;
        for (int i = 0; i < n; i++) v |= (long) (d[p + i] & 0xFF) << (8 * i);
        p += n;
        return v;
    }

    /** A little-endian two's complement number of {@code n} bytes (LONG1/LONG4); offsets always fit in 8 bytes. */
    private long signed(int n) throws IOException {
        if (n < 0 || n > 8) throw new IOException("pickle number too large");
        if (n == 0) return 0;
        long v = le(n);
        return n < 8 && (v & (1L << (8 * n - 1))) != 0 ? v - (1L << (8 * n)) : v;
    }

    private static Object number(String line) {
        String s = line.trim();
        if (s.equals("00")) return Boolean.FALSE;
        if (s.equals("01")) return Boolean.TRUE;
        return Long.parseLong(s.endsWith("L") ? s.substring(0, s.length() - 1) : s);
    }

    private int size8() throws IOException {
        long n = le(8);
        if (n < 0 || n > Integer.MAX_VALUE) throw new IOException("pickle string too large");
        return (int) n;
    }

    private String text(int n) {
        return new String(bytes(n), StandardCharsets.UTF_8);
    }

    private byte[] bytes(int n) {
        need(n);
        p += n;
        return Arrays.copyOfRange(d, p - n, p);
    }

    private String line() {
        int end = p;
        while (end < d.length && d[end] != '\n') end++;
        String s = new String(d, p, end - p, StandardCharsets.UTF_8);
        p = Math.min(end + 1, d.length);
        return s;
    }

    private void need(int n) {
        if (n < 0 || p + n > d.length) throw new IndexOutOfBoundsException("pickle is cut short");
    }

    private Object pop() {
        Object o = stack.remove(stack.size() - 1);
        if (o == MARK) throw new IllegalStateException("pickle mark where a value was expected");
        return o;
    }

    private Object top() {
        return stack.get(stack.size() - 1);
    }

    private Object memo(int i) throws IOException {
        if (!memo.containsKey(i)) throw new IOException("pickle memo " + i + " is missing");
        return memo.get(i);
    }

    private List<Object> popMark() {
        int mark = stack.lastIndexOf(MARK);
        if (mark < 0) throw new IllegalStateException("pickle mark is missing");
        List<Object> items = new ArrayList<>(stack.subList(mark + 1, stack.size()));
        stack.subList(mark, stack.size()).clear();
        return items;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }
}
