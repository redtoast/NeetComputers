package com.redtoast.simulation.value;

import com.redtoast.YSLua.LuaMaster;
import com.redtoast.simulation.base.LanguageGeneric;
import com.redtoast.simulation.value.ValueTypes.*;
import com.redtoast.simulation.value.ValueTypes.Exception;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

/**
 * The standard N.E.E.T. computer representation of a Generics value
 * <p>
 *     a Value instance encapsulates an instance from its parameterized type, excepts all primitives and the following complex classes
 * </p>
 * <ul>
 *     <li>{@link String}</li>
 *     <li>{@link List}</li>
 *     <li>{@link Tuple}</li>
 *     <li>{@link Table}</li>
 *     <li>{@link Function}</li>
 *     <li>{@link Exception}</li>
 *     <li>Value[]</li>
 *     <li>Any list of values</li>
 * </ul>
 * @param <Type> the class the Value is encapsulating
 * @see #of(Object) encapsulation method
 * @see VarType type enum
 * @see List standered list
 * @see Tuple standered tuple
 * @see Table standered table
 * @see Function standered function
 * @see Exception standered error
 */
public class Value<Type> {
    /**
     * static Value representation of null
     */
    public final static Value<Null> NULL = new Value<>(Null.INSTANCE);
    /**
     * static Value representation of invalid types
     */
    public final static Value<Invalid> INVALID = new Value<>(Invalid.INSTANCE);
    /**
     * static Value representation of true
     */
    public final static Value<Boolean> TRUE = Value.of(true);
    /**
     * static Value representation of false
     */
    public final static Value<Boolean> FALSE = Value.of(false);

    private final Type value;
    private VarType type = VarType.NULL;
    private LanguageGeneric language = null;

    /**
     * initializes the value raw with no type protection, it's advisable to use {@link #of(Object)} instead
     * @param val value to encapsulate
     */
    private Value(Type val){
        if (val instanceof Integer){
            type = VarType.INT;
        }else if (val instanceof Double){
            type = VarType.DOUBLE;
        }else if (val instanceof Float){
            type = VarType.FLOAT;
        }else if (val instanceof Boolean){
            type = VarType.BOOLEAN;
        }else if (val instanceof String){
            type = VarType.STRING;
        }else if (val instanceof Table){
            type = VarType.TABLE;
        }else if (val instanceof Tuple){
            type = VarType.TUPLE;
        }else if (val instanceof List){
            type = VarType.LIST;
        }else if (val instanceof Bytes){
            type = VarType.BYTES;
        }else if (val instanceof Function){
            type = VarType.FUNCTION;
        }else if (val instanceof Exception){
            type = VarType.EXCEPTION;
        }else if (val instanceof Invalid){
            type = VarType.INVALID;
        }
        value = val;
    }

