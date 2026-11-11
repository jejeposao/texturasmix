package com.texturasmix;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;

/** Ponte entre a tela comum e o Screen do Minecraft 1.20.1. */
abstract class TelaBase extends Screen {
	TelaBase() { super(Text.literal("Texturas Mix")); }

	abstract void aoAbrir();

	abstract void aoFechar();

	abstract void desenhar(G c, int mouseX, int mouseY);

	abstract boolean clique(double x, double y, int botao);

	abstract boolean rolar(double x, double y, double quanto);

	abstract boolean tecla(int key, int scan, boolean ctrl, boolean colar);

	abstract boolean caractere(char ch);

	// ---------- utilidades da versão ----------
	static long agora() { return Util.getMeasuringTimeMs(); }

	static void abrir(String uri) { Util.getOperatingSystem().open(uri); }

	static void abrir(File f) { Util.getOperatingSystem().open(f); }

	void som() { client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F)); }

	String clipboard() { return client.keyboard.getClipboard(); }

	void fechar() { close(); }

	boolean ehTeclaAbrir(int key, int scan) { return TexturasMix.TECLA.matchesKey(key, scan); }

	String nomeTecla() { return TexturasMix.TECLA.getBoundKeyLocalizedText().getString(); }

	// ---------- Screen ----------
	@Override
	protected void init() { aoAbrir(); }

	@Override
	public void removed() { aoFechar(); }

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void render(DrawContext c, int mouseX, int mouseY, float delta) { desenhar(new G(c), mouseX, mouseY); }

	@Override
	public boolean mouseClicked(double x, double y, int b) { return clique(x, y, b) || super.mouseClicked(x, y, b); }

	@Override
	public boolean mouseScrolled(double x, double y, double amount) { return rolar(x, y, amount); }

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		return tecla(key, scan, Screen.hasControlDown(), Screen.isPaste(key)) || super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean charTyped(char ch, int mods) { return caractere(ch); }
}
