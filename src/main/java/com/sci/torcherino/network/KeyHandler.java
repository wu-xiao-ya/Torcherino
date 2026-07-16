package com.sci.torcherino.network;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

public class KeyHandler
{
	public static KeyBinding usageKey;
	public static void preInit()
	{
		usageKey = new KeyBinding("key.torcherino.useage_key", 42, "key.categories.gameplay");
        ClientRegistry.registerKeyBinding(usageKey);
	}
}
