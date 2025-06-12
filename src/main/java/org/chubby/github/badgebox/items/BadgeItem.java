package org.chubby.github.badgebox.items;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class BadgeItem extends Item
{
    public BadgeItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            // Handle badge item usage if needed
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    // Create a badge item with specific data
    public static ItemStack createBadge(String badgeId, String name, String category, String description) {
        ItemStack badge = new ItemStack(ModItems.BADGE);
        NbtComponent comp = badge.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = comp.getNbt();
        nbt.putString("badgeId", badgeId);
        nbt.putString("badgeName", name);
        nbt.putString("badgeCategory", category);
        nbt.putString("badgeDescription", description);

        badge.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return badge;
    }
}
