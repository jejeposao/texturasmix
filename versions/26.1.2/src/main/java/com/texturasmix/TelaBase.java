package com.texturasmix;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;

import java.io.File;

/** Ponte entre a tela comum e o Screen do Minecraft 26.1.2. */
abstract class TelaBase extends Screen {
	TelaBase() { super(Component.literal("Texturas Mix")); }

	abstract void aoAbrir();

	abstract void aoFechar();

	abstract void desenhar(G c, int mouseX, int mouseY);

	abstract boolean clique(double x, double y, int botao);

	abstract boolean rolar(double x, double y, double quanto);

	abstract boolean tecla(int key, int scan, boolean ctrl, boolean colar);

	abstract boolean caractere(char ch);

	// ---------- utilidades da versão ----------
	static long agora() { return Util.getMillis(); }

	static void abrir(String uri) { Util.getPlatform().openUri(uri); }

	static void abrir(File f) { Util.getPlatform().openFile(f); }

	void som() { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F)); }

	String clipboard() { return minecraft.keyboardHandler.getClipboard(); }

	void fechar() { onClose(); }

	boolean ehTeclaAbrir(int key, int scan) { return TexturasMix.TECLA.matches(new KeyEvent(key, scan, 0)); }

	String nomeTecla() { return TexturasMix.TECLA.getTranslatedKeyMessage().getString(); }

	// ---------- Screen ----------
	@Override
	protected void init() { aoAbrir(); }

	@Override
	public void removed() { aoFechar(); }

	@Override
	public boolean isPauseScreen() { return false; }

	@Override
	public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {} // fundo próprio, sem blur

	@Override
	public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) { desenhar(new G(c), mouseX, mouseY); }

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean duplo) { return clique(e.x(), e.y(), e.button()) || super.mouseClicked(e, duplo); }

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) { return rolar(x, y, v); }

	@Override
	public boolean keyPressed(KeyEvent e) { return tecla(e.key(), e.scancode(), e.hasControlDown(), e.isPaste()) || super.keyPressed(e); }

	@Override
	public boolean charTyped(CharacterEvent e) {
		int cp = e.codepoint();
		return Character.isBmpCodePoint(cp) && caractere((char) cp);
	}
}
