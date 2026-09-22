package com.redtoast.YSLua;

import com.redtoast.Computer;
import com.redtoast.simulation.Parameters;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.annotations.Index;
import com.redtoast.simulation.annotations.Range;
import com.redtoast.simulation.base.LangThread;
import com.redtoast.simulation.base.LanguageGeneric;
import com.redtoast.simulation.config.ComputerConfig;
import com.redtoast.simulation.parameterErrors.*;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.*;
import com.redtoast.simulation.value.VarFilter;
import com.redtoast.simulation.value.VarType;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;

public class LuaMaster implements LanguageGeneric {
    @Override
    public String getName() {
        return "Lua";
    }

    @Override
    public LangThread createThread(String script, Runtime parentRuntime, Computer parentComputer, ComputerConfig specifications) {
        return new LuaThread(script, parentRuntime, specifications);
    }

    @Override
    public boolean canCast(Value<?> value, VarFilter castTo, Annotation[] annotations) {
        VarFilter type = VarFilter.fromType(value.getType());
        if (type!=VarFilter.NULL && castTo==VarFilter.ANY) return true;
        if (castTo==VarFilter.PRIMITIVE) return value.getType().isPrimitive();
        if (castTo.isNumber() && value.getType().isNumber()){
            if (Parameters.getAnnotation(annotations, Range.class) instanceof Range range) {
                boolean indexed = castTo==VarFilter.INT && Parameters.hasAnnotation(annotations, Index.class);
                int min = indexed ? range.min()+1 : range.min();
                int max = indexed ? range.max()+1 : range.max();
                double number = value.toDouble();
                if (min > number || number > max) throw new RangeArgumentError(-1, min, max, number);
            }
            return true;
        }
        if (type==castTo) return true;
        if (castTo==VarFilter.STRING && type==VarFilter.BYTES) return true;
        if (castTo==VarFilter.BYTES && type==VarFilter.STRING) return true;
        if (castTo==VarFilter.LIST && type==VarFilter.TUPLE) return true;
        if (castTo==VarFilter.TUPLE && type==VarFilter.LIST) return true;
        if (castTo==VarFilter.TABLE && type==VarFilter.LIST) return value.toList().isEmpty();
        return false;
    }

    @Override
    public Value<?> castValue(Value<?> value, VarType castTo, Annotation[] annotations) {
        if (castTo==VarType.INT) {
            if (Parameters.hasAnnotation(annotations, Index.class)) return Value.of(value.toInt()-1);
            return Value.of(value.toInt());
        }
        if (value.getType()==castTo) {
            return value;
        }
        if (castTo==VarType.BYTES) return Value.of(new Bytes(((String) value.getValue()).getBytes(StandardCharsets.ISO_8859_1)));
        if (castTo==VarType.STRING) return Value.of(new String(((Bytes) value.getValue()).getData(), StandardCharsets.UTF_8));
        if (castTo==VarType.LIST) return Value.of(value.toList());
        if (castTo==VarType.TUPLE) return Value.of(value.toTuple());
        if (castTo==VarType.TABLE) return new Table().asValue();
        return null;
    }

    @Override
    public String generateError(ParameterException rule) {
        if (rule instanceof ArgumentOverflowError error) return "#"+(rule.getPosition()+1)+" Expected nil, got "+getName(error.getUser());
        if (rule instanceof MissingArgumentError error) return "#"+(rule.getPosition()+1)+" Expected "+getName(error.getType())+", got nil";
        if (rule instanceof MismatchedArgumentError error) return "#"+(rule.getPosition()+1)+" Expected "+getName(error.getType())+", got "+getName(error.getUser());
        if (rule instanceof MismatchedVarargsError error) return "#"+(rule.getPosition()+1)+" Expected "+getName(error.getType()).replaceFirst("\\[]$", "")+", got "+getName(error.getUser());
        if (rule instanceof RangeArgumentError error) return "#"+(rule.getPosition()+1)+" Number "+Double.toString(error.getValue()).replaceFirst("\\.0$","")+" not in range of "+error.getMin()+'-'+error.getMax();
        return "Unknown Parameter Error #" +(rule.getPosition()+1);
    }

