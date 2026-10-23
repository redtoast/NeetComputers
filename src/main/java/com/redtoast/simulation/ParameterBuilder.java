package com.redtoast.simulation;

import com.redtoast.simulation.annotations.CanBeNull;
import com.redtoast.simulation.value.VarFilter;

import java.lang.annotation.Annotation;
import java.util.LinkedList;

import static com.redtoast.simulation.Parameters.*;

public class ParameterBuilder {
    public static final Annotation CANBENULL = new CanBeNull(){
        @Override
        public Class<? extends Annotation> annotationType() {return CanBeNull.class;}
    };

    private final LinkedList<ParameterType> types = new LinkedList<>();
    private final LinkedList<Class<?>> classes = new LinkedList<>();
    private boolean appendable = true;
    private boolean varargs = false;
    private boolean includeRuntime = false;

    public ParameterBuilder(){}

    public ParameterBuilder(Class<?> type, Annotation... annotations){
        argument(type, annotations);
    }

    public ParameterBuilder argument(Class<?> type, Annotation... annotations) {
        if (!appendable) throw new IllegalStateException("Cant append new arguments to finalized builder");
        types.add(configureList(inferType(type, false, 0, annotations), false));
        classes.add(type);
        return this;
    }

    public ParameterBuilder varargs(Class<?> type, Annotation... annotations) {
        if (!appendable) throw new IllegalStateException("Cant append new arguments to finalized builder");
        ParameterType parameterType = configureList(inferType(type, true, 0, annotations), true);
        if (parameterType.depth()<=0) throw new IllegalArgumentException("Type isn't interpretable as a list or array");
        types.add(parameterType);
        classes.add(type);
        varargs = true;
        appendable = false;
        return this;
    }

    public ParameterBuilder provideRuntime() {
        includeRuntime = true;
        return this;
    }

    public Parameters build() {
        appendable = false;
        return new Parameters(types.toArray(new ParameterType[0]), classes.toArray(new Class[0]), new Class[classes.size()], varargs, includeRuntime ? Runtime.class : null);
    }

    public static Parameters make(Class<?>... casts) {
        ParameterType[] parameterTypes = new ParameterType[casts.length];
        boolean varargs = false;
        for (int i = 0; i < casts.length; i++) {
            try{
                parameterTypes[i] = configureList(inferType(casts[i], i == casts.length-1, 0, new Annotation[0]), false);
                if (i == casts.length-1) varargs = parameterTypes[i].type()== VarFilter.TUPLE;
            } catch (IllegalStateException ignored){
                throw new IllegalArgumentException("Failed to recognize parameterErrors type "+casts[i].toString()+" at parameterErrors #"+i);
            }
        }
        return new Parameters(parameterTypes, casts, new Class[casts.length], varargs, null);
    }
}
