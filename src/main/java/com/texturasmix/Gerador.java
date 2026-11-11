package com.texturasmix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Gera o pack "texturasmix_gerado" só com os itens escolhidos e o coloca no topo. */
public final class Gerador {
	public static final String NOME = "texturasmix_gerado";
	static final String ID = "file/" + NOME;
	private static ScheduledFuture<?> pendente;
	public static volatile String status = "";

	static Path saida() { return Plataforma.pastaResourcepacks().resolve(NOME); }

	/** Agenda (com debounce) a geração e o recarregamento. Chamar na thread do jogo. */
	public static synchronized void agendar() {
		Map<String, Config.Selecao> copia = new LinkedHashMap<>();
		TexturasMix.CFG.selecao.forEach((k, s) -> copia.put(k, s.copia()));
		if (pendente != null) pendente.cancel(false);
		status = "Gerando pack…";
		pendente = TexturasMix.AGENDA.schedule(() -> {
			try {
				int n = gerar(copia);
				Plataforma.naThread(() -> {
					aplicarNoJogo(n > 0);
					status = n > 0 ? "Aplicado: " + n + (n == 1 ? " item" : " itens") : "Texturas originais";
				});
			} catch (Exception e) {
				status = "Erro ao gerar: " + e.getMessage();
				TexturasMix.LOG.error("Falha ao gerar pack", e);
			}
		}, 700, TimeUnit.MILLISECONDS);
	}

	public static void resetar() {
		TexturasMix.CFG.selecao.clear();
		TexturasMix.CFG.salvar();
		agendar();
	}

	static int gerar(Map<String, Config.Selecao> sel) throws Exception {
		for (int i = 0; i < 300 && Indice.indexando; i++) Thread.sleep(100);
		Path out = saida();
		apagar(out);
		if (sel.isEmpty()) return 0;
		Files.createDirectories(out);
		Files.writeString(out.resolve("pack.mcmeta"),
				Plataforma.packMeta("§dTexturas Mix §7· gerado"), StandardCharsets.UTF_8);
		JsonArray cred = new JsonArray();
		int n = 0;
		for (Map.Entry<String, Config.Selecao> e : sel.entrySet()) {
			Indice.Pack p = Indice.pack(e.getValue().pack);
			Indice.Variacao v = Indice.variacao(e.getValue().pack, e.getKey());
			if (p == null || v == null) continue;
			Set<String> feitos = new HashSet<>();
			Deque<String> fila = new ArrayDeque<>();
			for (String a : v.arquivos) if (!e.getValue().excluidos.contains(a)) fila.add(a);
			boolean algum = false;
			while (!fila.isEmpty()) {
				String a = fila.poll();
				if (!feitos.add(a) || a.contains("..")) continue;
				byte[] b = Indice.ler(p, a);
				if (b == null) continue;
				Path f = out.resolve(Plataforma.destino(a));
				Files.createDirectories(f.getParent());
				Files.write(f, b);
				algum = true;
				if (a.endsWith(".png")) fila.add(a + ".mcmeta"); // animação
				if (a.endsWith(".json") && a.contains("/models/")) dependencias(b, fila);
			}
			if (!algum) continue;
			n++;
			JsonObject c = new JsonObject();
			Config.MetaModrinth m = p.meta();
			c.addProperty("item", v.nomeItem);
			c.addProperty("pack", p.nome);
			c.addProperty("arquivo", p.arquivo);
			c.addProperty("autor", m != null ? m.autor : "—");
			c.addProperty("licenca", m != null ? m.licenca : "—");
			c.addProperty("link", m != null ? m.link() : p.caminho);
			cred.add(c);
		}
		Files.writeString(out.resolve("creditos.json"), Config.GSON.toJson(cred), StandardCharsets.UTF_8);
		return n;
	}

	/** Puxa parent, texturas e overrides de um modelo (quando existem no pack). */
	static void dependencias(byte[] json, Collection<String> fila) {
		try {
			JsonObject o = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
			if (o.has("parent")) fila.add(caminho(o.get("parent").getAsString(), "models", ".json"));
			if (o.has("textures")) for (Map.Entry<String, JsonElement> t : o.getAsJsonObject("textures").entrySet()) {
				String s = t.getValue().getAsString();
				if (!s.startsWith("#")) fila.add(caminho(s, "textures", ".png"));
			}
			if (o.has("overrides")) for (JsonElement ov : o.getAsJsonArray("overrides")) {
				JsonObject x = ov.getAsJsonObject();
				if (x.has("model")) fila.add(caminho(x.get("model").getAsString(), "models", ".json"));
			}
		} catch (Exception ignored) {}
	}

	static String caminho(String ref, String tipo, String ext) {
		String ns = "minecraft", p = ref;
		int i = ref.indexOf(':');
		if (i >= 0) { ns = ref.substring(0, i); p = ref.substring(i + 1); }
		return "assets/" + ns + "/" + tipo + "/" + p + ext;
	}

	static void aplicarNoJogo(boolean ativo) { Plataforma.aplicarPack(ID, ativo); }

	/** ENVIAR: exporta o pack gerado como .zip compartilhável. */
	public static Path exportar() throws IOException {
		Path src = saida();
		if (!Files.exists(src.resolve("pack.mcmeta"))) throw new IOException("nenhuma textura aplicada");
		Path z = src.resolveSibling("TexturasMix_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip");
		try (OutputStream os = Files.newOutputStream(z); ZipOutputStream zo = new ZipOutputStream(os); Stream<Path> w = Files.walk(src)) {
			for (Path f : (Iterable<Path>) w::iterator) {
				if (!Files.isRegularFile(f)) continue;
				zo.putNextEntry(new ZipEntry(src.relativize(f).toString().replace('\\', '/')));
				Files.copy(f, zo);
				zo.closeEntry();
			}
		}
		return z;
	}

	static void apagar(Path p) throws IOException {
		if (!Files.exists(p)) return;
		try (Stream<Path> w = Files.walk(p)) {
			List<Path> l = w.sorted(Comparator.reverseOrder()).toList();
			for (Path x : l) Files.deleteIfExists(x);
		}
	}
}
