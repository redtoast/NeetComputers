package com.redtoast.simulation.value.ValueTypes;

import com.redtoast.simulation.Parameters;
import com.redtoast.simulation.value.Value;

/**
 * represents an N.E.E.T. computers callable function, call implementation, parameterErrors handling, and error handling not included
 * @see Value
 * @see List
 * @see Tuple
 * @see Table
 * @see Exception
 * @see java.lang.reflect.Method
 */
public abstract class Function{
    private final Parameters ruleset;
    private String name;
    private final boolean userGenerated;

    public Function(boolean userGenerated){
        ruleset = Parameters.empty();
        this.userGenerated = userGenerated;
    }
    public Function(boolean userGenerated, String Name){
        name = Name;
        ruleset = Parameters.empty();
        this.userGenerated = userGenerated;
    }
    public Function(boolean userGenerated, Parameters rules){
        ruleset = rules;
        this.userGenerated = userGenerated;
    }
    public Function(boolean userGenerated, String Name, Parameters rules){
        ruleset=rules;
        name=Name;
        this.userGenerated = userGenerated;
    }
    @Deprecated
    public abstract Value call(Value<?>[] parameters);
    public Value invoke(Value<?>[] parameters){
        return call(parameters);
    }
    public Parameters getRules(){
        return ruleset;
    }
    public void setName(String Name){name = Name;}
    public String getName(){return name;}
    public boolean isUserGenerated() {return userGenerated;}
    public Value<Function> asValue(){
        return Value.of(this);
    }
}