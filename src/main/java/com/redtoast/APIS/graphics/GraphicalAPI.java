package com.redtoast.APIS.graphics;

import com.redtoast.graphics.RGBGraphicsArray;
import com.redtoast.simulation.APILoader;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.annotations.CanBeNull;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.Exposable;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.parameterErrors.RangeArgumentError;
import com.redtoast.simulation.value.ValueTypes.Bytes;
import com.redtoast.simulation.value.ValueTypes.Table;
import com.redtoast.simulation.value.ValueTypes.Tuple;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.stream.IntStream;

public class GraphicalAPI implements Exposable {
    private Runtime runtime;
    private final int height;
    private final int width;
    /* Packed RGB color buffer */
    private int[] buffer;

    public GraphicalAPI(int sizeX, int sizeY, Runtime runtime) {
        this.width = sizeX;
        this.height = sizeY;
        this.runtime = runtime;
        buffer = new int[sizeX * sizeY];
    }

    public void copyTo(RGBGraphicsArray graphicsArray) {
        for (int i = 0; i < height;) graphicsArray.pixels[i] = Arrays.copyOfRange(buffer, i++ * width, i * width);
    }

    public static int blend(int RGB, int RGBA) {
        int A = RGBA & 0xFF;
        RGBA >>= 8;
        int output = (int) (((RGBA & 0xFF0000L) * A + (RGB & 0xFF0000L) * (255-A)) / 255) & 0xFF0000;
        output |= ((RGBA & 0xFF00) * A + (RGB & 0xFF00) * (255-A)) / 255 & 0xFF00;
        return output | ((RGBA & 0xFF) * A + (RGB & 0xFF) * (255-A)) / 255 & 0xFF;
    }

    @Exposed
    public Tuple getSize() {
        return new Tuple(width, height);
    }

    @Exposed
    public void drawPixel(int x, int y, int RGBA) {
        if (0 > x || x >= width || 0 > y || y >= height) return;
        if ((RGBA & 0x000000FF) == 255) buffer[x + y * width] = RGBA >> 8;
        buffer[x + y * width] = blend(buffer[x + y * width], RGBA);
    }

    @Exposed
    public void drawLine(int x1, int y1, int x2, int y2, int RGBA) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            drawPixel(x1, y2, RGBA);

