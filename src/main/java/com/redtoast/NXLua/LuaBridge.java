package com.redtoast.NXLua;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public abstract class LuaBridge {
    public static void load(String... paths) {
        for (String path : paths) System.load(path);
    }

    private final long pointer;
    private volatile boolean closed = false;
    private final Map<Integer, LuaValue.Function> functionMemory = new Hashtable<>();
    private int rollingPointer = 0;

    public LuaBridge() {
        pointer = create();
    }

    private native long create();
    public native int getVersion();
    private native void initRaw(String code, int batchSize, int batches, long pointer);

    public void init(String code, int batchSize, int batches) {
        if (closed) throw new IllegalStateException("Bridge closed");
        initRaw(code, batchSize, batches, pointer);
    }

    private native void tick(long pointer);
    private native void runString(String code, long pointer);
    private native void yield(long pointer);

    private native void release(long pointer);

    private native void pushNil(long pointer);
    private native void pushBool(boolean bool, long pointer);
    private native void pushInt(int integer, long pointer);
    private native void pushFloat(double floatingPoint, long pointer);
    private native void pushString(String string, long pointer);
    private native void pushBinary(byte[] data, long pointer);
    private native void pushFunc(int id, long pointer);
    private native void pushError(String string, long pointer);

    private native void startTable(int preallc, long pointer);
    private native void pushPair(long pointer);
    private native long tableIdentity(int index, long pointer);
    private native void startList(int preallc, long pointer);
    private native void pushList(int index, long pointer);

    private native void setGlobal(String string, long pointer);

    private void push(LuaValue value) {
        switch (value.getType()){
            case NIL -> pushNil(pointer);
            case BOOLEAN -> pushBool((boolean) value.getValue(), pointer);
            case NUMINT -> pushInt((int) value.getValue(), pointer);
            case NUMFLOAT -> pushFloat((double) value.getValue(), pointer);
            case STRING -> pushString((String) value.getValue(), pointer);
            case BINARY -> pushBinary((byte[]) value.getValue(), pointer);
            case FUNCTION -> {
                functionMemory.put(rollingPointer, (LuaValue.Function) value.getValue());
                pushFunc(rollingPointer++, pointer);
            }
            case LIST -> {
                LuaValue[] list = (LuaValue[]) value.getValue();
                startList(list.length, pointer);
                for (int i = 0; i < list.length;) {
                    push(list[i++]);
                    pushList(i, pointer);
                }
            }
            case TABLE -> {
                Map<LuaValue, LuaValue> table = (Map<LuaValue, LuaValue>) value.getValue();
                startTable(table.size(), pointer);
                for (Map.Entry<LuaValue, LuaValue> pair : table.entrySet()) {
                    push(pair.getKey());
                    push(pair.getValue());
                    pushPair(pointer);
                }
            }
            case ERROR -> pushError((String) value.getValue(), pointer);
            case INVALID -> pushString("Invalid Data", pointer);
            case OPAQUE -> pushString("[" + (String) value.getValue() + "]", pointer);
        }
    }

    private native void pop(int index, long pointer);
    private native int getType(int index, long pointer);
    private native int absoluteIndex(int index, long pointer);

    private native boolean getBool(int index, long pointer);
    private native int getInt(int index, long pointer);
    private native double getFloat(int index, long pointer);
    private native String getString(int index, long pointer);

    private native int pullTable(int index, long pointer);
    private native boolean nextTable(int index, long pointer);

    private LuaValue pull(int index, List<Long> seen) {
        index = absoluteIndex(index, pointer);
        return switch (getType(index, pointer)) {
            case (-2) -> {
                int id = getInt(-1, pointer);
                pop(1, pointer);
                pop(1, pointer);
                if (functionMemory.containsKey(id)) yield  LuaValue.from(functionMemory.get(id));
                yield  LuaValue.from();
            }
            case (-1) -> LuaValue.from(getInt(index, pointer));
            case (0) -> LuaValue.from();
            case (1) -> LuaValue.from(getBool(index, pointer));
            case (2) -> LuaValue.opaque("userdata");
            case (3) -> LuaValue.from(getFloat(index, pointer));
            case (4) -> LuaValue.from(getString(index, pointer));
            case (6) -> LuaValue.opaque("function");
            case (7) -> LuaValue.opaque("userdata");
            case (8) -> LuaValue.opaque("thread");
            case (5) -> {
                long id = tableIdentity(index, pointer);
                if (seen.contains(id)) yield LuaValue.invalid();
                seen.add(id);
                int reportedLength = pullTable(index, pointer);
                int placesFilled = 0;
                int realLen = 0;
                LuaValue[] listPrototype = new LuaValue[reportedLength];
                Map<LuaValue, LuaValue> map = new Hashtable<>();
                boolean isList = true;
                while (nextTable(index, pointer)) {
                    realLen++;
                    LuaValue key = pull(-2, seen);
                    LuaValue value = pull(-1, seen);
                    pop(1, pointer);
                    if (key.getType() == LuaValue.Type.INVALID || value.getType() == LuaValue.Type.INVALID) continue;
                    map.put(key, value);
                    if (key.getType() == LuaValue.Type.NUMINT && isList) {
                        int intkey = (int) key.getValue();
                        if (intkey>listPrototype.length || intkey < 1){
                            isList = false;
                        }else{
                            listPrototype[intkey-1] = value;
                            placesFilled++;
                        }
                    }else{
                        isList = false;
                    }
                }
                if (realLen == reportedLength && realLen == placesFilled && isList) {
                    yield LuaValue.from(listPrototype);
                }else{
                    yield LuaValue.from(map);
                }
            }
            default -> LuaValue.invalid();
        };
    }

    private int functionCall(int id, int amount) {
        if (functionMemory.containsKey(id)) {
            LuaValue.Function func = functionMemory.get(id);
            LuaValue[] values = new LuaValue[amount];
            for (int i = 0; i < amount;) values[i++] = pull(i, new ArrayList<>());
            LuaValue[] returns = func.call(values);
            for (int i = 0; i < amount; i++) pop(1, pointer);
            for (LuaValue value : returns) {
                if (value.getType() == LuaValue.Type.ERROR) {
                    pushString((String) value.getValue(), pointer);
                    return -1;
                }
            }
            for (LuaValue value : returns) push(value);
            return returns.length;
        }else{
            for (int i = 0; i < amount; i++) pop(1, pointer);
            pushString("Lost function reference (how did you get here?)", pointer);
            return -1;
        }
    }

    private void cullFunction(int id) {
        functionMemory.remove(id);
    }

    public void setGlobal(String key, LuaValue value){
        if (closed) throw new IllegalStateException("Bridge closed");
        push(value);
        setGlobal(key, pointer);
    }

    public abstract void print(String text);
    public abstract void error(String error);
    public abstract void shutDown();
    public abstract void log(String message);
    public abstract boolean isAlive();

    @Deprecated
    public void runString(String code) {
        if (closed) throw new IllegalStateException("Bridge closed");
        runString(code, pointer);
    }

    public void tick(){
        if (closed) throw new IllegalStateException("Bridge closed");
        if (!isAlive()) return;
        if (closed) return;
        tick(pointer);
    }

    public void yield(){
        if (closed) throw new IllegalStateException("Bridge closed");
        this.yield(pointer);
    }

    public boolean isClosed(){
        return closed;
    }

    public final void close() {
        if (!closed) {
            release(pointer);
            functionMemory.clear();
            closed = true;
        }
    }
}
