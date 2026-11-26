package com.redtoast.simulation.FS;

import com.redtoast.simulation.FS.FileImplementations.Filepath;
import com.redtoast.simulation.annotations.Exposed;
import com.redtoast.simulation.base.Exposable;
import com.redtoast.simulation.base.ExposedError;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Bytes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FileHeader implements Exposable {
    public final Filepath internal;
    public final OpeningMode mode;
    public final GenericSystem fs;
    public boolean canRead;
    public boolean canWrite;
    public boolean open = true;
    public int cursor = 0;
    private byte[] byteBuffer = new byte[0];

    public FileHeader(Filepath filepath, GenericSystem fs, OpeningMode mode) {
        internal = filepath;
        this.mode = mode;
        this.fs = fs;
        canRead = mode.canRead();
        canWrite = mode.canWrite();
        try {
            if (filepath.exists() && !mode.truncate() && filepath.canRead()) {
                if (mode.binary()) {
                    byteBuffer = filepath.readAllBinary();
                } else {
                    byteBuffer = filepath.readAll().getBytes(StandardCharsets.UTF_8);
                }
            }
            if (mode.create())
                filepath.createNewFile();
        } catch (Throwable e) {
            if (e instanceof IOException) {
                throw new ExposedError(e.getMessage());
            } else {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Exposed
    public void flush() {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        try {
            if (mode.canWrite()) {
                if (mode.binary()) {
                    internal.writeBinary(byteBuffer);
                } else {
                    internal.write(byteBuffer);
                }
            }
        } catch (Throwable e) {
            if (e instanceof IOException) {
                throw new ExposedError(e.getMessage());
            } else {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }

    @Exposed
    public void close() {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        flush();
        open = false;
        byteBuffer = new byte[0];
    }

    @Exposed
    public int seek(String whence, int offset) {
        if (whence.equals("set")) {
            if (offset < 0)
                throw new ExposedError("Invalid offset");
            if (offset > byteBuffer.length)
                throw new ExposedError("Invalid offset");
            cursor = offset;
        } else if (whence.equals("cur")) {
            int notcursor = cursor + offset;
            if (notcursor < 0)
                throw new ExposedError("Invalid offset");
            if (notcursor > byteBuffer.length)
                throw new ExposedError("Invalid offset");
            cursor = notcursor;
        } else if (whence.equals("end")) {
            if (offset > 0)
                throw new ExposedError("Invalid offset");
            if (Math.abs(offset) > byteBuffer.length)
                throw new ExposedError("Invalid offset");
            cursor = byteBuffer.length + offset;
        } else {
            throw new ExposedError("Invalid option");
        }
        return cursor;
    }

    @Exposed
    public int seek(String whence) {
        return seek(whence, 0);
    }

    @Exposed
    public int seek() {
        return cursor;
    }

    @Exposed
    public int size() {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        return byteBuffer.length;
    }

    @Exposed
    public void truncate(int size) {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        if (!canWrite)
            throw new ExposedError("Access denied");
        if (size < 0)
            throw new ExposedError("Invalid size");
        if (size > byteBuffer.length && !fs.hasSpaceFor(size - byteBuffer.length))
            throw new ExposedError("Disk full");
        byte[] resized = new byte[size];
        System.arraycopy(byteBuffer, 0, resized, 0, Math.min(byteBuffer.length, size));
        byteBuffer = resized;
        flush();
    }

    @Exposed
    public void write(int bytes) {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        if (!canWrite)
            throw new ExposedError("Access denied");
        requireSpace(1);
        byte[] newBuffer = new byte[byteBuffer.length + 1];
        System.arraycopy(byteBuffer, 0, newBuffer, 0, byteBuffer.length);
        newBuffer[byteBuffer.length] = (byte) bytes;
        byteBuffer = newBuffer;
    }

    @Exposed
    public void write(Bytes bytes) {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        if (!canWrite)
            throw new ExposedError("Access denied");
        byte[] dataToWrite;
        if (mode.binary()) {
            dataToWrite = bytes.getData();
        } else {
            dataToWrite = new String(bytes.getData(), StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        }
        requireSpace(dataToWrite.length);
        byte[] newBuffer = new byte[byteBuffer.length + dataToWrite.length];
        System.arraycopy(byteBuffer, 0, newBuffer, 0, byteBuffer.length);
        System.arraycopy(dataToWrite, 0, newBuffer, byteBuffer.length, dataToWrite.length);
        byteBuffer = newBuffer;
    }

    private void requireSpace(int growth) {
        if (growth > 0 && !fs.hasSpaceFor(growth)) throw new ExposedError("Disk full");
    }

    @Exposed
    public Value read() {
        return read("l");
    }

    private int indexof(byte[] data, char charicter) {
        for (int i = 0; i < data.length; i++) {
            if (data[i] == (byte) charicter)
                return i;
        }
        return -1;
    }

    @Exposed
    public Value read(String format) {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        if (!canRead)
            throw new ExposedError("Access denied");
        if (format.length() != 1)
            throw new ExposedError("Invalid format");
        if (byteBuffer.length == cursor)
            return null;

        return switch (format.charAt(0)) {
            case 'l', 'L' -> {
                int remainingLength = byteBuffer.length - cursor;
                byte[] remainingData = new byte[remainingLength];
                System.arraycopy(byteBuffer, cursor, remainingData, 0, remainingLength);

                if (mode.binary()) {
                    int endpoint = indexof(remainingData, '\n');
                    if (endpoint == -1) {
                        cursor = byteBuffer.length;
                        yield Value.of(remainingData);
                    }
                    cursor = cursor + endpoint + 1;
                    if (format.charAt(0) == 'l') {
                        byte[] result = new byte[endpoint];
                        System.arraycopy(remainingData, 0, result, 0, endpoint);
                        yield Value.of(result);
                    } else {
                        byte[] result = new byte[endpoint + 1];
                        System.arraycopy(remainingData, 0, result, 0, endpoint);
                        result[endpoint] = (byte) '\n';
                        yield Value.of(result);
                    }
                } else {
                    String text = new String(remainingData, StandardCharsets.UTF_8);
                    int endpoint = text.indexOf("\n");
                    if (endpoint == -1) {
                        cursor = byteBuffer.length;
                        yield Value.of(text);
                    }
                    cursor = cursor + endpoint + 1;
                    if (format.charAt(0) == 'l') {
                        yield Value.of(text.substring(0, endpoint));
                    } else {
                        yield Value.of(text.substring(0, endpoint + 1));
                    }
                }
            }
            case 'a' -> {
                int remainingLength = byteBuffer.length - cursor;
                byte[] data = new byte[remainingLength];
                System.arraycopy(byteBuffer, cursor, data, 0, remainingLength);
                cursor = byteBuffer.length;

                if (mode.binary()) {
                    yield Value.of(data);
                } else {
                    yield Value.of(new String(data, StandardCharsets.UTF_8));
                }
            }
            default -> throw new ExposedError("Invalid format");
        };
    }

    @Exposed
    public Value read(int amount) {
        if (!open)
            throw new ExposedError("Attempt to use a closed file");
        if (!canRead)
            throw new ExposedError("Access denied");

        int newcur = cursor + amount;
        if (newcur < 0) {
            newcur = 0;
        }
        if (newcur > byteBuffer.length) {
            newcur = byteBuffer.length;
        }
        if (newcur == cursor)
            return Value.of("");

        int startPos = Math.min(newcur, cursor);
        int endPos = Math.max(newcur, cursor);
        int dataLength = endPos - startPos;

        if (mode.binary()) {
            byte[] data = new byte[dataLength];
            System.arraycopy(byteBuffer, startPos, data, 0, dataLength);
            cursor = newcur;
            return Value.of(data);
        } else {
            byte[] data = new byte[dataLength];
            System.arraycopy(byteBuffer, startPos, data, 0, dataLength);
            cursor = newcur;
            return Value.of(new String(data, StandardCharsets.UTF_8));
        }
    }
}