    private String getName(Object obj) {
        return obj.toString()
                .replaceFirst("null", "nil")
                .replaceFirst("int", "number")
                .replaceFirst("float", "number")
                .replaceFirst("double", "number")
                .replaceFirst("any\\[]", "list");
    }

    public Value<?> toValue(LuaValue var) {
        if (var==null) return Value.NULL;
        Value<?> output = switch (var.getType()){
            case NIL -> Value.NULL;
            case BOOLEAN -> Value.of((boolean) var.getValue());
            case NUMINT -> Value.of((int) var.getValue());
            case NUMFLOAT -> Value.of((double) var.getValue());
            case STRING -> Value.of((String) var.getValue());
            case BINARY -> Value.of((byte[]) var.getValue());
            case FUNCTION -> var.getValue() instanceof LuaFunction function ? Value.of(function.function) : Value.NULL;
            case LIST -> {
                List list = new List();
                for (LuaValue value : (LuaValue[]) var.getValue()) list.add(toValue(value));
                yield list.asValue();
            }
            case TABLE -> {
                Table table = new Table();
                ((Map<LuaValue, LuaValue>) var.getValue()).forEach((key, value) -> table.put(toValue(key), toValue(value)));
                yield table.asValue();
            }
            case ERROR -> Value.asError((String) var.getValue());
            default -> Value.INVALID;
        };
        output.setLanguage(this);
        return output;
    }

    public LuaValue fromValue(Value<?> var) {
        if (var==null) return LuaValue.from();
        switch (var.getType()){
            case INT -> {
                return LuaValue.from((int) var.getValue());
            }
            case DOUBLE, FLOAT -> {
                return LuaValue.from(((Number) var.getValue()).doubleValue());
            }
            case BOOLEAN -> {
                return LuaValue.from((boolean) var.getValue());
            }
            case STRING -> {
                return LuaValue.from(var.toString());
            }
            case BYTES -> {
                return LuaValue.from(Objects.requireNonNull(var.toBytes()).getData());
            }
            case NULL -> {
                return LuaValue.from();
            }
            case EXCEPTION -> {
                return LuaValue.error(var.getValue().toString());
            }
            case FUNCTION -> {
                return LuaValue.from(new LuaFunction(var.toFunction()));
            }
            case TABLE -> {
                Table table = var.toTable();
                Map<LuaValue, LuaValue> map = new Hashtable<>();
                table.foreach((key, value) -> map.put(fromValue(key), fromValue(value)));
                return LuaValue.from(map);
            }
            case LIST,TUPLE -> {
                List list = var.toList();
                LuaValue[] array = new LuaValue[list.size()];
                for (int i = 0; i < array.length; i++) array[i] = fromValue(list.get(i));
                return LuaValue.from(array);
            }
            default -> {
                return LuaValue.invalid();
            }
        }
    }

    private class LuaFunction implements LuaValue.Function {
        private final Function function;

        public LuaFunction(Function function) {
            this.function = function;
        }

        @Override
        public LuaValue[] call(LuaValue[] parameters) {
            Value<?>[] args = new Value[parameters.length];
            for (int i = 0; i < parameters.length; i++) args[i] = toValue(parameters[i]);
            Value<?> retrn = function.invoke(args);
            if (retrn.isNull()) return new LuaValue[0];
            if (retrn.instanceOf(VarType.TUPLE)) {
                Tuple tuple = retrn.toTuple();
                LuaValue[] returns = new LuaValue[tuple.size()];
                for (int i = 0; i < tuple.size(); i++)
                    returns[i] = fromValue(tuple.get(i));
                return returns;
            }
            return new LuaValue[]{fromValue(retrn)};
        }

        public Function getFunction() {return function;}
    }
}
