package com.texturasmix;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Primitivas de desenho do Minecraft 26.1.2 (GuiGraphicsExtractor, GUI por estados).
 * Só retângulos/texto/blit: cantos arredondados são feitos por faixas (Ui.rrFaixas).
 */
final class G {
	static final FontDescription FONTE = new FontDescription.Resource(Identifier.fromNamespaceAndPath("texturasmix", "inter"));
	static final FontDescription FONTE_B = new FontDescription.Resource(Identifier.fromNamespaceAndPath("texturasmix", "inter_bold"));
	private static Boolean glEs, ttfOk;

	final GuiGraphicsExtractor c;

	G(GuiGraphicsExtractor c) { this.c = c; }

	static Font tr() { return Minecraft.getInstance().font; }

	/** Android / Pojav / Mojo / gl4es / GL ES. */
	static boolean glEs() {
		if (glEs == null) {
			boolean v = new java.io.File("/system/build.prop").exists() || System.getenv("POJAV_RENDERER") != null
					|| System.getProperty("java.vendor", "").toLowerCase(java.util.Locale.ROOT).contains("android");
			try {
				var d = com.mojang.blaze3d.systems.RenderSystem.getDevice();
				String s = (d.getVersion() + " " + d.getRenderer()).toLowerCase(java.util.Locale.ROOT);
				v |= s.contains("opengl es") || s.contains("gl4es") || s.contains("ltw") || s.contains("zink") || s.contains("vgpu");
			} catch (Throwable ignored) {}
			glEs = v;
		}
		return glEs;
	}

	static boolean ttfOk() {
		if (ttfOk == null) {
			String s = "Wil1iam MW 0.5";
			int a = tr().width(Component.literal(s).setStyle(Style.EMPTY.withFont(FONTE)));
			ttfOk = a > 0 && a != tr().width(s);
		}
		return ttfOk;
	}

	static void reavaliarFonte() { ttfOk = null; }

	static Component t(String s, boolean b) {
		if (Ui.fontePadrao()) return b ? Component.literal(s).setStyle(Style.EMPTY.withBold(true)) : Component.literal(s);
		return Component.literal(s).setStyle(Style.EMPTY.withFont(b ? FONTE_B : FONTE));
	}

	static int largura(String s, boolean b) { return tr().width(t(s, b)); }

	/** Cor sem alfa (0xRRGGBB) vira opaca, como no 1.20.1. */
	private static int argb(int cor) { return (cor & 0xFC000000) == 0 ? cor | 0xFF000000 : cor; }

	void texto(String s, float x, float y, int cor, boolean b, float e) {
		c.pose().pushMatrix();
		c.pose().translate(x, y);
		c.pose().scale(e, e);
		c.text(tr(), t(s, b), 0, 0, argb(cor), false);
		c.pose().popMatrix();
	}

	void push() { c.pose().pushMatrix(); }

	void pop() { c.pose().popMatrix(); }

	void escala(float f) { c.pose().scale(f, f); }

	/** Recorte em pixels de tela (sem a escala atual). */
	void recorte(int x1, int y1, int x2, int y2) {
		c.pose().pushMatrix();
		c.pose().identity();
		c.enableScissor(x1, y1, x2, y2);
		c.pose().popMatrix();
	}

	void semRecorte() { c.disableScissor(); }

	void textura(Previews.Tex t, float x, float y, float esc, int rw, int rh, float u, float v, int w, int h) {
		c.pose().pushMatrix();
		c.pose().translate(x, y);
		c.pose().scale(esc, esc);
		c.blit(RenderPipelines.GUI_TEXTURED, t.id, 0, 0, u, v, rw, rh, w, h);
		c.pose().popMatrix();
	}

	void rr(float x, float y, float w, float h, float r, int cor) { Ui.rrFaixas(this, x, y, w, h, r, cor); }

	/** Retângulo float: fill de 1×1 escalado (fill só aceita inteiros). */
	void gradiente(float x, float y, float w, float h, int cima, int baixo) {
		if (w <= 0 || h <= 0) return;
		c.pose().pushMatrix();
		c.pose().translate(x, y);
		c.pose().scale(w / 64f, h / 64f);
		if (cima == baixo) c.fill(0, 0, 64, 64, argb(cima));
		else c.fillGradient(0, 0, 64, 64, argb(cima), argb(baixo));
		c.pose().popMatrix();
	}
}
