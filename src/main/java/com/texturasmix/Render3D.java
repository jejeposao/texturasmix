package com.texturasmix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prévias 3D renderizadas uma única vez na CPU (fora da thread do jogo) e guardadas como textura:
 * boneco vestindo a armadura (vitrine) e bloco isométrico. Rasterizador simples com z-buffer e amostragem nearest.
 */
final class Render3D {
	static final String MC = Indice.MC;

	static final class Img {
		final int w, h;
		final int[] p; // ARGB

		Img(int w, int h, int[] p) { this.w = w; this.h = h; this.p = p; }

		int at(int x, int y) { return x < 0 || y < 0 || x >= w || y >= h ? 0 : p[y * w + x]; }
	}

	private static final class Face {
		double[] o, u, v;
		Img t;
		float tu, tv, tw, th, luz;
		boolean esp;
		int tint;
	}

	private final int W, H;
	private final double cy, sy, cp, sp;
	private final List<Face> faces = new ArrayList<>();

	Render3D(int w, int h, double yaw, double pitch) {
		W = w;
		H = h;
		cy = Math.cos(Math.toRadians(yaw));
		sy = Math.sin(Math.toRadians(yaw));
		cp = Math.cos(Math.toRadians(pitch));
		sp = Math.sin(Math.toRadians(pitch));
	}

	private double[] rot(double[] a) {
		double x1 = a[0] * cy + a[2] * sy, z1 = -a[0] * sy + a[2] * cy;
		return new double[]{x1, a[1] * cp - z1 * sp, a[1] * sp + z1 * cp};
	}

	private void face(Img t, float[] r, float k, double[] o, double[] u, double[] v, float luz, boolean esp, int tint) {
		if (t == null) return;
		Face f = new Face();
		f.t = t;
		f.tu = r[0] * k;
		f.tv = r[1] * k;
		f.tw = r[2] * k;
		f.th = r[3] * k;
		f.o = o;
		f.u = u;
		f.v = v;
		f.luz = luz;
		f.esp = esp;
		f.tint = tint;
		faces.add(f);
	}

	/** Caixa no formato dos modelos do Minecraft (UV a partir de u,v; tamanhos w,h,d em pixels de 64). */
	void caixa(Img t, int u, int v, int w, int h, int d, float x0, float y0, float z0, float inf, boolean esp, int tint) {
		if (t == null) return;
		float k = t.w / 64f;
		double X0 = x0 - inf, X1 = x0 + w + inf, Y0 = y0 - inf, Y1 = y0 + h + inf, Z0 = z0 - inf, Z1 = z0 + d + inf;
		double LW = X1 - X0, LH = Y1 - Y0, LD = Z1 - Z0;
		float[] fr = {u + d, v + d, w, h}, bk = {u + 2 * d + w, v + d, w, h}, rs = {u, v + d, d, h}, ls = {u + d + w, v + d, d, h},
				tp = {u + d, v, w, d}, bt = {u + d + w, v, w, d};
		if (esp) { float[] x = rs; rs = ls; ls = x; }
		face(t, fr, k, new double[]{X0, Y0, Z1}, new double[]{LW, 0, 0}, new double[]{0, LH, 0}, 0.86f, esp, tint);
		face(t, bk, k, new double[]{X1, Y0, Z0}, new double[]{-LW, 0, 0}, new double[]{0, LH, 0}, 0.7f, esp, tint);
		face(t, rs, k, new double[]{X0, Y0, Z0}, new double[]{0, 0, LD}, new double[]{0, LH, 0}, 0.66f, esp, tint);
		face(t, ls, k, new double[]{X1, Y0, Z1}, new double[]{0, 0, -LD}, new double[]{0, LH, 0}, 0.66f, esp, tint);
		face(t, tp, k, new double[]{X0, Y0, Z0}, new double[]{LW, 0, 0}, new double[]{0, 0, LD}, 1f, esp, tint);
		face(t, bt, k, new double[]{X0, Y1, Z1}, new double[]{LW, 0, 0}, new double[]{0, 0, -LD}, 0.5f, esp, tint);
	}

