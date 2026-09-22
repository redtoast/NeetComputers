package com.redtoast.simulation.base;

import com.redtoast.Computer;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.config.ComputerConfig;
import com.redtoast.simulation.parameterErrors.ParameterException;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.VarFilter;
import com.redtoast.simulation.value.VarType;

import java.lang.annotation.Annotation;

public interface LanguageGeneric {
    String getName();

    LangThread createThread(String script, Runtime parentRuntime, Computer parentComputer, ComputerConfig specifications);

    boolean canCast(Value<?> value, VarFilter castTo, Annotation[] annotations);

    Value<?> castValue(Value<?> value, VarType castTo, Annotation[] annotations);

    String generateError(ParameterException rule);
}