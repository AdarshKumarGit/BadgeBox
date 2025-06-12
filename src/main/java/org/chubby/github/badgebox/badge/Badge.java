package org.chubby.github.badgebox.badge;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.chubby.github.badgebox.items.BadgeItem;

public class Badge {
    private final String id;
    private final String name;
    private final String category;
    private final String description;
    private final String texture;
    private final boolean obtainable;

    public Badge(String id, String name, String category, String description, String texture, boolean obtainable) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.texture = texture;
        this.obtainable = obtainable;
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public String getTexture() { return texture; }
    public boolean isObtainable() { return obtainable; }

    // Create ItemStack from badge
    public ItemStack createItemStack() {
        return BadgeItem.createBadge(id, name, category, description);
    }

    // JSON serialization
    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.addProperty("category", category);
        obj.addProperty("description", description);
        obj.addProperty("texture", texture);
        obj.addProperty("obtainable", obtainable);
        return obj;
    }

    public static Badge fromJson(JsonObject obj) {
        return new Badge(
                obj.get("id").getAsString(),
                obj.get("name").getAsString(),
                obj.get("category").getAsString(),
                obj.get("description").getAsString(),
                obj.get("texture").getAsString(),
                obj.get("obtainable").getAsBoolean()
        );
    }
}
