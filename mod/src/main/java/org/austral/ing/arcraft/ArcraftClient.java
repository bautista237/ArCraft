package org.austral.ing.arcraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only entry point. Registers the in-game Config screen (Mods → ArCraft → Config):
 * NeoForge auto-generates the UI from {@link ArcraftConfig}'s spec, so every TOML option is
 * editable in-game. This class never loads on a dedicated server ({@code dist = Dist.CLIENT}).
 */
@Mod(value = ArcraftMod.MODID, dist = Dist.CLIENT)
public class ArcraftClient {

    public ArcraftClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