    /**
     * encapsulates the provided value into a Value object
     * <p>
     *     works as a type-protected wrapper for the {@link #Value} constructor
     * </p>
     * @param value value to encapsulate
     * @return Value
     */
    public static Value<?> of(Object value){
        if (value == null) return NULL;
        if (value instanceof Value<?> val) return val;
        if (value instanceof ValueConvertible<?> convertible) return convertible.asValue();
        if (value instanceof Long val) return new Value<>((int) (long) val);
        if (value instanceof Short val) return new Value<>((int) (short) val);
        if (value instanceof Character val) return new Value<>(String.valueOf(val));
        if (value instanceof Bytes val) return of(val);
        if (value instanceof int[] val) return of(val);
        if (value instanceof byte[] val) return of(val);
        if (value instanceof Value[] val) return of(val);
        if (value instanceof java.util.List<?> val) return of(val);
        return new Value<>(value);
    }
    /**
     * bulk converts a varible amount of args into a encapsulated {@link List}
     * @param values values to encapsulate
     * @return Value containing List
     */
    public static Value<List> of(Object... values){
        List list = new List();
        for (Object obj : values){
            list.add(Value.of(obj));
        }
        return list.asValue();
    }
    public static Value<Integer> of(short value){
        return new Value<>((int) value);
    }
    public static Value<Integer> of(long value){
        return new Value<>((int) value);
    }
    public static Value<Integer> of(int value){
        return new Value<>(value);
    }
    public static Value<Double> of(double value){
        return new Value<>(value);
    }
    public static Value<Float> of(float value){
        return new Value<>(value);
    }
    public static Value<Boolean> of(boolean value){
        return new Value<>(value);
    }
    public static Value<String> of(String value){
        return new Value<>(value);
    }
    public static Value<String> of(char value){
        return new Value<>(String.valueOf(value));
    }
    public static Value<?> of(ValueConvertible<?> convertible) {return convertible==null ? NULL : convertible.asValue();}
    public static Value<Table> of(Table value){
        return new Value<>(value);
    }
    public static Value<List> of(List value){
        return new Value<>(value);
    }
    public static Value<Tuple> of(Tuple value){
        return new Value<>(value);
    }
    public static Value<Function> of(Function value){
        return new Value<>(value);
    }
    public static Value<Exception> of(Exception value){
        return new Value<>(value);
    }
    public static Value<Bytes> of(Bytes bytes){
        return new Value<>(bytes);
    }
    public static Value<Bytes> of(byte[] bytes){
        return new Value<>(new Bytes(bytes));
    }
    public static <T> Value<T> of (Value<T> value) {return value;}
    public static Value<List> of(Value[] values){
        return new Value<>(new List(values));
    }
    public static Value<List> of(java.util.List<?> values){
        List list = new List();
        for (Object obj : values){
            list.add(Value.of(obj));
        }
        return list.asValue();
    }
    public static Value<Null> of(){
        return NULL;
    }

    /**
     * generates an encapsulated Exception (NC version not java version) object
     * @param message message to use as error
     * @return encapsulated exception
     */
    public static Value<Exception> asError(String message){
        return Value.of(new Exception(message));
    }

    /**
     * Associates a language object with the value, the language module is preferred for API casting operations
     */
    public void setLanguage(@NotNull LanguageGeneric language) {
        this.language = language;
    }

    /**
     * Gets a values associated language, may be null
     */
    public LanguageGeneric getLanguage(){
        return language;
    }

    /**
     * returns value with the modifier that if the value is a list it will be cast to a tuple
     * <p>
     *     <i>the returned value is never a list</i>
     * </p>
     * @return modified value
     * @see #pack()
     */
    public Value unpack(){
        if (type==VarType.LIST){
            return this.toTuple().asValue();
        }else{
            return this;
        }
    }

    /**
     * returns value with the modifier that if the value is a tuple it will be cast to a list
     * <p>
     *     <i>the returned value is never a tuple</i>
     * </p>
     * @return modified value
     * @see #unpack()
     */
    public Value pack(){
        if (type==VarType.TUPLE){
            return this.toList().asValue();
        }else{
            return this;
        }
    }

