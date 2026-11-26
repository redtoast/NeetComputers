package com.redtoast.NXLua;

import java.util.Hashtable;
import java.util.Map;

public final class Bit32Compat {
    private Bit32Compat() {}

    public static LuaValue build() {
        Map<LuaValue, LuaValue> table = new Hashtable<>();
        put(table, "band", args -> result(band(checkInts(args, "band"))));
        put(table, "bor", args -> result(bor(checkInts(args, "bor"))));
        put(table, "bxor", args -> result(bxor(checkInts(args, "bxor"))));
        put(table, "bnot", args -> result(~checkInt(args, 0, "bnot")));
        put(table, "btest", args -> new LuaValue[]{LuaValue.from(band(checkInts(args, "btest")) != 0)});
        put(table, "lshift", args -> result(lshift(checkInt(args, 0, "lshift"), checkInt(args, 1, "lshift"))));
        put(table, "rshift", args -> result(rshift(checkInt(args, 0, "rshift"), checkInt(args, 1, "rshift"))));
        put(table, "arshift", args -> result(arshift(checkInt(args, 0, "arshift"), checkInt(args, 1, "arshift"))));
        put(table, "lrotate", args -> result(lrotate(checkInt(args, 0, "lrotate"), checkInt(args, 1, "lrotate"))));
        put(table, "rrotate", args -> result(rrotate(checkInt(args, 0, "rrotate"), checkInt(args, 1, "rrotate"))));
        put(table, "extract", args -> result(extract(
                checkInt(args, 0, "extract"),
                checkInt(args, 1, "extract"),
                optInt(args, 2, 1, "extract"))));
        put(table, "replace", args -> result(replace(
                checkInt(args, 0, "replace"),
                checkInt(args, 1, "replace"),
                checkInt(args, 2, "replace"),
                optInt(args, 3, 1, "replace"))));
        return LuaValue.from(table);
    }

    private interface RawFunction {
        LuaValue[] apply(LuaValue[] args);
    }

    private static void put(Map<LuaValue, LuaValue> table, String name, RawFunction fn) {
        table.put(LuaValue.from(name), LuaValue.from(args -> {
            try {
                return fn.apply(args);
            } catch (BitArgException e) {
                return new LuaValue[]{LuaValue.error(e.getMessage())};
            }
        }));
    }

    private static LuaValue[] result(int bits) {
        return new LuaValue[]{bitsToValue(bits)};
    }

    private static int band(int[] xs) {
        int result = -1;
        for (int x : xs) result &= x;
        return result;
    }

    private static int bor(int[] xs) {
        int result = 0;
        for (int x : xs) result |= x;
        return result;
    }

    private static int bxor(int[] xs) {
        int result = 0;
        for (int x : xs) result ^= x;
        return result;
    }

    private static int lshift(int x, int disp) {
        if (disp <= -32 || disp >= 32) return 0;
        return disp >= 0 ? x << disp : x >>> -disp;
    }

    private static int rshift(int x, int disp) {
        if (disp <= -32 || disp >= 32) return 0;
        return disp >= 0 ? x >>> disp : x << -disp;
    }

    private static int arshift(int x, int disp) {
        if (disp >= 0) return disp >= 32 ? (x < 0 ? -1 : 0) : x >> disp;
        return lshift(x, -disp);
    }

    private static int lrotate(int x, int disp) {
        if (disp < 0) return rrotate(x, -disp);
        disp &= 31;
        return (x << disp) | (x >>> (32 - disp));
    }

    private static int rrotate(int x, int disp) {
        if (disp < 0) return lrotate(x, -disp);
        disp &= 31;
        return (x >>> disp) | (x << (32 - disp));
    }

    private static int extract(int n, int field, int width) {
        if (field < 0) throw new BitArgException("bad argument #2 to 'extract' (field cannot be negative)");
        if (width < 0) throw new BitArgException("bad argument #3 to 'extract' (width must be positive)");
        if (field + width > 32) throw new BitArgException("trying to access non-existent bits");
        return (n >>> field) & (-1 >>> (32 - width));
    }

    private static int replace(int n, int v, int field, int width) {
        if (field < 0) throw new BitArgException("bad argument #3 to 'replace' (field cannot be negative)");
        if (width < 0) throw new BitArgException("bad argument #4 to 'replace' (width must be positive)");
        if (field + width > 32) throw new BitArgException("trying to access non-existent bits");
        int mask = (-1 >>> (32 - width)) << field;
        return (n & ~mask) | ((v << field) & mask);
    }

    private static LuaValue bitsToValue(int x) {
        if (x >= 0) return LuaValue.from(x);
        return LuaValue.from((float) (x & 0xFFFFFFFFL));
    }

    private static int[] checkInts(LuaValue[] args, String fname) {
        if (args.length == 0) throw new BitArgException("bad argument #1 to '" + fname + "' (number expected, got no value)");
        int[] out = new int[args.length];
        for (int i = 0; i < args.length; i++) out[i] = checkInt(args, i, fname);
        return out;
    }

    private static int checkInt(LuaValue[] args, int index, String fname) {
        if (index >= args.length) {
            throw new BitArgException("bad argument #" + (index + 1) + " to '" + fname + "' (number expected, got no value)");
        }
        LuaValue value = args[index];
        return switch (value.getType()) {
            case NUMINT -> (int) value.getValue();
            case NUMFLOAT -> (int) (double) value.getValue();
            default -> throw new BitArgException("bad argument #" + (index + 1) + " to '" + fname + "' (number expected, got " + typeName(value) + ")");
        };
    }

    private static int optInt(LuaValue[] args, int index, int fallback, String fname) {
        if (index >= args.length || args[index].getType() == LuaValue.Type.NIL) return fallback;
        return checkInt(args, index, fname);
    }

    private static String typeName(LuaValue value) {
        return switch (value.getType()) {
            case NIL -> "nil";
            case BOOLEAN -> "boolean";
            case STRING -> "string";
            case TABLE -> "table";
            case FUNCTION -> "function";
            case LIST -> "table";
            case BINARY -> "string";
            case OPAQUE -> (String) value.getValue();
            default -> "value";
        };
    }

    private static final class BitArgException extends RuntimeException {
        BitArgException(String message) { super(message); }
    }
}
