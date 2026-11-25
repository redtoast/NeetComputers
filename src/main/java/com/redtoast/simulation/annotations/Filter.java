package com.redtoast.simulation.annotations;

import com.redtoast.simulation.value.VarFilter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})
public @interface Filter {
    VarFilter value();
}