            if (x1 == x2 && y1 == y2)
                break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err = err - dy;
                x1 = x1 + sx;
            }
            if (e2 < dx) {
                err = err + dx;
                y1 = y1 + sy;
            }
        }
    }

    @Exposed
    public void substitute(int src, int dest) {
        final int flatSrc = src & 0xFFFFFF;
        final int flatDest = dest & 0xFFFFFF;
        buffer = Arrays.stream(buffer).map((check) -> check == flatSrc ? flatDest : check).toArray();
    }

    public int[] readSector(int x1, int y1, int x2, int y2) {
        int width = x2 - x1 + 1;
        int height = y2 - y1 + 1;
        int[] scan = new int[width * height];
        int stop = y2 * this.width + x1;
        int y = 0;
        for (int i = y1 * this.width + x1; i <= stop; i += this.width) {
            System.arraycopy(buffer, i, scan, y, width);
            y += width;
        }
        return scan;
    }

    @Exposed
    public Table clone(int x1, int y1, int x2, int y2) {
        /* clean params*/
        if (x1 > x2) throw new ExposedError("x2 must be larger then x1");
        if (y1 > y2) throw new ExposedError("y2 must be larger then y1");
        if (x1 < 0) throw new RangeArgumentError(0, 0, width-1, x1);
        if (y1 < 0) throw new RangeArgumentError(1, 0, height-1, y1);
        if (x2 >= width) throw new RangeArgumentError(2, 0, width-1, x2);
        if (y2 >= height) throw new RangeArgumentError(3, 0, height-1, y2);
        /*copy area into new layer*/
        GraphicalAPI layer = new GraphicalAPI(x2 - x1, y2 - y1, runtime);
        layer.buffer = readSector(x1, y1, x2, y2);
        return APILoader.TableizeAPI(layer, runtime);
    }

    @Exposed
    public Bytes readData(int x1, int y1, int x2, int y2) {
        /* clean params*/
        if (x1 > x2) throw new ExposedError("x2 must be larger then x1");
        if (y1 > y2) throw new ExposedError("y2 must be larger then y1");
        if (x1 < 0) throw new RangeArgumentError(0, 0, width-1, x1);
        if (y1 < 0) throw new RangeArgumentError(1, 0, height-1, y1);
        if (x2 >= width) throw new RangeArgumentError(2, 0, width-1, x2);
        if (y2 >= height) throw new RangeArgumentError(3, 0, height-1, y2);
        /*get area as a buffer*/
        byte[] data = ArrayUtils.toPrimitive(Arrays.stream(readSector(x1, y1, x2, y2)).mapMulti((value, ic) -> {
            for (int i = 16; i >= 0; i -= 8) {
                ic.accept(value >> i & 0xFF);
            }
            ic.accept(0xFF);
        }).mapToObj(I -> (byte) I).toArray(Byte[]::new));
        return new Bytes(data);
    }

    @Exposed
    public void writeData(int x, int y, byte[] buffer, int width) {
        if (buffer.length%4!=0) throw new ExposedError("Length of buffer must by dividable by 4");
        if ((buffer.length / 4)%width!=0) throw new ExposedError("Length of buffer must by dividable by width");
        int height = buffer.length / width / 4;
        if (x < 0) throw new RangeArgumentError(0, 0, width-1, x);
        if (y < 0) throw new RangeArgumentError(1, 0, height-1, y);
        if (y + height > this.height || x + width > this.width) throw new ExposedError("Draw call extends past valid bounds");
        /*read existing data and apply opacity*/
        int[] scan = readSector(x, y, x + width - 1, y + height - 1);
        int[] mapped = IntStream.range(0, buffer.length / 4).map(index -> {
            int byteIndex = index * 4;
            int r = buffer[byteIndex] & 0xFF;
            int g = buffer[byteIndex + 1] & 0xFF;
            int b = buffer[byteIndex + 2] & 0xFF;
            int alpha = buffer[byteIndex + 3] & 0xFF;
            if (alpha == 0xFF) return r << 16 | g << 8 | b;
            if (alpha == 0) return scan[index];
            return blend(scan[index], r << 24 | g << 16 | b << 8 | alpha);
        }).toArray();
        /*draw to internal buffer*/
        for (int i = 0; i < height; i++) {
            System.arraycopy(mapped, i * width, this.buffer, (y + i) * this.width + x, width);
        }
    }

    @Exposed
    public void set(@CanBeNull Integer RGBA) {
        if (RGBA==null || RGBA==0xFF) {
            buffer = new int[width * height];
            return;
        }
        int A = RGBA & 0xFF;
        if (A == 255) Arrays.fill(buffer, RGBA>>8);
        buffer = Arrays.stream(buffer).map(RGB -> blend(RGB, RGBA)).toArray();
    }

    @Exposed
    public void fill(int x1, int y1, int x2, int y2, int RGBA) {
        /* clean params*/
        if (x1 > x2) throw new ExposedError("x2 must be larger then x1");
        if (y1 > y2) throw new ExposedError("y2 must be larger then y1");
        if (x1 < 0) throw new RangeArgumentError(0, 0, width - 1, x1);
        if (y1 < 0) throw new RangeArgumentError(1, 0, height - 1, y1);
        if (x2 >= width) throw new RangeArgumentError(2, 0, width - 1, x2);
        if (y2 >= height) throw new RangeArgumentError(3, 0, height - 1, y2);
        int width = x2 - x1 + 1;
        if ((RGBA & 0xFF) == 0xFF) {
            /*Create a row and copy it into the buffer*/
            int[] row = new int[width];
            Arrays.fill(row, RGBA>>8);
            for (int i = y1; i < y2; i++) {
                System.arraycopy(row, 0, buffer, i * this.width + x1, width);
            }
        }else{
            /*scan and apply transparency*/
            int height = y2 - y1 + 1;
            int[] scan = readSector(x1, y1, x2, y2);
            int[] mapped = Arrays.stream(scan).map(RGB -> blend(RGB, RGBA)).toArray();
            for (int i = 0; i < height; i++) {
                System.arraycopy(mapped, i * width, buffer, (y1 + i) * this.width + x1, width);
            }
        }
    }
}
