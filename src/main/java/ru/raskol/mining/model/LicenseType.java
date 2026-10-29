package ru.raskol.mining.model;

/**
 * Типы лицензий шахтёра.
 * extendable = false: купить новую можно только после окончания текущей (без продления заранее).
 * Цена и срок читаются из конфига по configPath + ".default-price" / ".duration-days".
 */
public enum LicenseType {

    MINER("miner", "license", "шахтёра", true),
    GOLD("gold", "license.gold", "на золото", false);

    public final String id;
    public final String configPath;
    public final String title;
    public final boolean extendable;

    LicenseType(String id, String configPath, String title, boolean extendable) {
        this.id = id;
        this.configPath = configPath;
        this.title = title;
        this.extendable = extendable;
    }

    public static LicenseType byId(String id) {
        if (id == null) return null;
        for (LicenseType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
