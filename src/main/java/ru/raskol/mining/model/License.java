package ru.raskol.mining.model;

public final class License {
    public String uuid;
    public String name;
    public long expiresAtEpochSeconds;

    public boolean isValid() {
        return expiresAtEpochSeconds > System.currentTimeMillis() / 1000L;
    }

    public String remainingFormatted() {
        long s = Math.max(0, expiresAtEpochSeconds - System.currentTimeMillis() / 1000L);
        long days = s / 86400;
        long hours = (s % 86400) / 3600;
        long mins = (s % 3600) / 60;
        if (days > 0) return days + " дн. " + hours + " ч";
        if (hours > 0) return hours + " ч " + mins + " мин";
        return mins + " мин";
    }
}
