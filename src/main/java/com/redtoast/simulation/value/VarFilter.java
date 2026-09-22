package com.redtoast.simulation.value;

import org.jetbrains.annotations.Nullable;

public enum VarFilter {
    INT(true, true),
    DOUBLE(true, true),
    FLOAT(true, true),
    BOOLEAN(false, true),
    STRING(false, true),
    NULL(false, false),
    INVALID(false, false),
    EXCEPTION(false, false),
    FUNCTION(false, false),
    TABLE(false, false),
    LIST(false, false),
    TUPLE(false, false),
    BYTES(false, true),
    PRIMITIVE(false, true),
    NUMBER(true, true),
    ANY(false, false);

    private final boolean isNumber;
    private final boolean isPrimitive;

    VarFilter(boolean isNumber, boolean isPrimitive) {
        this.isNumber = isNumber;
        this.isPrimitive = isPrimitive;
    }

    public static VarFilter fromType(VarType type) {
        return switch (type) {
            case INT -> VarFilter.INT;
            case DOUBLE -> VarFilter.DOUBLE;
            case FLOAT -> VarFilter.FLOAT;
            case BOOLEAN -> VarFilter.BOOLEAN;
            case STRING -> VarFilter.STRING;
            case NULL -> VarFilter.NULL;
            case INVALID -> VarFilter.INVALID;
            case EXCEPTION -> VarFilter.EXCEPTION;
            case FUNCTION -> VarFilter.FUNCTION;
            case TABLE -> VarFilter.TABLE;
            case LIST -> VarFilter.LIST;
            case TUPLE -> VarFilter.TUPLE;
            case BYTES -> VarFilter.BYTES;
        };
    }

    public static VarFilter fromGroup(VarGroup group) {
        return switch(group) {
            case NONE -> VarFilter.NULL;
            case NUMBER -> VarFilter.NUMBER;
            case PRIMITIVE -> VarFilter.PRIMITIVE;
            case ANY -> VarFilter.ANY;
        };
    }

    public @Nullable VarType toType() {
        return switch (this) {
            case INT -> VarType.INT;
            case DOUBLE -> VarType.DOUBLE;
            case FLOAT -> VarType.FLOAT;
            case BOOLEAN -> VarType.BOOLEAN;
            case STRING -> VarType.STRING;
            case NULL -> VarType.NULL;
            case INVALID -> VarType.INVALID;
            case EXCEPTION -> VarType.EXCEPTION;
            case FUNCTION -> VarType.FUNCTION;
            case TABLE -> VarType.TABLE;
            case LIST -> VarType.LIST;
            case TUPLE -> VarType.TUPLE;
            case BYTES -> VarType.BYTES;
            default -> null;
        };
    }

    public @Nullable VarGroup toGroup() {
        return switch (this) {
            case PRIMITIVE -> VarGroup.PRIMITIVE;
            case NUMBER -> VarGroup.NUMBER;
            case ANY -> VarGroup.ANY;
            default -> null;
        };
    }

    public boolean isNumber() {
        return isNumber;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}