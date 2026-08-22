package com.redtoast.simulation;

import com.google.gson.internal.Primitives;
import com.redtoast.simulation.annotations.*;
import com.redtoast.simulation.annotations.Number;
import com.redtoast.simulation.base.LanguageGeneric;
import com.redtoast.simulation.parameterErrors.*;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.*;
import com.redtoast.simulation.value.VarType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public record Parameters(ParameterType[] types, Class<?>[] classes, boolean isPacked) {
    private static final Parameters empty = new Parameters(new ParameterType[0], new Class[0], false);
    private static final Parameters any = new Parameters(new ParameterType[]{new ParameterType(VarType.TUPLE, VarType.ANY, 1, new Annotation[0])}, new Class[]{Object[].class}, true);

    public record ParameterType(VarType type, VarType filter, int depth, Annotation[] annotations) {
        public boolean canCast(Value<?> value) {
            if (type == VarType.LIST || type == VarType.TUPLE) {
                if (!value.canCast(type, annotations)) return false;
                return iterate(1, value);
            }else{
                if (type==VarType.ANY) return true;
                return value.canCast(type, annotations);
            }
        }

        private boolean iterate(int i, Value<?> value) {
            if (value.getType() != VarType.LIST && value.getType() != VarType.TUPLE) return false;
            for (Value<?> val : value.toList()) {
                if (i < depth){
                    if (val.getType() != VarType.LIST && val.getType() != VarType.TUPLE) return false;
                    boolean retrn = iterate(i+1, val);
                    if (!retrn) {
                        if (type == VarType.TUPLE) throw new MismatchedVarargsError(i, val.getType(), null);
                        return false;
                    }
                }else{
                    if (!val.canCast(filter, annotations)) {
                        if (type == VarType.TUPLE) throw new MismatchedVarargsError(i, val.getType(), null);
                        return false;
                    }
                }
            }
            return true;
        }

        public boolean hasAnnotation(Class<? extends Annotation> annotation) {
            return Parameters.hasAnnotation(annotations, annotation);
        }

        public Annotation getAnnotation(Class<? extends Annotation> annotation) {
            return Parameters.getAnnotation(annotations, annotation);
        }

        @Override
        public String toString(){
            StringBuilder name;
            if (depth==0) {
                name = new StringBuilder(type.toString());
            }else{
                name = new StringBuilder(filter.toString());
                name.append("[]".repeat(depth));
            }
            if (hasAnnotation(Index.class)) name.insert(0, '#');
            if (hasAnnotation(CanBeNull.class)) name.insert(0, '~');
            if (getAnnotation(Range.class) instanceof Range range) {
                name.append(new char[]{' ', '<'});
                name.append(range.min());
                name.append('-');
                name.append(range.max());
                name.append('>');
            }
            return name.toString();
        }
    }

    public Object[] cast(Value<?>[] values) {
        Object[] array = new Object[types.length];
        if (isPacked) {
            Tuple varargs = new Tuple();
            int i = 0;
            int x = size()-1;
            for (; i < x; i++) {
                array[i] = cast(values[i], classes[i], false, types[i].annotations());
            }
            for (; i < values.length; i++) {
                varargs.add(values[i]);
            }
            array[x] = cast(varargs.asValue(), classes[x], true, types[x].annotations());
        }else{
            for (int i = 0; i < size(); i++) {
                array[i] = cast(i >= values.length ? Value.NULL : values[i], classes[i], false, types[i].annotations());
            }
        }
        return array;
    }

    public Optional<String> canCast(Value<?>[] values, LanguageGeneric language) {
        if (language == null && values.length>0) language = values[0].getLanguage();
        int i = 0;
        try {
            int size = isPacked ? size()-1 : size();
            /*check if values can be cast*/
            for (; i < Math.min(values.length, size); i++) {
                if (!types[i].canCast(values[i]) && !hasAnnotation(types[i].annotations, CanBeNull.class)) throw new MismatchedArgumentError(i, values[i].getType(),types[i]);
            }
            /*throw errors for values that are missing*/
            for (; i < size; i++) {
                if (!hasAnnotation(types[i].annotations, CanBeNull.class)) throw new MissingArgumentError(i, types[i]);
            }
            if (isPacked) {
                Tuple tuple = new Tuple();
                for (; i < values.length; i++) tuple.add(values[i]);
                try {
                    if (!types[size].canCast(tuple.asValue())) throw new ParameterException(size);
                }catch (MismatchedVarargsError error){
                    throw new MismatchedVarargsError(error.getPosition()+i-2, error.getUser(), types[size]);
                }
            }else if (values.length > size) throw new ArgumentOverflowError(i, values[size].getType());
        }catch (ParameterException parameterException){
            parameterException.setPositionIfMissing(i);
            return getError(language, parameterException);
        }
        return Optional.empty();
    }

    public int size() {return types.length;}

    public static Object cast(Value<?> value, Class<?> clazz, boolean pack, Annotation[] annotations) {
        if (value.isNull()) return null;
        if (clazz == int.class) return checkExists(value.castTo(VarType.INT, annotations).getValue());
        if (clazz == double.class) return checkExists(value.castTo(VarType.DOUBLE, annotations).getValue());
        if (clazz == float.class) return checkExists(value.castTo(VarType.FLOAT, annotations).getValue());
        if (clazz == boolean.class) return checkExists(value.castTo(VarType.BOOLEAN, annotations).getValue());
        if (clazz == Integer.class) return checkExists(value.castTo(VarType.INT, annotations).getValue());
        if (clazz == Double.class) return checkExists(value.castTo(VarType.DOUBLE, annotations).getValue());
        if (clazz == Float.class) return checkExists(value.castTo(VarType.FLOAT, annotations).getValue());
        if (clazz == Boolean.class) return checkExists(value.castTo(VarType.BOOLEAN, annotations).getValue());
        if (clazz == byte[].class) return checkExists(value.castTo(VarType.BYTES, annotations).toBytes().getData());
        if (clazz == Bytes.class) return checkExists(value.castTo(VarType.BYTES, annotations).getValue());
        if (clazz == char[].class) return checkExists(value.castTo(VarType.STRING, annotations).toString().toCharArray());
        if (clazz == String.class) return checkExists(value.castTo(VarType.STRING, annotations).getValue());
        if (clazz == Table.class) return checkExists(value.castTo(VarType.TABLE, annotations).getValue());
        if (clazz == Function.class) return checkExists(value.castTo(VarType.FUNCTION, annotations).getValue());
        if (clazz == Object.class) return checkExists(value.getValue());
        if (clazz == Value.class) return value;
        if (clazz == List.class) return checkExists(value.castTo(VarType.LIST, annotations).getValue());
        if (clazz == Tuple.class) return checkExists(value.castTo(VarType.TUPLE, annotations).getValue());
        if (clazz.isArray()) {
            List list = (List) checkExists((pack ? value.castTo(VarType.TUPLE, annotations) : value.castTo(VarType.LIST, annotations)).getValue());
            Class<?> sub = Primitives.wrap(clazz.componentType());
            Object[] array = (Object[]) Array.newInstance(sub, list.size());
            for (int i = 0; i < list.size(); i++) {
                array[i] = cast(list.get(i), sub, false, annotations);
            }
            return array;
        }
        throw new IllegalStateException("Attempted to cast unrecognized parameterErrors type");
    }

    private Optional<String> getError(LanguageGeneric language, ParameterException exception) {
        return language==null ? Optional.of("Language Missing #"+exception.getPosition()+":\n"+ this) : Optional.of(language.generateError(exception)+":\n"+ this);
    }

    private static Object checkExists(Object value) {
        if (value == null || value instanceof Null) throw new IllegalArgumentException("Attempted to cast unrecognized parameterErrors type");
        return value;
    }

    public static Parameters deduceParameters(Parameter[] parameters) {
        ParameterType[] parameterTypes = new ParameterType[parameters.length];
        Class<?>[] classes = new Class[parameters.length];
        boolean varargs = false;
        for (int i = 0; i < parameters.length; i++) {
            try{
                parameterTypes[i] = configureList(inferType(parameters[i].getType(), i == parameters.length-1, 0, parameters[i].getDeclaredAnnotations()), parameters[i].isVarArgs());
                classes[i] = parameters[i].getType();
                if (i == parameters.length-1) varargs = parameters[i].isVarArgs() && parameterTypes[i].depth>0 || parameterTypes[i].type==VarType.TUPLE;
            } catch (IllegalStateException ignored){
                throw new IllegalArgumentException("Failed to recognize parameterErrors type "+parameters[i].getType().toString()+" at parameterErrors #"+i);
            }
        }
        return new Parameters(parameterTypes, classes, varargs);
    }

    public static Function attachSelectiveErrors(java.util.List<Function> unpatchedFunctions, String name, Runtime runtime) {
        return new Function(false, name, Parameters.any()) {
            @Override
            public Value call(Value<?>[] parameters) {
                StringBuilder errorBuilder = new StringBuilder();
                for (int i = 0; i < unpatchedFunctions.size(); i++){
                    Optional<String> check = unpatchedFunctions.get(i).getRules().canCast(parameters, runtime==null ? null : runtime.getThread().getLang());
                    if (check.isEmpty()) {
                        return unpatchedFunctions.get(i).invoke(parameters);
                    }else{
                        if (i!=0) errorBuilder.append('\n');
                        errorBuilder.append(check.get());
                    }
                }
                return Value.asError(errorBuilder.toString());
            }
        };
    }

    public static Function attachErrors(Function function, Runtime runtime) {
        return new Function(false, function.getName(), function.getRules()) {
            @Override
            public Value call(Value<?>[] parameters) {
                Optional<String> check = getRules().canCast(parameters, runtime==null ? null : runtime.getThread().getLang());
                return check.map(Value::asError).orElseGet(() -> function.invoke(parameters));
            }
        };
    }

    public static Parameters make(Class<?>... casts) {
        ParameterType[] parameterTypes = new ParameterType[casts.length];
        boolean varargs = false;
        for (int i = 0; i < casts.length; i++) {
            try{
                parameterTypes[i] = configureList(inferType(casts[i], i == casts.length-1, 0, new Annotation[0]), false);
                if (i == casts.length-1) varargs = parameterTypes[i].type==VarType.TUPLE;
            } catch (IllegalStateException ignored){
                throw new IllegalArgumentException("Failed to recognize parameterErrors type "+casts[i].toString()+" at parameterErrors #"+i);
            }
        }
        return new Parameters(parameterTypes, casts, varargs);
    }

    private static ParameterType configureList(ParameterType type, boolean tuple) {
        if (type.depth>0 && type.filter==VarType.NULL) {
            return new ParameterType(tuple ? VarType.TUPLE : VarType.LIST, type.type(), type.depth, type.annotations);
        }
        return type;
    }

    public static boolean hasAnnotation(Annotation[] annotations, Class<? extends Annotation> annotation) {
        for (Annotation anno : annotations) {
            if (Objects.equals(anno.toString().split("\\(")[0], annotation.toString().replaceFirst("^interface ", "@"))) {
                return true;
            }
        }
        return false;
    }

    public static Annotation getAnnotation(Annotation[] annotations, Class<? extends Annotation> annotation) {
        for (Annotation anno : annotations) {
            if (Objects.equals(anno.toString().split("\\(")[0], annotation.toString().replaceFirst("^interface ", "@"))) {
                return anno;
            }
        }
        return null;
    }

    private static ParameterType inferType(Class<?> clazz, boolean allowTuple, int entryDepth, Annotation[] annotations) {
        VarType type = VarType.NULL;
        int depth = entryDepth;
        VarType filter = VarType.NULL;
        if (entryDepth>0 && Primitives.isPrimitive(clazz)) throw new IllegalArgumentException("Parameter arrays cant be primitive I.E (int[] should be Integer[])");
        if (clazz == int.class || clazz == Integer.class) type = VarType.INT;
        if (clazz == double.class || clazz == Double.class) type = VarType.DOUBLE;
        if (clazz == float.class || clazz == Float.class) type = VarType.FLOAT;
        if (clazz == boolean.class || clazz == Boolean.class) type = VarType.BOOLEAN;
        if (clazz == byte[].class || clazz == Bytes.class) type = VarType.BYTES;
        if (clazz == char[].class || clazz == String.class) type = VarType.STRING;
        if (clazz == List.class) {
            type = VarType.LIST;
            depth++;
            filter = VarType.ANY;
            if (hasAnnotation(annotations, Primative.class)) filter = VarType.PRIMITIVE;
            if (hasAnnotation(annotations, Number.class)) filter = VarType.NUMBER;
        }
        if (clazz == Tuple.class) {
            type = allowTuple ? VarType.TUPLE : VarType.LIST;
            depth++;
            filter = VarType.ANY;
            if (hasAnnotation(annotations, Primative.class)) filter = VarType.PRIMITIVE;
            if (hasAnnotation(annotations, Number.class)) filter = VarType.NUMBER;
        }
        if (clazz == Table.class) type = VarType.TABLE;
        if (clazz == Function.class) type = VarType.FUNCTION;
        if (clazz == Object.class || clazz == Value.class) type = hasAnnotation(annotations, Primative.class) ? VarType.PRIMITIVE : hasAnnotation(annotations, Number.class) ?  VarType.NUMBER : VarType.ANY;
        if (type == VarType.NULL) {
            if (clazz.isArray()) {
                return inferType(clazz.componentType(), allowTuple, depth + 1, annotations);
            }
            throw new IllegalStateException("Attempted to parse unrecognized parameterErrors type");
        }
        return new ParameterType(type, filter, depth, annotations);
    }

    public static Parameters empty() {
        return empty;
    }

    public static Parameters any() {
        return any;
    }

    @Override
    public String toString() {
        return isPacked ? Arrays.toString(types).replaceFirst("\\[]]$", "...]") : Arrays.toString(types);
    }
}