	/** Cubo 16×16×16 com textura de topo, lado e frente (1º quadro, se animada). */
	void cubo(Img topo, Img lado, Img frente, int tintTopo, int tintLado) {
		float[] rt = {0, 0, topo.w, Math.min(topo.h, topo.w)}, rl = {0, 0, lado.w, Math.min(lado.h, lado.w)}, rf = {0, 0, frente.w, Math.min(frente.h, frente.w)};
		face(topo, rt, 1, new double[]{-8, -8, -8}, new double[]{16, 0, 0}, new double[]{0, 0, 16}, 1f, false, tintTopo);
		face(frente, rf, 1, new double[]{-8, -8, 8}, new double[]{16, 0, 0}, new double[]{0, 16, 0}, 0.8f, false, tintLado);
		face(lado, rl, 1, new double[]{-8, -8, -8}, new double[]{0, 0, 16}, new double[]{0, 16, 0}, 0.6f, false, tintLado);
		face(lado, rl, 1, new double[]{8, -8, 8}, new double[]{0, 0, -16}, new double[]{0, 16, 0}, 0.6f, false, tintLado);
	}

	Img render() {
		double minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
		for (Face f : faces) {
			for (double[] c : new double[][]{f.o, soma(f.o, f.u), soma(f.o, f.v), soma(soma(f.o, f.u), f.v)}) {
				double[] r = rot(c);
				minx = Math.min(minx, r[0]); maxx = Math.max(maxx, r[0]);
				miny = Math.min(miny, r[1]); maxy = Math.max(maxy, r[1]);
			}
		}
		int[] px = new int[W * H];
		if (faces.isEmpty()) return new Img(W, H, px);
		double esc = Math.min(W * 0.9 / (maxx - minx), H * 0.92 / (maxy - miny));
		double ox = W / 2.0 - (minx + maxx) / 2 * esc, oy = H / 2.0 - (miny + maxy) / 2 * esc;
		double[] zb = new double[W * H];
		Arrays.fill(zb, -1e18);
		for (Face f : faces) {
			double[] o = rot(f.o), a = rot(f.u), b = rot(f.v);
			double X = o[0] * esc + ox, Y = o[1] * esc + oy, ax = a[0] * esc, ay = a[1] * esc, bx = b[0] * esc, by = b[1] * esc;
			double det = ax * by - ay * bx;
			if (Math.abs(det) < 1e-6) continue;
			int x0 = (int) Math.max(0, Math.floor(Math.min(Math.min(X, X + ax), Math.min(X + bx, X + ax + bx))));
			int x1 = (int) Math.min(W - 1, Math.ceil(Math.max(Math.max(X, X + ax), Math.max(X + bx, X + ax + bx))));
			int y0 = (int) Math.max(0, Math.floor(Math.min(Math.min(Y, Y + ay), Math.min(Y + by, Y + ay + by))));
			int y1 = (int) Math.min(H - 1, Math.ceil(Math.max(Math.max(Y, Y + ay), Math.max(Y + by, Y + ay + by))));
			for (int yy = y0; yy <= y1; yy++) {
				for (int xx = x0; xx <= x1; xx++) {
					double qx = xx + 0.5 - X, qy = yy + 0.5 - Y;
					double s = (qx * by - qy * bx) / det, t = (ax * qy - ay * qx) / det;
					if (s < 0 || s >= 1 || t < 0 || t >= 1) continue;
					int i = yy * W + xx;
					double z = o[2] + s * a[2] + t * b[2];
					if (z < zb[i] - 1e-4) continue;
					double ss = f.esp ? 1 - s : s;
					int tx = (int) (f.tu + Math.min(f.tw - 1e-3, ss * f.tw)), ty = (int) (f.tv + Math.min(f.th - 1e-3, t * f.th));
					int c = f.t.at(tx, ty);
					if ((c >>> 24) < 0x80) continue;
					int r = c >> 16 & 255, g = c >> 8 & 255, bl = c & 255;
					if (f.tint != -1) {
						r = r * (f.tint >> 16 & 255) / 255;
						g = g * (f.tint >> 8 & 255) / 255;
						bl = bl * (f.tint & 255) / 255;
					}
					px[i] = 0xFF000000 | (int) (r * f.luz) << 16 | (int) (g * f.luz) << 8 | (int) (bl * f.luz);
					zb[i] = z;
				}
			}
		}
		return new Img(W, H, px);
	}

	private static double[] soma(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }

	// ---------------- prévias ----------------
	static Img ler(Indice.Pack p, String entrada) {
		if (entrada == null) return null;
		try {
			byte[] b = Indice.ler(p, entrada);
			return b == null ? null : Previews.decodificar(b);
		} catch (Exception e) {
			return null;
		}
	}

