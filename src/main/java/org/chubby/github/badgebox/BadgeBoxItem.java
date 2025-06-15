package org.chubby.github.badgebox;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.chubby.github.badgebox.BadgeEditorScreenHandler;

public class BadgeBoxItem extends Item {

    public BadgeBoxItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);

        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            // Open the badge editor for the player
            serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    BadgeEditorScreenHandler::new,
                    Text.literal("Badge Editor - " + user.getName().getString())
            ));
        }

        return new TypedActionResult<>(ActionResult.SUCCESS, itemStack);
    }
}