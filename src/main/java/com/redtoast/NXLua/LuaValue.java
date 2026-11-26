package com.redtoast.NXLua;

import java.util.Map;

public class LuaValue {
    private final Object obj;
    private final Type type;

    private LuaValue(Object obj, Type type) {
        this.obj = obj;
        this.type = type;
    }

    public static LuaValue from() {
        return new LuaValue(null, Type.NIL);
    }

    public static LuaValue from(boolean value) {
        return new LuaValue(value, Type.BOOLEAN);
    }

    public static LuaValue from(int value) {
        return new LuaValue(value, Type.NUMINT);
    }

    public static LuaValue from(double value) {
        return new LuaValue(value, Type.NUMFLOAT);
    }

    public static LuaValue from(String value) {
        return new LuaValue(value, Type.STRING);
    }

    public static LuaValue from(byte[] value) {
        return new LuaValue(value, Type.BINARY);
    }

    public static LuaValue from(LuaValue[] list) {
        return new LuaValue(list, Type.LIST);
    }

    public static LuaValue from(Map<LuaValue, LuaValue> table) {
        return new LuaValue(table, Type.TABLE);
    }

    public static LuaValue from(Function function) {
        return new LuaValue(function, Type.FUNCTION);
    }

    public static LuaValue error(String error) {
        return new LuaValue(error, Type.ERROR);
    }

    public static LuaValue invalid() {
        return new LuaValue(null, Type.INVALID);
    }

    public static LuaValue opaque(String nativeTypeName) {
        return new LuaValue(nativeTypeName, Type.OPAQUE);
    }

    public interface Function {
        LuaValue[] call(LuaValue[] parameters);
    }

    public Type getType(){
        return type;
    }

    public Object getValue(){
        return obj;
    }

    public enum Type {
        NIL,
        BOOLEAN,
        NUMINT,
        NUMFLOAT,
        STRING,
        BINARY,
        FUNCTION,
        LIST,
        TABLE,
        ERROR,
        INVALID,
        OPAQUE
    }
}