	/** Boneco parado, virado levemente de lado, vestindo as camadas da armadura do pack. */
	static Img armadura(Indice.Variacao v) {
		String l1 = null, l2 = null, o1 = null, o2 = null;
		for (String a : v.arquivos) {
			if (a.endsWith("_layer_1.png")) l1 = a;
			else if (a.endsWith("_layer_2.png")) l2 = a;
			else if (a.endsWith("_layer_1_overlay.png")) o1 = a;
			else if (a.endsWith("_layer_2_overlay.png")) o2 = a;
		}
		Indice.Pack p = v.pack;
		Img L1 = ler(p, l1), L2 = ler(p, l2), O1 = ler(p, o1), O2 = ler(p, o2);
		if (L1 == null && L2 == null) return null;
		int tint = v.chave.equals("armadura:leather") ? 0xA06540 : -1;
		Img pele = ler(null, MC + "textures/entity/player/wide/steve.png");
		if (pele == null) { int[] g = new int[64 * 64]; Arrays.fill(g, 0xFF7C8494); pele = new Img(64, 64, g); }
		Render3D r = new Render3D(112, 160, 28, -9);
		boolean novo = pele.h >= pele.w;
		r.caixa(pele, 0, 0, 8, 8, 8, -4, 0, -4, 0, false, -1);
		r.caixa(pele, 16, 16, 8, 12, 4, -4, 8, -2, 0, false, -1);
		r.caixa(pele, 40, 16, 4, 12, 4, -8, 8, -2, 0, false, -1);
		if (novo) r.caixa(pele, 32, 48, 4, 12, 4, 4, 8, -2, 0, false, -1); else r.caixa(pele, 40, 16, 4, 12, 4, 4, 8, -2, 0, true, -1);
		r.caixa(pele, 0, 16, 4, 12, 4, -4, 20, -2, 0, false, -1);
		if (novo) r.caixa(pele, 16, 48, 4, 12, 4, 0, 20, -2, 0, false, -1); else r.caixa(pele, 0, 16, 4, 12, 4, 0, 20, -2, 0, true, -1);
		camada2(r, L2, tint);
		camada2(r, O2, -1);
		camada1(r, L1, tint);
		camada1(r, O1, -1);
		return r.render();
	}

	private static void camada1(Render3D r, Img t, int tint) { // capacete, peitoral, braços e botas
		if (t == null) return;
		r.caixa(t, 0, 0, 8, 8, 8, -4, 0, -4, 1f, false, tint);
		r.caixa(t, 16, 16, 8, 12, 4, -4, 8, -2, 1f, false, tint);
		r.caixa(t, 40, 16, 4, 12, 4, -8, 8, -2, 1f, false, tint);
		r.caixa(t, 40, 16, 4, 12, 4, 4, 8, -2, 1f, true, tint);
		r.caixa(t, 0, 16, 4, 12, 4, -4, 20, -2, 1f, false, tint);
		r.caixa(t, 0, 16, 4, 12, 4, 0, 20, -2, 1f, true, tint);
	}

	private static void camada2(Render3D r, Img t, int tint) { // calça
		if (t == null) return;
		r.caixa(t, 16, 16, 8, 12, 4, -4, 8, -2, 0.5f, false, tint);
		r.caixa(t, 0, 16, 4, 12, 4, -4, 20, -2, 0.5f, false, tint);
		r.caixa(t, 0, 16, 4, 12, 4, 0, 20, -2, 0.5f, true, tint);
	}

	/** Bloco em 3D isométrico (topo + 2 lados, sombreado). */
	static Img bloco(Indice.Variacao v) {
		String n = v.chave.substring(2);
		Img topo = tex(v, n + "_top", n, n + "_side", n + "_side0"), lado = tex(v, n + "_side", n + "_side0", n, n + "_top"),
				frente = tex(v, n + "_front", n + "_side", n + "_side0", n, n + "_top");
		if (topo == null && lado == null) {
			Img pr = ler(v.texVanilla ? null : v.pack, v.principal);
			if (pr == null) return null;
			topo = lado = frente = pr;
		}
		if (topo == null) topo = lado;
		if (lado == null) lado = topo;
		if (frente == null) frente = lado;
		boolean verde = n.equals("grass_block") || n.endsWith("_leaves");
		Render3D r = new Render3D(128, 128, 45, -30);
		r.cubo(topo, lado, frente, verde ? (n.equals("grass_block") ? 0x91BD59 : 0x77AB2F) : -1, n.endsWith("_leaves") ? 0x77AB2F : -1);
		return r.render();
	}

	private static Img tex(Indice.Variacao v, String... nomes) {
		for (String nome : nomes) {
			String e = MC + "textures/block/" + nome + ".png";
			Img i = v.pack != null && v.arquivos.contains(e) ? ler(v.pack, e) : null;
			if (i == null) i = ler(null, e);
			if (i != null) return i;
		}
		return null;
	}
}
