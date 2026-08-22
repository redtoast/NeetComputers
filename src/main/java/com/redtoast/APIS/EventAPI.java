package com.redtoast.APIS;

import com.redtoast.Computer;
import com.redtoast.simulation.annotations.CanBeNull;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.events.EventGeneric;
import com.redtoast.simulation.events.EventLabel;
import com.redtoast.simulation.events.EventManager;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.List;
import com.redtoast.simulation.value.ValueTypes.Tuple;

public class EventAPI implements API {
    EventManager eventManager;

    public EventAPI(Computer computer) {
        this.eventManager = computer.getEventManager();
    }

    private EventLabel decodeEventLabel(String string) {
        for (EventLabel label : EventLabel.values()) {
            if (label.name().equals(string.toUpperCase())) return label;
        }
        throw new ExposedError("Invalid event category '"+string+"'");
    }

    @Exposed
    public void queueEvent(String category, String eventName, Tuple args){
        eventManager.queueEvent(new EventGeneric(eventName, args), category.equalsIgnoreCase("all") ? EventLabel.UNLABELED : decodeEventLabel(category));
    }

    @Exposed
    public Value<List> getQueue(String category, @CanBeNull String filter) {
        return filter==null ? Value.of(eventManager.getQueue(decodeEventLabel(category))) : Value.of(eventManager.getQueue(decodeEventLabel(category), filter));
    }

    @Exposed
    public Value<?> getFirst(String category, @CanBeNull String filter) {
        return filter==null ? Value.of(eventManager.getFirst(decodeEventLabel(category))) : Value.of(eventManager.getFirst(decodeEventLabel(category), filter));
    }

    @Exposed
    public void clear(@CanBeNull String category){
        if (category==null){
            eventManager.reset();
        }else{
            eventManager.clear(decodeEventLabel(category));
        }
    }

    @Override
    public String getLabel() {
        return "event";
    }
}
