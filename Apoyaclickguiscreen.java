package net.wurstclient.clickgui.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class Apoyaclickguiscreen extends Screen
{
	public Apoyaclickguiscreen()
	{
		super(Component.literal("Apoya Basan Client"));
	}
	
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY,
		float delta)
	{
		super.render(graphics, mouseX, mouseY, delta);
		
		// yarı saydam kutu
		int x1 = width / 2 - 100;
		int y1 = height / 2 - 40;
		int x2 = width / 2 + 100;
		int y2 = height / 2 + 40;
		graphics.fill(x1, y1, x2, y2, 0xAA000000);
		
		// başlık yazısı
		graphics.drawString(font, "Apoya Basan Client", x1 + 10, y1 + 10,
			0xFFFFFFFF);
	}
	
	@Override
	public boolean isPauseScreen()
	{
		return false;
	}
}
