package com.texturasmix;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Primitivas de desenho do Minecraft 1.20.1 (DrawContext + Tessellator). */
final class G {
	static final Identifier FONTE = new Identifier("texturasmix", "inter");
	static final Identifier FONTE_B = new Identifier("texturasmix", "inter_bold");
	private static Boolean glEs, ttfOk;

	final DrawContext c;

	G(DrawContext c) { this.c = c; }

	static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

	/** Android / Pojav / Mojo / gl4es / GL ES: usa só recursos simples. */
	static boolean glEs() {
		if (glEs == null) {
			boolean v = new java.io.File("/system/build.prop").exists() || System.getenv("POJAV_RENDERER") != null
					|| System.getProperty("java.vendor", "").toLowerCase(java.util.Locale.ROOT).contains("android");
			try {
				String s = (com.mojang.blaze3d.platform.GlDebugInfo.getVersion() + " " + com.mojang.blaze3d.platform.GlDebugInfo.getRenderer()).toLowerCase(java.util.Locale.ROOT);
				v |= s.contains("opengl es") || s.contains("gl4es") || s.contains("ltw") || s.contains("zink") || s.contains("vgpu");
			} catch (Throwable ignored) {}
			glEs = v;
		}
		return glEs;
	}

	/** TTF carregada? Se o provedor falhou, o JSON cai na referência padrão e as larguras ficam iguais. */
	static boolean ttfOk() {
		if (ttfOk == null) {
			String s = "Wil1iam MW 0.5";
			int a = tr().getWidth(Text.literal(s).setStyle(Style.EMPTY.withFont(FONTE)));
			ttfOk = a > 0 && a != tr().getWidth(s);
		}
		return ttfOk;
	}

	static void reavaliarFonte() { ttfOk = null; }

	static Text t(String s, boolean b) {
		if (Ui.fontePadrao()) return b ? Text.literal(s).setStyle(Style.EMPTY.withBold(true)) : Text.literal(s);
		return Text.literal(s).setStyle(Style.EMPTY.withFont(b ? FONTE_B : FONTE));
	}

	static int largura(String s, boolean b) { return tr().getWidth(t(s, b)); }

	void texto(String s, float x, float y, int cor, boolean b, float e) {
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate(x, y, 1); // levemente à frente: evita texto sumir por z-fighting (gl4es)
		m.scale(e, e, 1);
		c.drawText(tr(), t(s, b), 0, 0, cor, false);
		m.pop();
	}

	void push() { c.getMatrices().push(); }

	void pop() { c.getMatrices().pop(); }

	void escala(float f) { c.getMatrices().scale(f, f, 1); }

	/** Recorte em pixels de tela. */
	void recorte(int x1, int y1, int x2, int y2) { c.enableScissor(x1, y1, x2, y2); }

	void semRecorte() { c.disableScissor(); }

	/** Desenha a região (u,v,rw,rh) de uma textura w×h em (x,y) com escala esc. */
	void textura(Previews.Tex t, float x, float y, float esc, int rw, int rh, float u, float v, int w, int h) {
		MatrixStack m = c.getMatrices();
		m.push();
		m.translate(x, y, 0);
		m.scale(esc, esc, 1);
		c.drawTexture(t.id, 0, 0, rw, rh, u, v, rw, rh, w, h);
		m.pop();
	}

	void rr(float x, float y, float w, float h, float r, int cor) {
		if (glEs() || r < 0.5f) { // GL ES: retângulo simples (mesmo caminho do fill vanilla)
			gradiente(x, y, w, h, cor, cor);
			return;
		}
		Matrix4f m = c.getMatrices().peek().getPositionMatrix();
		float a = (cor >>> 24) / 255f, rd = (cor >> 16 & 255) / 255f, g = (cor >> 8 & 255) / 255f, b = (cor & 255) / 255f;
		int seg = r < 2 ? 2 : 6;
		float[][] cs = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
		List<float[]> per = new ArrayList<>();
		for (float[] k : cs) {
			for (int i = 0; i <= seg; i++) {
				double ang = Math.toRadians(k[2] + 90.0 * i / seg);
				per.add(new float[]{(float) (k[0] + Math.cos(ang) * r), (float) (k[1] + Math.sin(ang) * r)});
			}
		}
		per.add(per.get(0));
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder bb = Tessellator.getInstance().getBuffer();
		bb.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR); // triângulos: suportado em qualquer GL
		float cx = x + w / 2, cy = y + h / 2;
		for (int i = 0; i + 1 < per.size(); i++) {
			bb.vertex(m, cx, cy, 0).color(rd, g, b, a).next();
			bb.vertex(m, per.get(i)[0], per.get(i)[1], 0).color(rd, g, b, a).next();
			bb.vertex(m, per.get(i + 1)[0], per.get(i + 1)[1], 0).color(rd, g, b, a).next();
		}
		BufferRenderer.drawWithGlobalProgram(bb.end());
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
	}

	void gradiente(float x, float y, float w, float h, int cima, int baixo) {
		Matrix4f m = c.getMatrices().peek().getPositionMatrix();
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		BufferBuilder bb = Tessellator.getInstance().getBuffer();
		bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		bb.vertex(m, x, y, 0).color(cima).next();
		bb.vertex(m, x, y + h, 0).color(baixo).next();
		bb.vertex(m, x + w, y + h, 0).color(baixo).next();
		bb.vertex(m, x + w, y, 0).color(cima).next();
		BufferRenderer.drawWithGlobalProgram(bb.end());
		RenderSystem.enableDepthTest();
		RenderSystem.enableCull();
	}
}
