package com.texturasmix;

import java.util.ArrayList;
import java.util.List;

/** Desenho comum (todas as versões): texto em coordenadas float, caixas e cortes. Primitivas em {@link G}. */
final class Ui {
	static boolean glEs() { return G.glEs(); }

	static void reavaliarFonte() { G.reavaliarFonte(); }

	static boolean fontePadrao() { return TexturasMix.CFG.fonteMenu == 1 || G.glEs() || !G.ttfOk(); }

	static float larg(String s, boolean b, float e) { return G.largura(s, b) * e; }

	static void txt(G c, String s, float x, float y, int cor, boolean b, float e) { c.texto(s, x, y, cor, b, e); }

	static void txtC(G c, String s, float cx, float y, int cor, boolean b, float e) {
		txt(c, s, cx - larg(s, b, e) / 2, y, cor, b, e);
	}

	static void txtD(G c, String s, float xd, float y, int cor, boolean b, float e) {
		txt(c, s, xd - larg(s, b, e), y, cor, b, e);
	}

	/** Texto com espaçamento entre letras (rótulos em caixa alta). */
	static float txtEsp(G c, String s, float x, float y, int cor, boolean b, float e, float esp) {
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			String ch = new String(Character.toChars(cp));
			txt(c, ch, x, y, cor, b, e);
			x += larg(ch, b, e) + esp * e;
			i += Character.charCount(cp);
		}
		return x;
	}

	static float largEsp(String s, boolean b, float e, float esp) { return larg(s, b, e) + s.codePointCount(0, s.length()) * esp * e; }

	static String cortar(String s, float max, boolean b, float e) {
		if (s == null) return "";
		if (larg(s, b, e) <= max) return s;
		while (s.length() > 1 && larg(s + "…", b, e) > max) s = s.substring(0, s.length() - 1);
		return s + "…";
	}

	static List<String> quebrar(String s, float max, boolean b, float e, int linhas) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String p : s.split("\\s+")) {
			String tent = cur.length() == 0 ? p : cur + " " + p;
			if (larg(tent, b, e) > max && cur.length() > 0) {
				out.add(cur.toString());
				cur = new StringBuilder(p);
				if (out.size() == linhas) break;
			} else cur = new StringBuilder(tent);
		}
		if (out.size() < linhas && cur.length() > 0) out.add(cur.toString());
		if (out.size() == linhas) out.set(linhas - 1, cortar(out.get(linhas - 1), max, b, e));
		return out;
	}

	static void rr(G c, float x, float y, float w, float h, float r, int cor) {
		if (w <= 0 || h <= 0) return;
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
		c.rr(x, y, w, h, r, cor);
	}

	static void caixa(G c, float x, float y, float w, float h, float r, int fundo, int borda) {
		caixa(c, x, y, w, h, r, fundo, borda, 0.75f);
	}

	static void caixa(G c, float x, float y, float w, float h, float r, int fundo, int borda, float esp) {
		rr(c, x, y, w, h, r, borda);
		rr(c, x + esp, y + esp, w - 2 * esp, h - 2 * esp, Math.max(0, r - esp), fundo);
	}

	static void gradiente(G c, float x, float y, float w, float h, int cima, int baixo) { c.gradiente(x, y, w, h, cima, baixo); }

	/** Retângulo arredondado por faixas horizontais (só retângulos: funciona em qualquer renderizador). */
	static void rrFaixas(G c, float x, float y, float w, float h, float r, int cor) {
		if (r < 0.75f) { c.gradiente(x, y, w, h, cor, cor); return; }
		int n = r < 2.5f ? 2 : 4;
		float fx = r / n;
		c.gradiente(x, y + r, w, h - 2 * r, cor, cor);
		for (int i = 0; i < n; i++) {
			float yc = (i + 0.5f) * fx; // centro da faixa, medido da borda
			float d = r - yc;
			float ins = r - (float) Math.sqrt(Math.max(0, r * r - d * d));
			c.gradiente(x + ins, y + i * fx, w - 2 * ins, fx, cor, cor);
			c.gradiente(x + ins, y + h - (i + 1) * fx, w - 2 * ins, fx, cor, cor);
		}
	}
}
