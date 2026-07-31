package shit.zen.modules;

public enum Category {
    COMBAT("Combat"),
    MOVEMENT("Movement"),
    PLAYER("Player"),
    RENDER("Render"),
    EXPLOIT("Exploit"),
    WORLD("World"),
    MISC("Misc");

    public String displayName;

    Category(String string2) {
        this.displayName = string2;
    }

    public static Category fromString(String string) {
        for (Category category : Category.values()) {
            if (!category.displayName.equalsIgnoreCase(string)) continue;
            return category;
        }
        return COMBAT;
    }
}