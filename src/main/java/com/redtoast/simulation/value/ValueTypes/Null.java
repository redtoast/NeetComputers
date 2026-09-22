package com.redtoast.simulation.value.ValueTypes;

import com.redtoast.simulation.value.Value;

/**
 * represents an N.E.E.T. computers null value, if your using {@link Value} correctly you should never be reading this
 * @see Value
 * @see List
 * @see Tuple
 * @see Table
 * @see Function
 * @see Exception
 */
public class Null{
    public static final Null INSTANCE = new Null();
    private Null() {}

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Null){
            return true;
        }else if (obj == null){
            return true;
        }
        return super.equals(obj);
    }
    public Value<Null> asValue(){
        return Value.NULL;
    }
    @Override
    public String toString() {return "null";}
}