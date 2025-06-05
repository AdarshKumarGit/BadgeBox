package org.chubby.github.badgebox;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import java.util.List;
import java.util.ArrayList;

public class Badge {
    private final Identifier id;
    private final String name;
    private final List<String> lore;
    private final String category;
    private final ItemStack displayItem;
    private final ItemStack silhouetteItem;
    private final boolean isObtainable;

    public Badge(Identifier id, String name, List<String> lore, String category,
                 ItemStack displayItem, ItemStack silhouetteItem, boolean isObtainable) {
        this.id = id;
        this.name = name;
        this.lore = new ArrayList<>(lore);
        this.category = category;
        this.displayItem = displayItem;
        this.silhouetteItem = silhouetteItem;
        this.isObtainable = isObtainable;
    }

    public Identifier getId() { return id; }
    public String getName() { return name; }
    public List<String> getLore() { return new ArrayList<>(lore); }
    public String getCategory() { return category; }
    public ItemStack getDisplayItem() { return displayItem.copy(); }
    public ItemStack getSilhouetteItem() { return silhouetteItem.copy(); }
    public boolean isObtainable() { return isObtainable; }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup wrapperLookup) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", id.toString());
        nbt.putString("name", name);
        nbt.putString("category", category);
        nbt.putBoolean("obtainable", isObtainable);

        // Store lore
        NbtCompound loreNbt = new NbtCompound();
        for (int i = 0; i < lore.size(); i++) {
            loreNbt.putString(String.valueOf(i), lore.get(i));
        }
        nbt.put("lore", loreNbt);

        // Store items
        nbt.put("displayItem", displayItem.encode(wrapperLookup));
        nbt.put("silhouetteItem", silhouetteItem.encode(wrapperLookup));

        return nbt;
    }

    public static Badge fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapperLookup) {
        Identifier id = Identifier.of(nbt.getString("id"));
        String name = nbt.getString("name");
        String category = nbt.getString("category");
        boolean obtainable = nbt.getBoolean("obtainable");

        // Load lore
        List<String> lore = new ArrayList<>();
        NbtCompound loreNbt = nbt.getCompound("lore");
        int i = 0;
        while (loreNbt.contains(String.valueOf(i))) {
            lore.add(loreNbt.getString(String.valueOf(i)));
            i++;
        }

        // Load items
        ItemStack displayItem = ItemStack.fromNbt(wrapperLookup,nbt.getCompound("displayItem")).get();
        ItemStack silhouetteItem = ItemStack.fromNbt(wrapperLookup,nbt.getCompound("silhouetteItem")).get();

        return new Badge(id, name, lore, category, displayItem, silhouetteItem, obtainable);
    }
}