package com.redtoast.Compat;

import com.redtoast.simulation.value.Value;

import java.util.function.Supplier;

public class MainThreadTicket {
    private boolean complete = false;
    private Supplier<Value> function;
    private Value answer = null;

    public MainThreadTicket(Supplier<Value> function) {
        this.function = function;
    }

    public boolean isComplete() {
        return complete;
    }

    public void call() {
        answer = function.get();
        complete = true;
    }

    public Value getAnswer() {
        return answer;
    }
}
