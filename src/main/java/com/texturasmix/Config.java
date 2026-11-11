package com.texturasmix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public final class Config {
	/** Vazio = .minecraft/texturasmix/packs. Caminho próprio só vale se existir. */
	public String pastaPacks = "";
	public int limiteCacheMB = 2048;
	/** 0 = Personalizada (TTF), 1 = Padrão do Minecraft. */
	public int fonteMenu = 0;
	/** Economia de disco: 0 = perguntar, 1 = extrair texturas e apagar .zip, 2 = manter .zip. */
	public int biblioteca = 0;
	/** JSON de versão para atualização automática (vazio = desligado). */
	public static final String URL_PADRAO = "https://raw.githubusercontent.com/jejeposao/texturasmix/main/versao.json";
	public String urlAtualizacao = URL_PADRAO;
	public int tema = 0;
	public Set<String> favoritos = new LinkedHashSet<>();
	/** chave do item -> pack escolhido. */
	public Map<String, Selecao> selecao = new LinkedHashMap<>();
	/** nome do arquivo baixado -> dados do Modrinth (para créditos/etiquetas). */
	public Map<String, MetaModrinth> modrinth = new LinkedHashMap<>();

	public static final class Selecao {
		public String pack;
		public List<String> excluidos = new ArrayList<>();

		public Selecao() {}

		public Selecao(String pack) { this.pack = pack; }

		public Selecao copia() {
			Selecao s = new Selecao(pack);
			s.excluidos = new ArrayList<>(excluidos);
			return s;
		}
	}

	public static final class MetaModrinth {
		public String id, slug, titulo, autor, licenca;
		public long downloads;

		public String link() { return "https://modrinth.com/resourcepack/" + (slug != null ? slug : id); }
	}

	static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	public static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("texturasmix"); }

	public static Path biblioteca() { return dir().resolve("biblioteca"); }

	public static Path cache() { return dir().resolve("cache"); }

	public static Path pastaPadrao() { return FabricLoader.getInstance().getGameDir().resolve("texturasmix").resolve("packs"); }

	/** Pasta configurada (se existir) ou a padrão relativa ao jogo, criada se faltar. */
	public Path packs() {
		if (pastaPacks != null && !pastaPacks.isBlank()) {
			try {
				Path p = Paths.get(pastaPacks.trim());
				if (Files.isDirectory(p)) return p;
			} catch (Exception ignored) {}
		}
		Path d = pastaPadrao();
		try { Files.createDirectories(d); } catch (IOException ignored) {}
		return d;
	}

	static Config carregar() {
		Config c = null;
		try {
			Path f = dir().resolve("config.json");
			if (Files.exists(f)) c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Config.class);
		} catch (Exception e) {
			TexturasMix.LOG.warn("Config inválida, usando padrão", e);
		}
		if (c == null) c = new Config();
		if (c.favoritos == null) c.favoritos = new LinkedHashSet<>();
		if (c.selecao == null) c.selecao = new LinkedHashMap<>();
		if (c.modrinth == null) c.modrinth = new LinkedHashMap<>();
		if (c.urlAtualizacao == null || c.urlAtualizacao.isBlank()) c.urlAtualizacao = URL_PADRAO;
		if (c.limiteCacheMB < 64) c.limiteCacheMB = 2048;
		c.selecao.values().removeIf(s -> s == null || s.pack == null);
		c.selecao.values().forEach(s -> { if (s.excluidos == null) s.excluidos = new ArrayList<>(); });
		return c;
	}

	public void salvar() {
		try {
			Files.createDirectories(dir());
			Files.writeString(dir().resolve("config.json"), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TexturasMix.LOG.error("Falha ao salvar config", e);
		}
	}

	// ---------- presets ----------
	static Path presetsDir() { return dir().resolve("presets"); }

	static String limpar(String nome) { return nome.replaceAll("[^\\p{L}\\p{N} _-]", "").trim(); }

	public static List<String> presets() {
		List<String> l = new ArrayList<>();
		if (!Files.isDirectory(presetsDir())) return l;
		try (Stream<Path> s = Files.list(presetsDir())) {
			s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5)).sorted(String.CASE_INSENSITIVE_ORDER).forEach(l::add);
		} catch (IOException ignored) {}
		return l;
	}

	public boolean salvarPreset(String nome) {
		nome = limpar(nome);
		if (nome.isEmpty()) return false;
		try {
			Files.createDirectories(presetsDir());
			Files.writeString(presetsDir().resolve(nome + ".json"), GSON.toJson(selecao), StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public boolean carregarPreset(String nome) {
		try {
			Map<String, Selecao> m = GSON.fromJson(Files.readString(presetsDir().resolve(limpar(nome) + ".json")),
					new TypeToken<LinkedHashMap<String, Selecao>>() {}.getType());
			if (m == null) return false;
			m.values().removeIf(s -> s == null || s.pack == null);
			m.values().forEach(s -> { if (s.excluidos == null) s.excluidos = new ArrayList<>(); });
			selecao = new LinkedHashMap<>(m);
			salvar();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void excluirPreset(String nome) {
		try { Files.deleteIfExists(presetsDir().resolve(limpar(nome) + ".json")); } catch (IOException ignored) {}
	}
}