    /**
     * casts the value as an integer or returns nothing if the encapsulated value is not integer cast-able
     * @return Integer or null
     */
    public @Nullable Integer toInt(){
        if (instanceOf(VarGroup.NUMBER)){
            switch (type){
                case INT:
                    return ((Integer) value);
                case DOUBLE:
                    return (int)Math.floor((Double) value);
                case FLOAT:
                    return (int)Math.floor((Float) value);
            }
            return null;
        }else{
            return null;
        }
    }
    /**
     * casts the value as a double or returns nothing if the encapsulated value is not double cast-able
     * @return Double or null
     */
    public @Nullable Double toDouble(){
        if (instanceOf(VarGroup.NUMBER)){
            switch (type){
                case INT:
                    return (double)((Integer) value);
                case DOUBLE:
                    return (Double) value;
                case FLOAT:
                    return (double)((Float) value);
            }
            return null;
        }else{
            return null;
        }
    }
    /**
     * casts the value as a float or returns nothing if the encapsulated value is not float cast-able
     * @return Float or null
     */
    public @Nullable Float toFloat(){
        if (instanceOf(VarGroup.NUMBER)){
            switch (type){
                case INT:
                    return (float)((Integer) value);
                case DOUBLE:
                    return (float)((double) value);
                case FLOAT:
                    return (Float) value;
            }
            return null;
        }else{
            return null;
        }
    }
    /**
     * de-encapsulates the internal value as a boolean
     * @return Boolean or null
     */
    public @Nullable Boolean toBool(){
        if (instanceOf(VarType.BOOLEAN)){
            return (boolean) value;
        }else{
            return null;
        }
    }
    /**
     * de-encapsulates the internal value as a function
     * @return Function or null
     */
    public @Nullable Function toFunction(){
        if (instanceOf(VarType.FUNCTION)){
            return (Function) value;
        }else{
            return null;
        }
    }
    /**
     * de-encapsulates the internal value as a String
     * @return String or null
     * @see #asString() get as string instead of de-encapsulating
     */
    public @Nullable String toString(){
        if (type==VarType.STRING){
            return (String) value;
        }else if (instanceOf(VarType.BYTES)){
            return new String(((Bytes) value).getData(), StandardCharsets.UTF_8);
        }else{
            return null;
        }
    }
    /**
     * de-encapsulates the internal value as a Bytes object
     * @return Bytes or null
     */
    public @Nullable Bytes toBytes(){
        if (instanceOf(VarType.BYTES)){
            return (Bytes) value;
        }else if (instanceOf(VarType.STRING)){
            return new Bytes(((String) value).getBytes(StandardCharsets.ISO_8859_1));
        }else{
            return null;
        }
    }
    /**
     * casts the value as a list or returns nothing if the encapsulated value is not list cast-able
     * @return List or null
     */
    public @Nullable List toList(){
        if (value instanceof List){
            if (value instanceof Tuple tup){
                return new List(tup.toArray());
            }else{
                return (List) getValue();
            }
        }else{
            return null;
        }
    }
    /**
     * casts the value as a tuple or returns nothing if the encapsulated value is not tuple cast-able
     * @return Tuple or null
     */
    public @Nullable Tuple toTuple(){
        if (value instanceof List){
            if (value instanceof Tuple tup){
                return tup;
            }else{
                return new Tuple(((List) getValue()).toArray());
            }
        }else{
            return null;
        }
    }
    /**
     * de-encapsulates the internal value as a table
     * @return Table or null
     */
    public @Nullable Table toTable(){
        if (instanceOf(VarType.TABLE)){
            return (Table) getValue();
        }else{
            return null;
        }
    }

    /**
     * gets the type enum for this value
     * @return VarType
     */
    public VarType getType(){
        return type;
    }

    /**
     * tests to see if the values type matches the provided comparison type with exceptions for .Number and .ANY types
     * @param comparison the VarType to compare with
     * @return the result of the test preformed
     */
    public boolean instanceOf(VarType comparison){
        return comparison==type;
    }

    public boolean instanceOf(VarGroup comparison){
        if (comparison==VarGroup.ANY) return true;
        if (comparison==VarGroup.PRIMITIVE){
            switch (type){
                case NULL, FLOAT, INT, DOUBLE, STRING, BYTES, BOOLEAN: return true;
            }
        }
        if (comparison==VarGroup.NUMBER && type==VarType.INT) return true;
        if (comparison==VarGroup.NUMBER && type==VarType.DOUBLE) return true;
        return comparison == VarGroup.NUMBER && type == VarType.FLOAT;
    }

    public boolean instanceOf(VarFilter comparison){
        if (comparison==VarFilter.ANY) return true;
        if (comparison==VarFilter.PRIMITIVE){
            switch (type){
                case NULL, FLOAT, INT, DOUBLE, STRING, BYTES, BOOLEAN: return true;
            }
        }
        if (comparison==VarFilter.NUMBER && type==VarType.INT) return true;
        if (comparison==VarFilter.NUMBER && type==VarType.DOUBLE) return true;
        if (comparison == VarFilter.NUMBER && type == VarType.FLOAT) return true;
        return comparison.toType()==type;
    }

