package org.chubby.github.badgebox.items;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import org.chubby.github.badgebox.BadgeBox;

public class ModItems
{

    public static final Item NAV_ARROW_LEFT = registerItem("nav_arrow_left", new Item(new Item.Settings()));
    public static final Item NAV_ARROW_RIGHT = registerItem("nav_arrow_right", new Item(new Item.Settings()));

    // Badge items
    public static final Item BADGE_SILHOUETTE = registerItem("badge_silhouette", new Item(new Item.Settings()));
    public static final Item BADGE_SILHOUETTE_IN_CASE = registerItem("badge_silhouette_in_case", new Item(new Item.Settings()));
    public static final Item BADGE = registerItem("badge", new BadgeItem(new Item.Settings()));

    // Display items
    public static final Item GENERIC_EMPTY_DISPLAY_SLOT = registerItem("generic_empty_display_slot", new Item(new Item.Settings()));
    public static final Item BADGE_CATEGORY_ITEM = registerItem("badge_category_item", new Item(new Item.Settings()));

    // GUI opener item
    //public static final Item BADGE_BOX_OPENER = registerItem("badge_box_opener", new BadgeBoxOpenerItem(new Item.Settings()));

    public static <T extends Item> T registerItem(String name, T item) {
        return Registry.register(Registries.ITEM, BadgeBox.id(name), item);
    }

    public static void init(){}
}
