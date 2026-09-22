package com.redtoast.simulation.value;

public enum VarGroup {
    NONE,
    NUMBER,
    PRIMITIVE,
    ANY;

    public VarFilter toFilter() {
        return VarFilter.fromGroup(this);
    }
}
