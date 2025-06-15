package org.chubby.github.badgebox.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class BadgeboxClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("BadgeboxClient");

    // Key bindings
    public static KeyBinding OPEN_BADGE_EDITOR_KEY;
    public static KeyBinding OPEN_BADGE_DISPLAY_KEY;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Badge Box Client");

        // Register client networking
        BadgeClientNetworking.registerClientHandlers();

        // Register key bindings
        registerKeyBindings();

        // Register client events
        registerClientEvents();
    }

    private void registerKeyBindings() {
        OPEN_BADGE_EDITOR_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.badgebox.open_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.badgebox.keys"
        ));

        OPEN_BADGE_DISPLAY_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.badgebox.open_display",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.badgebox.keys"
        ));
    }

    private void registerClientEvents() {
        // Handle key presses
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            // Register tick event for key handling
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(clientInstance -> {
                while (OPEN_BADGE_EDITOR_KEY.wasPressed()) {
                    if (clientInstance.player != null) {
                        BadgeClientNetworking.openBadgeEditor();
                    }
                }

                while (OPEN_BADGE_DISPLAY_KEY.wasPressed()) {
                    if (clientInstance.player != null) {
                        BadgeClientNetworking.openBadgeDisplay(clientInstance.player.getUuid());
                    }
                }
            });
        });

        // Clear cache on disconnect
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            BadgeClientNetworking.clearCache();
        });
    }
}