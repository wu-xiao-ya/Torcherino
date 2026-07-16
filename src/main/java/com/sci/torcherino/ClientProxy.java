package com.sci.torcherino;

import com.sci.torcherino.client.ClientBlockRenderer;
import com.sci.torcherino.client.ClientEventHandler;
import com.sci.torcherino.network.KeyHandler;

import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy
{
	@Override
	public void preInit()
	{
		super.preInit();
		ClientBlockRenderer.init();
		KeyHandler.preInit();
		MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
	}
}