    /**
     * Asks the associated language if the value can be cast to the given type, falls back on {@link #instanceOf(VarType)} if no language is found
     */
    public boolean canCast(VarFilter type, Annotation[] annotations) {
        if (language != null) {
            return language.canCast(this, type, annotations);
        }else{
            if (this.type==VarType.INT && (type==VarFilter.DOUBLE || type==VarFilter.FLOAT)) return true;
            if (this.type==VarType.DOUBLE && (type==VarFilter.INT || type==VarFilter.FLOAT)) return true;
            if (this.type==VarType.FLOAT && (type==VarFilter.INT || type==VarFilter.DOUBLE)) return true;
            return instanceOf(type);
        }
    }

    /**
     * Asks the associated language if the value can be cast to the given type, falls back on {@link #instanceOf(VarType)} if no language is found
     */
    public boolean canCast(VarType type) {
        return canCast(type.toFilter(), new Annotation[0]);
    }

    /**
     * Trys to use the language associated with the value to cast the value, or falls back on standard casting functions, passes a list of annotations that can affect the casting process
     */
    public Value<?> castTo(VarType type, Annotation[] annotations) {
        if (language != null) {
            return language.castValue(this, type, annotations);
        }else{
            if (type == this.type) return this;
            return Value.of(switch (type) {
                case INT -> toInt();
                case DOUBLE -> toDouble();
                case FLOAT -> toFloat();
                case BOOLEAN -> toBool();
                case STRING -> toString();
                case FUNCTION -> toFunction();
                case TABLE -> toTable();
                case LIST -> toList();
                case TUPLE -> toTuple();
                case BYTES -> toBytes();
                default -> throw new IllegalStateException("Invalid casting operation");
            });
        }
    }

    /**
     * Trys to use the language associated with the value to cast the value, or falls back on standard casting functions
     */
    public Value<?> castTo(VarType type) {
        if (language != null) {
            return language.castValue(this, type, new Annotation[0]);
        }else{
            if (type == this.type) return this;
            return Value.of(switch (type) {
                case INT -> toInt();
                case DOUBLE -> toDouble();
                case FLOAT -> toFloat();
                case BOOLEAN -> toBool();
                case STRING -> toString();
                case FUNCTION -> toFunction();
                case TABLE -> toTable();
                case LIST -> toList();
                case TUPLE -> toTuple();
                case BYTES -> toBytes();
                default -> throw new IllegalStateException("Invalid casting operation");
            });
        }
    }

    /**
     * returns the non-Type protected direct value the instance was encapsulating
     * @return the raw value
     */
    @Deprecated
    public Type getValue() {
        return value;
    }

    /**
     * checks if this object represents a null instance
     * @return the state of the check
     */
    public boolean isNull(){
        return type == VarType.NULL;
    }

    /**
     * returns the type of this instance as a string
     * @return type this Value represents
     */
    public String typeName(){
        return VarName(type);
    }

    /**
     * represents a VarType enum as a string
     * @param type the enum being evaluated
     * @return name of the type
     */
    public static String VarName(VarType type){
        switch (type){
            case INT:
                return "int";
            case DOUBLE:
                return "double";
            case FLOAT:
                return "float";
            case BOOLEAN:
                return "boolean";
            case INVALID:
                return "invalid";
            case STRING:
                return "string";
            case TABLE:
                return "table";
            case LIST:
                return "list";
            case TUPLE:
                return "tuple";
            case FUNCTION:
                return "function";
            case EXCEPTION:
                return "exemption";
            case BYTES:
                return "bytes";
        }
        return "null";
    }

    /**
     * creates a string visualising the value, does not return encapsulated string values, see {@link String toString()}
     * @return string form of value
     */
    public String asString(){
        if (type==VarType.NULL) return "Value of <null>";
        return "Value of <"+typeName()+' '+getValue().toString()+'>';
    }

    @Override
    public boolean equals(Object obj){
        if (obj instanceof Value<?> _value){
            return _value.value.equals(value);
        }
        return value.equals(obj);
    }
}
