package com.redtoast.APIS.graphics;

import com.redtoast.Computer;
import com.redtoast.graphics.RGBGraphicsArray;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.ValueTypes.Table;

public class ScreenAPI extends DrawableGraphicalAPI implements API {
    Computer computer;

    public ScreenAPI(Computer computer) {
        super(computer.getGraphics(), computer.getRuntime());
        this.computer = computer;
    }

    @Override
    public String getLabel() {
        return "screen";
    }

    @Exposed
    @Override
    public void draw(){
        super.draw();
        computer.renderColorGraphics();
    }
}
