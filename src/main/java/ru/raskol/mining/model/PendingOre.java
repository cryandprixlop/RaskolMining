package ru.raskol.mining.model;

/** Добытая руда, ждущая регенерации на своём месте. */
public final class PendingOre {
    public String world;
    public int x, y, z;
    public String material;
    public long restoreAtEpochSeconds;
}
