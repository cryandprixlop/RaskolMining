package ru.raskol.mining.model;

/**
 * Тиры кирок. VANILLA = обычная ванильная кирка (без PDC-тега).
 * dropsFragment — какой фрагмент роняет эта кирка.
 * craftFragment — какой фрагмент нужен для крафта этой кирки.
 */
public enum PickTier {

    VANILLA("§7Обычная кирка", "§7", "VETERAN", null, null),
    VETERAN("§eКирка бывалого", "§e", "DWARF", "VETERAN", "§eФрагмент кирки бывалого"),
    DWARF("§bКирка гнома", "§b", "GOBLIN", "DWARF", "§bФрагмент кирки гнома"),
    GOBLIN("§5Кирка гоблина", "§5", null, "GOBLIN", "§5Фрагмент кирки гоблина");

    public final String displayName;
    public final String color;
    public final String fragmentDisplayName;
    private final String dropsFragmentName;
    private final String craftFragmentName;

    PickTier(String displayName, String color, String dropsFragmentName,
             String craftFragmentName, String fragmentDisplayName) {
        this.displayName = displayName;
        this.color = color;
        this.dropsFragmentName = dropsFragmentName;
        this.craftFragmentName = craftFragmentName;
        this.fragmentDisplayName = fragmentDisplayName;
    }

    public static PickTier fromName(String name) {
        if (name == null) return VANILLA;
        try {
            return PickTier.valueOf(name);
        } catch (IllegalArgumentException e) {
            return VANILLA;
        }
    }

    public PickTier dropsFragment() {
        return dropsFragmentName == null ? null : valueOf(dropsFragmentName);
    }

    public PickTier craftFragment() {
        return craftFragmentName == null ? null : valueOf(craftFragmentName);
    }
}
