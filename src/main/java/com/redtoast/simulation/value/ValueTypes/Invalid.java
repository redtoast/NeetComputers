package com.redtoast.simulation.value.ValueTypes;

import com.redtoast.simulation.value.Value;

/**
 * represents an N.E.E.T. computers invalid value, if your using {@link Value} correctly you should never be reading this
 * @see Value
 * @see List
 * @see Tuple
 * @see Table
 * @see Function
 * @see Exception
 */
public class Invalid {
    public static final Invalid INSTANCE = new Invalid();

    private Invalid() {}

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Invalid)
            return true;
        return false;
    }
    public Value<Invalid> asValue(){
        return Value.INVALID;
    }
    @Override
    public String toString() {return "invalid";}
}