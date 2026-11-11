package com.texturasmix;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Texturas de prévia: só existem com a tela aberta; leitura/decodificação fora da thread do jogo. */
public final class Previews {
	public static final class Tex {
		public volatile Identifier id;
		public volatile int w, h;
		public volatile boolean falhou;
	}

	private static final Map<String, Tex> MAPA = new ConcurrentHashMap<>();
	private static final AtomicInteger N = new AtomicInteger();
	private static volatile boolean ativo;

	public static boolean png(byte[] b) {
		return b != null && b.length > 8 && (b[0] & 255) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
	}

	/** PNG -> pixels ARGB (para as prévias 3D feitas na CPU). */
	static Render3D.Img decodificar(byte[] b) throws java.io.IOException {
		if (!png(b)) return null;
		try (NativeImage img = NativeImage.read(new ByteArrayInputStream(b))) {
			int w = img.getWidth(), h = img.getHeight();
			int[] p = new int[w * h];
			for (int y = 0; y < h; y++)
				for (int x = 0; x < w; x++) {
					p[y * w + x] = img.getPixel(x, y); // ARGB
				}
			return new Render3D.Img(w, h, p);
		}
	}

	/** Prévia gerada (3D): renderizada uma vez em segundo plano e guardada como textura. */
	public static Tex deImagem(String chave, Callable<Render3D.Img> fonte) {
		return carregar(chave, () -> {
			Render3D.Img i = fonte.call();
			if (i == null) return null;
			NativeImage n = new NativeImage(i.w, i.h, true);
			for (int y = 0; y < i.h; y++)
				for (int x = 0; x < i.w; x++) {
					n.setPixel(x, y, i.p[y * i.w + x]);
				}
			return n;
		});
	}

	public static Tex de(String chave, Callable<byte[]> fonte) {
		return carregar(chave, () -> {
			byte[] b = fonte.call();
			return png(b) ? NativeImage.read(new ByteArrayInputStream(b)) : null;
		});
	}

	private static Tex carregar(String chave, Callable<NativeImage> fonte) {
		Tex t = MAPA.get(chave);
		if (t != null) return t;
		Tex nova = new Tex();
		t = MAPA.putIfAbsent(chave, nova);
		if (t != null) return t;
		TexturasMix.IO.submit(() -> {
			try {
				if (!ativo) return;
				NativeImage img = fonte.call();
				if (img == null) { nova.falhou = true; return; }
				Minecraft.getInstance().execute(() -> {
					if (!ativo || MAPA.get(chave) != nova) { img.close(); return; }
					nova.w = img.getWidth();
					nova.h = img.getHeight();
					Identifier id = Identifier.fromNamespaceAndPath("texturasmix", "previa/" + N.incrementAndGet());
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "texturasmix", img));
					nova.id = id;
				});
			} catch (Exception e) {
				nova.falhou = true;
			}
		});
		return nova;
	}

	public static void abrir() { ativo = true; }

	public static void fechar() {
		ativo = false;
		for (Tex t : MAPA.values()) if (t.id != null) Minecraft.getInstance().getTextureManager().release(t.id);
		MAPA.clear();
		TexturasMix.IO.submit(Indice::fecharZips);
	}
}
