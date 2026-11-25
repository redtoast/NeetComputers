package com.redtoast.APIS;

import com.redtoast.blocks.Generics.Displays.BinaryGraphicsProvider;
import com.redtoast.graphics.BinaryGraphicsArray;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.annotations.Index;
import com.redtoast.simulation.base.API;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.ValueTypes.Tuple;

public class HeadsUpAPI implements API {
    BinaryGraphicsProvider binaryGraphicsProvider;
    BinaryGraphicsArray graphics;
    int sizex, sizey;
    public HeadsUpAPI(BinaryGraphicsProvider computer) {
        this.binaryGraphicsProvider = computer;
        sizex = this.binaryGraphicsProvider.getBinaryGraphics().getSize().x;
        sizey = this.binaryGraphicsProvider.getBinaryGraphics().getSize().y;
        graphics = new BinaryGraphicsArray(sizex, sizey);
    }

    @Exposed
    public void setPixel(@Index int x, @Index int y, boolean state){
        if (x<0 || y<0 || x>=sizex || y>=sizey) {
            throw new ExposedError("values not in allowed range");
        }
        graphics.set(x,y,state);
    }

    @Exposed
    public Tuple getSize(){
        return new Tuple(sizex, sizey);
    }

    @Exposed
    public void drawLine(@Index int x1, @Index int y1, @Index int x2, @Index int y2){
        if (x1<0 || x2<0 || y1<0 || y2<0 || x1>=sizex || x2>=sizex || y1>=sizey || y2>=sizey) {
            throw new ExposedError("values not in allowed range");
        }

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;

        int err = dx-dy;
        int e2;

        while (true)
        {
            graphics.set(x1,y1,true);

            if (x1 == x2 && y1 == y2)
                break;

            e2 = 2 * err;
            if (e2 > -dy)
            {
                err = err - dy;
                x1 = x1 + sx;
            }

            if (e2 < dx)
            {
                err = err + dx;
                y1 = y1 + sy;
            }
        }
    }

    @Exposed
    public void fill(@Index int x1, @Index int y1, @Index int x2, @Index int y2, boolean state){
        if (x1<0 || x2<0 || y1<0 || y2<0 || x1>=sizex || x2>=sizex || y1>=sizey || y2>=sizey) {
            throw new ExposedError("values not in allowed range");
        }
        for (int x = Math.min(x1,x2); x <= Math.max(x1,x2); x++){
            for (int y = Math.min(y1,y2); y <= Math.max(y1,y2); y++){
                graphics.set(x,y,state);
            }
        }
    }

    @Exposed
    public void draw(){
        binaryGraphicsProvider.setBinaryGraphics(graphics);
    }

    @Exposed
    public void set(boolean state){
        if (state) {
            fill(0, 0, sizex-1, sizey-1, true);
        }else{
            graphics = new BinaryGraphicsArray(sizex,sizey);
        }
    }

    @Override
    public String getLabel() {
        return "headsup";
    }
}
