package com.redtoast.simulation.FS;

public record Partition(String path, boolean readOnly, boolean hidden, String source) {
}
