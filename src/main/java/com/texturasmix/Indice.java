package com.texturasmix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Indexa resource packs (zip/pasta) da pasta configurada em variações por item. */
public final class Indice {
	/** pack_format da versão do jogo (maior). */
	public static final int FORMATO = Plataforma.FORMATO;
	static final String MC = "assets/minecraft/";
	private static final Pattern ARMADURA = Pattern.compile("(.+?)_layer_[12](_overlay)?");
	private static final Pattern SUFIXO = Pattern.compile("_(top|bottom|side|front|back|end|inner|outer|on|off|open|closed|still|flow|lit|stage\\d+|\\d+)$");
	private static final int VERSAO_CACHE = 4;

	public static final class Pack {
		public String arquivo, caminho, nome, descricao = "";
		public boolean zip, compativel;
		public long tamanho, mtime;
		/** Quando o pack apareceu na pasta pela primeira vez. */
		public long visto;
		public int formato = -1;
		public List<String> entradas = new ArrayList<>();
		/** Pack de outra versão: entrada 1.20.1 -> entrada real. */
		public Map<String, String> mapa = new HashMap<>();
		/** Chaves cujo modelo não pôde ser convertido (só a textura é usada). */
		public List<String> soTextura = new ArrayList<>();
		public boolean temAntigas;

		public boolean convertido() { return !compativel; }

		public boolean modrinth() { return TexturasMix.CFG.modrinth.containsKey(arquivo); }

		public boolean novo() { return System.currentTimeMillis() - visto < 7L * 24 * 3600 * 1000; }

		public Config.MetaModrinth meta() { return TexturasMix.CFG.modrinth.get(arquivo); }
	}

	public static final class Variacao {
		public final Pack pack;
		public final String chave, id, principal, nomeItem;
		public final Categoria cat;
		public final List<String> arquivos;
		/** Principal lido do pack padrão (pack só troca o modelo). */
		public final boolean texVanilla;
		/** Convertido de outra versão usando só a textura. */
		public final boolean soTextura;

		Variacao(Pack pack, String chave, List<String> arquivos, String principal, boolean texVanilla) {
			this.pack = pack;
			this.chave = chave;
			this.arquivos = arquivos;
			this.principal = principal;
			this.texVanilla = texVanilla;
			this.soTextura = pack != null && pack.soTextura != null && pack.soTextura.contains(chave);
			this.cat = Categoria.de(chave);
			this.id = (pack == null ? "vanilla" : pack.arquivo) + "|" + chave;
			this.nomeItem = nomeItem(chave);
		}

		public boolean vanilla() { return pack == null; }

		public String nomePack() { return pack == null ? "Vanilla" : pack.nome; }

		public long mtime() { return pack == null ? Long.MAX_VALUE : pack.visto; }
	}

	private static volatile Map<Categoria, List<Variacao>> porCat = new EnumMap<>(Categoria.class);
	private static volatile Map<String, Pack> porArquivo = Map.of();
	private static volatile Map<String, Variacao> porId = Map.of();
	public static volatile int versao;
	public static volatile boolean indexando;
	public static volatile String aviso = "";
	/** Há .zip e o usuário ainda não escolheu se quer a biblioteca. */
	public static volatile boolean pedirBiblioteca;
	private static boolean iniciado;
	private static final Map<String, ZipFile> ZIPS = new ConcurrentHashMap<>();

	public static void garantir() {
		if (!iniciado) {
			iniciado = true;
			atualizar(false);
		}
	}

	public static synchronized void atualizar(boolean forcar) {
		if (indexando) return;
		indexando = true;
		TexturasMix.IO.submit(() -> {
			try {
				executar(forcar);
			} catch (Throwable t) {
				TexturasMix.LOG.error("Falha ao indexar", t);
				aviso = "Erro ao indexar: " + t.getMessage();
			} finally {
				indexando = false;
			}
		});
	}

	public static List<Variacao> lista(Categoria c) { return porCat.getOrDefault(c, List.of()); }

	public static Pack pack(String arquivo) { return arquivo == null ? null : porArquivo.get(arquivo); }

	public static Collection<Pack> packs() { return porArquivo.values(); }

	public static Variacao variacao(String arquivo, String chave) {
		return porId.get((arquivo == null ? "vanilla" : arquivo) + "|" + chave);
	}

	public static Variacao porId(String id) { return porId.get(id); }

	// ---------------- varredura ----------------
	private static Path[] pastas() {
		try { Files.createDirectories(Config.biblioteca()); } catch (IOException ignored) {}
		return new Path[]{TexturasMix.CFG.packs(), Plataforma.pastaResourcepacks(), Config.biblioteca()};
	}

	private static boolean ignorado(String n) { return n.equals(Gerador.NOME) || n.endsWith(".part") || n.startsWith("TexturasMix_"); }

	private static long mt(Path p) {
		try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; }
	}

	/** Marca barata de uma pasta-pack: muda quando entram/saem arquivos nas subpastas principais. */
	private static long marcaPasta(Path p) {
		long h = mt(p);
		for (String sub : new String[]{"assets/minecraft/textures", "assets/minecraft/textures/item", "assets/minecraft/textures/block",
				"assets/minecraft/textures/models/armor", "assets/minecraft/textures/entity", "assets/minecraft/models/item"})
			h = h * 31 + mt(p.resolve(sub));
		return h;
	}

	/** Assinatura das pastas (nome + tamanho + data); usada pelo observador. */
	static String assinatura() {
		StringBuilder sb = new StringBuilder();
		for (Path dir : pastas()) {
			sb.append(dir).append('>');
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> s = Files.list(dir)) {
				s.sorted().forEach(p -> {
					String n = p.getFileName().toString();
					if (ignorado(n)) return;
					if (Files.isRegularFile(p) && n.toLowerCase(Locale.ROOT).endsWith(".zip")) {
						try { sb.append(n).append(':').append(Files.size(p)).append(':').append(mt(p)).append(';'); } catch (IOException ignored) {}
					} else if (Files.isDirectory(p)) {
						Path m = p.resolve("pack.mcmeta");
						sb.append(n).append(':').append(Files.exists(m) ? mt(m) + ":" + marcaPasta(p) : "?").append(';');
					}
				});
			} catch (IOException ignored) {}
		}
		return sb.toString();
	}

	private static volatile String indexada, ultimaVista;

	/** Observa as pastas a cada 4 s; indexa (incremental) quando algo mudou e ficou estável. */
	public static void observar() {
		TexturasMix.AGENDA.scheduleWithFixedDelay(() -> {
			try {
				String s = assinatura();
				if (s.equals(ultimaVista) && indexada != null && !s.equals(indexada) && !indexando) atualizar(false);
				ultimaVista = s;
			} catch (Throwable ignored) {}
		}, 6, 4, java.util.concurrent.TimeUnit.SECONDS);
	}

	private static void executar(boolean forcar) throws IOException {
		indexada = assinatura();
		boolean primeira = !Files.exists(Config.cache().resolve("indice.json"));
		Map<String, Pack> cache = lerCache();
		long agora = System.currentTimeMillis();
		List<Pack> lista = new ArrayList<>();
		Set<String> vistos = new HashSet<>(), mantidos = new HashSet<>();
		aviso = "";
		for (Path dir : pastas()) {
			if (!Files.isDirectory(dir)) continue;
			try (Stream<Path> s = Files.list(dir)) {
				for (Path p : (Iterable<Path>) s::iterator) {
					String n = p.getFileName().toString();
					if (ignorado(n) || !vistos.add(n)) continue;
					boolean zip = Files.isRegularFile(p) && n.toLowerCase(Locale.ROOT).endsWith(".zip");
					boolean pasta = Files.isDirectory(p) && Files.exists(p.resolve("pack.mcmeta"));
					if (!zip && !pasta) continue;
					try {
						long mt = Files.getLastModifiedTime(zip ? p : p.resolve("pack.mcmeta")).toMillis();
						long tam = zip ? Files.size(p) : marcaPasta(p);
						Pack c = cache.get(n);
						long visto = c == null ? (primeira ? mt : agora) : (c.visto > 0 ? c.visto : c.mtime);
						if (!forcar && c != null && c.mtime == mt && c.tamanho == tam && c.entradas != null && p.toString().equals(c.caminho)) {
							mantidos.add(c.caminho);
							c.visto = visto;
							lista.add(biblioteca(c, p, vistos));
						} else {
							Pack k = ler(p, n, zip, mt, tam);
							k.visto = visto;
							lista.add(biblioteca(k, p, vistos));
						}
					} catch (Exception e) {
						TexturasMix.LOG.warn("Pack ignorado {}: {}", n, e.toString());
					}
				}
			} catch (IOException e) {
				TexturasMix.LOG.warn("Falha ao listar {}: {}", dir, e.toString());
			}
		}
		fecharExceto(mantidos);
		salvarCache(lista);
		construir(lista);
	}

	private static Pack ler(Path p, String n, boolean zip, long mt, long tam) throws IOException {
		Pack k = new Pack();
		k.arquivo = n;
		k.caminho = p.toString();
		k.zip = zip;
		k.mtime = mt;
		k.tamanho = tam;
		k.nome = limpar(n.replaceAll("(?i)\\.zip$", ""));
		if (k.nome.isEmpty()) k.nome = n;
		List<String> nomes = new ArrayList<>();
		if (zip) {
			try (ZipFile z = abrirZip(p)) {
				ZipEntry m = z.getEntry("pack.mcmeta");
				if (m != null) try (InputStream in = z.getInputStream(m)) { lerMeta(k, in.readAllBytes()); }
				Enumeration<? extends ZipEntry> en = z.entries();
				while (en.hasMoreElements()) {
					ZipEntry e = en.nextElement();
					if (!e.isDirectory()) nomes.add(e.getName());
				}
				processar(k, nomes, en2 -> {
					ZipEntry e = z.getEntry(en2);
					if (e == null) return null;
					try (InputStream in = z.getInputStream(e)) { return in.readAllBytes(); } catch (IOException x) { return null; }
				});
			}
		} else {
			lerMeta(k, Files.readAllBytes(p.resolve("pack.mcmeta")));
			String[] subs = {"assets/minecraft/textures", "assets/minecraft/models", "assets/minecraft/items"};
			for (String sub : subs) {
				Path b = p.resolve(sub);
				if (!Files.isDirectory(b)) continue;
				try (Stream<Path> w = Files.walk(b)) {
					w.filter(Files::isRegularFile).forEach(f -> nomes.add(p.relativize(f).toString().replace('\\', '/')));
				}
			}
			processar(k, nomes, en2 -> {
				try { return Files.readAllBytes(p.resolve(en2)); } catch (IOException x) { return null; }
			});
		}
		return k;
	}

	// ---------------- biblioteca (economia de disco) ----------------
	/** Se ligado, troca o .zip por uma pasta só com as texturas usadas; senão devolve o próprio pack. */
	private static Pack biblioteca(Pack k, Path zip, Set<String> vistos) {
		if (!k.zip) return k;
		int modo = TexturasMix.CFG.biblioteca;
		if (modo == 0) { pedirBiblioteca = true; return k; }
		if (modo != 1) return k;
		try {
			String nome = k.arquivo.replaceAll("(?i)\\.zip$", "").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
			if (nome.isEmpty()) nome = "pack";
			Path dest = Config.biblioteca().resolve(nome), tmp = Config.biblioteca().resolve(nome + ".tmp");
			fecharExceto(new HashSet<>(ZIPS.keySet()) {{ remove(zip.toString()); }});
			Gerador.apagar(tmp);
			try (ZipFile z = abrirZip(zip)) {
				Enumeration<? extends ZipEntry> en = z.entries();
				while (en.hasMoreElements()) {
					ZipEntry e = en.nextElement();
					String n = e.getName();
					if (e.isDirectory() || n.contains("..") || n.startsWith("/") || !util(n)) continue;
					Path f = tmp.resolve(n);
					Files.createDirectories(f.getParent());
					try (InputStream in = z.getInputStream(e)) { Files.copy(in, f); }
				}
			}
			if (!Files.exists(tmp.resolve("pack.mcmeta"))) { Gerador.apagar(tmp); return k; }
			Gerador.apagar(dest);
			Files.move(tmp, dest);
			try {
				Files.delete(zip);
			} catch (IOException emUso) { // zip aberto pelo jogo: mantém o zip e desfaz
				Gerador.apagar(dest);
				return k;
			}
			String antigo = k.arquivo, novoNome = dest.getFileName().toString();
			vistos.add(novoNome);
			Pack f = ler(dest, novoNome, false, Files.getLastModifiedTime(dest.resolve("pack.mcmeta")).toMillis(), marcaPasta(dest));
			f.visto = k.visto;
			Plataforma.naThread(() -> renomear(antigo, novoNome));
			TexturasMix.LOG.info("Biblioteca: {} -> {}", antigo, dest);
			return f;
		} catch (Exception e) {
			TexturasMix.LOG.warn("Falha ao extrair {}: {}", k.arquivo, e.toString());
			return k;
		}
	}

	/** Entradas mantidas na biblioteca: metadados, modelos/definições e texturas de item, bloco, armadura, escudo e cristal. */
	private static boolean util(String n) {
		if (n.equals("pack.mcmeta") || n.equals("pack.png")) return true;
		if (!n.startsWith(MC)) return false;
		String r = n.substring(MC.length());
		if (r.startsWith("models/") || r.startsWith("items/")) return r.endsWith(".json");
		if (!(r.endsWith(".png") || r.endsWith(".mcmeta"))) return false;
		for (String d : new String[]{"textures/item", "textures/block", "textures/models/armor/", "textures/entity/equipment/",
				"textures/entity/shield", "textures/entity/end_crystal/", "textures/entity/endercrystal/"})
			if (r.startsWith(d)) return true;
		return false;
	}

	/** Mantém créditos (Modrinth), seleções e favoritos apontando para a pasta nova. */
	private static void renomear(String antigo, String novo) {
		Config c = TexturasMix.CFG;
		Config.MetaModrinth m = c.modrinth.remove(antigo);
		if (m != null) c.modrinth.put(novo, m);
		c.selecao.values().forEach(s -> { if (antigo.equals(s.pack)) s.pack = novo; });
		Set<String> fav = new LinkedHashSet<>();
		for (String f : c.favoritos) fav.add(f.startsWith(antigo + "|") ? novo + f.substring(antigo.length()) : f);
		c.favoritos = fav;
		c.salvar();
		Gerador.agendar();
	}

	/** Filtra as entradas; em packs de outra versão monta o mapa de conversão e valida os modelos. */
	private static void processar(Pack k, List<String> nomes, java.util.function.Function<String, byte[]> leitor) {
		for (String n : nomes) if (n.startsWith(MC + "textures/items/") || n.startsWith(MC + "textures/blocks/")) { k.temAntigas = true; break; }
		boolean ant = Conversor.antigo(k);
		Set<String> reais = new HashSet<>(nomes);
		for (String n : nomes) {
			if (!n.startsWith(MC)) continue;
			String v = Conversor.caminho(n, ant);
			if (!v.equals(n)) {
				if (reais.contains(v) || k.mapa.containsKey(v)) continue; // o caminho atual já existe: ele vence
				k.mapa.put(v, n);
			}
			if (!relevante(v)) continue;
			if (Conversor.ehModelo(n)) {
				byte[] b = leitor.apply(n);
				if (b == null || Conversor.modelo(k, n, v, b) == null) {
					k.mapa.remove(v);
					String ch = chave(v.substring(MC.length()));
					if (ch != null && !k.soTextura.contains(ch)) k.soTextura.add(ch);
					continue;
				}
			}
			k.entradas.add(v);
		}
	}

	static ZipFile abrirZip(Path p) throws IOException {
		try {
			return new ZipFile(p.toFile(), StandardCharsets.UTF_8);
		} catch (IOException | IllegalArgumentException e) {
			return new ZipFile(p.toFile(), Charset.forName("IBM437"));
		}
	}

	/** Formato "15", "[84, 0]" ou "84.0" -> número maior. */
	private static int maior(JsonElement e) {
		if (e.isJsonArray()) return e.getAsJsonArray().get(0).getAsInt();
		return (int) Math.floor(e.getAsDouble());
	}

	private static void lerMeta(Pack k, byte[] b) {
		try {
			String s = new String(b, StandardCharsets.UTF_8).replace("﻿", "");
			JsonObject pk = JsonParser.parseString(s).getAsJsonObject().getAsJsonObject("pack");
			JsonElement pf = pk.get("pack_format");
			k.formato = pf != null ? maior(pf) : -1;
			JsonElement d = pk.get("description");
			if (d != null) k.descricao = limpar(d.isJsonPrimitive() ? d.getAsString() : d.toString());
			boolean ok = k.formato == FORMATO;
			JsonElement sf = pk.get("supported_formats");
			if (sf != null) {
				int min, max;
				if (sf.isJsonPrimitive()) min = max = sf.getAsInt();
				else if (sf.isJsonArray()) {
					JsonArray a = sf.getAsJsonArray();
					min = a.get(0).getAsInt();
					max = a.get(a.size() - 1).getAsInt();
				} else {
					JsonObject o = sf.getAsJsonObject();
					min = o.get("min_inclusive").getAsInt();
					max = o.get("max_inclusive").getAsInt();
				}
				ok |= FORMATO >= min && FORMATO <= max;
			}
			if (pk.has("min_format") && pk.has("max_format")) { // 1.21.9+
				int min = maior(pk.get("min_format")), max = maior(pk.get("max_format"));
				if (k.formato < 0) k.formato = max;
				ok |= FORMATO >= min && FORMATO <= max;
			}
			k.compativel = ok;
		} catch (Exception e) {
			k.compativel = false;
		}
	}

	private static boolean relevante(String nome) {
		if (!nome.startsWith(MC)) return false;
		String r = nome.substring(MC.length());
		boolean ext = r.endsWith(".png") || r.endsWith(".png.mcmeta") || (r.startsWith("models/item/") && r.endsWith(".json"));
		return ext && chave(r) != null;
	}

	// ---------------- agrupamento ----------------
	/** Caminho relativo a assets/minecraft/ -> chave do item (null = ignorado). */
	/** Nome legível: conserta UTF-8 lido como Latin-1 ("Ã§"), tira §x, controles e lixo. */
	public static String limpar(String s) {
		if (s == null) return "";
		if ((s.contains("Ã") || s.contains("Â")) && s.chars().allMatch(ch -> ch < 256)) {
			String t = new String(s.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
			if (!t.contains("\uFFFD")) s = t;
		}
		s = s.replaceAll("§.?", "").replaceAll("[\\p{Cntrl}\\p{Co}\\p{Cn}\\uFFFD]", "").replaceAll("[_\\s]+", " ").trim();
		return s.codePoints().anyMatch(Character::isLetterOrDigit) ? s : "";
	}

	public static String chave(String rel) {
		String r = rel.endsWith(".mcmeta") ? rel.substring(0, rel.length() - 7) : rel;
		if (r.startsWith("models/item/") && r.endsWith(".json")) {
			String n = r.substring(12, r.length() - 5);
			return n.contains("/") ? null : chaveItem(n);
		}
		if (!r.endsWith(".png")) return null;
		if (r.startsWith("textures/item/")) {
			String n = r.substring(14, r.length() - 4);
			return n.contains("/") ? null : chaveItem(n);
		}
		if (r.startsWith("textures/block/")) {
			String n = r.substring(15, r.length() - 4);
			return n.contains("/") ? null : chaveBloco(n);
		}
		if (r.startsWith("textures/entity/end_crystal/")) return "end_crystal";
		if (r.startsWith("textures/entity/shield")) return "shield";
		if (r.startsWith("textures/models/armor/")) {
			String n = r.substring(22, r.length() - 4);
			if (n.contains("/")) return null;
			Matcher m = ARMADURA.matcher(n);
			return m.matches() ? "armadura:" + m.group(1) : null;
		}
		return null;
	}

	static String chaveItem(String n) {
		if (n.equals("cobweb")) return "b:cobweb"; // item usa a textura do bloco
		if (n.matches("bow_pulling_\\d")) return "bow";
		if (n.startsWith("crossbow")) return "crossbow";
		if (n.startsWith("shield")) return "shield";
		if (n.matches("(splash_|lingering_)?potion(_overlay)?")) return "potion";
		if (n.endsWith("_overlay")) return n.substring(0, n.length() - 8);
		return n;
	}

	static String chaveBloco(String n) {
		if (n.startsWith("respawn_anchor")) return "b:respawn_anchor";
		String a;
		do {
			a = n;
			n = SUFIXO.matcher(n).replaceFirst("");
		} while (!a.equals(n) && !n.isEmpty());
		return "b:" + (n.isEmpty() ? a : n);
	}

	/** Candidatos de textura principal (entradas completas), em ordem de preferência. */
	static List<String> candidatos(String k) {
		List<String> l = new ArrayList<>();
		if (k.startsWith("armadura:")) l.add(MC + "textures/models/armor/" + k.substring(9) + "_layer_1.png");
		else if (k.equals("shield")) l.add(MC + "textures/entity/shield_base_nopattern.png");
		else if (k.equals("b:respawn_anchor")) l.add(MC + "textures/block/respawn_anchor_top.png");
		else if (k.startsWith("b:")) {
			String n = k.substring(2);
			for (String suf : new String[]{"", "_top", "_side", "_front", "_0"}) l.add(MC + "textures/block/" + n + suf + ".png");
		} else {
			l.add(MC + "textures/item/" + k + ".png");
			if (k.equals("crossbow")) l.add(MC + "textures/item/crossbow_standby.png");
		}
		return l;
	}

	static String principal(String k, List<String> arquivos) {
		for (String c : candidatos(k)) if (arquivos.contains(c)) return c;
		for (String a : arquivos) if (a.endsWith(".png") && a.contains("/item/")) return a;
		for (String a : arquivos) if (a.endsWith(".png")) return a;
		return null;
	}

	private static void construir(List<Pack> packs) {
		Map<Categoria, List<Variacao>> res = new EnumMap<>(Categoria.class);
		Map<String, Pack> arq = new HashMap<>();
		Map<String, Variacao> ids = new HashMap<>();
		Set<String> chaves = new TreeSet<>();
		for (Pack p : packs) {
			Config.MetaModrinth meta = p.meta();
			if (meta != null && meta.titulo != null) p.nome = meta.titulo;
			String ln = limpar(p.nome);
			p.nome = ln.isEmpty() ? p.arquivo : ln;
			arq.put(p.arquivo, p);
			Map<String, List<String>> g = new TreeMap<>();
			for (String e : p.entradas) {
				String k = chave(e.substring(MC.length()));
				if (k != null) g.computeIfAbsent(k, x -> new ArrayList<>()).add(e);
			}
			g.forEach((k, fs) -> {
				String pr = principal(k, fs);
				boolean van = false;
				if (pr == null) { // pack só com modelo: prévia usa textura padrão
					pr = vanillaExistente(k);
					van = true;
					if (pr == null) return;
				}
				Variacao v = new Variacao(p, k, List.copyOf(fs), pr, van);
				res.computeIfAbsent(v.cat, x -> new ArrayList<>()).add(v);
				ids.put(v.id, v);
				chaves.add(k);
			});
		}
		for (String k : chaves) {
			String pr = vanillaExistente(k);
			if (pr == null) continue;
			Variacao v = new Variacao(null, k, List.of(pr), pr, true);
			res.computeIfAbsent(v.cat, x -> new ArrayList<>()).add(0, v);
			ids.put(v.id, v);
		}
		porCat = res;
		porArquivo = arq;
		porId = ids;
		versao++;
	}

	private static String vanillaExistente(String k) {
		for (String c : candidatos(k)) {
			if (Plataforma.vanillaExiste(c.substring(MC.length()))) return c;
		}
		return null;
	}

	static String nomeItem(String k) {
		if (k.startsWith("armadura:")) return "Armadura vestida (" + bonito(k.substring(9)) + ")";
		boolean bloco = k.startsWith("b:");
		String n = bloco ? k.substring(2) : k;
		for (String tk : bloco ? new String[]{"block.minecraft." + n} : new String[]{"item.minecraft." + n, "block.minecraft." + n}) {
			String t = Plataforma.traducao(tk);
			if (t != null) return t;
		}
		return bonito(n);
	}

	static String bonito(String n) {
		StringBuilder b = new StringBuilder();
		for (String p : n.split("_")) if (!p.isEmpty()) b.append(b.length() > 0 ? " " : "").append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
		return b.toString();
	}

	// ---------------- leitura ----------------
	/** Lê bytes de uma entrada; pack null = pack padrão do jogo. */
	public static byte[] ler(Pack p, String entrada) throws IOException {
		if (entrada == null || entrada.contains("..")) return null;
		if (p == null) return lerReal(null, entrada);
		String real = p.mapa == null ? entrada : p.mapa.getOrDefault(entrada, entrada);
		byte[] b = lerReal(p, real);
		if (b != null && Conversor.ehModelo(real)) b = Conversor.modelo(p, real, entrada, b);
		return b;
	}

	private static byte[] lerReal(Pack p, String entrada) throws IOException {
		if (p == null) {
			if (!entrada.startsWith(MC)) return null;
			return Plataforma.lerVanilla(entrada.substring(MC.length()));
		}
		if (p.zip) {
			ZipFile z = zip(p);
			ZipEntry e = z.getEntry(entrada);
			if (e == null) return null;
			try (InputStream in = z.getInputStream(e)) { return in.readAllBytes(); }
		}
		Path f = Paths.get(p.caminho, entrada);
		return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
	}

	private static ZipFile zip(Pack p) throws IOException {
		ZipFile z = ZIPS.get(p.caminho);
		if (z != null) return z;
		synchronized (ZIPS) {
			z = ZIPS.get(p.caminho);
			if (z == null) {
				z = abrirZip(Paths.get(p.caminho));
				ZIPS.put(p.caminho, z);
			}
			return z;
		}
	}

	/** Fecha só os zips de packs que mudaram ou saíram (prévias abertas continuam funcionando). */
	private static void fecharExceto(Set<String> manter) {
		synchronized (ZIPS) {
			ZIPS.entrySet().removeIf(e -> {
				if (manter.contains(e.getKey())) return false;
				try { e.getValue().close(); } catch (IOException ignored) {}
				return true;
			});
		}
	}

	public static void fecharZips() {
		synchronized (ZIPS) {
			for (ZipFile z : ZIPS.values()) try { z.close(); } catch (IOException ignored) {}
			ZIPS.clear();
		}
	}

	// ---------------- cache ----------------
	private static final class ArquivoCache {
		int versao;
		List<Pack> packs;
	}

	private static Map<String, Pack> lerCache() {
		Map<String, Pack> m = new HashMap<>();
		try {
			Path f = Config.cache().resolve("indice.json");
			if (!Files.exists(f)) return m;
			ArquivoCache c = Config.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<ArquivoCache>() {}.getType());
			if (c != null && c.packs != null) for (Pack p : c.packs) {
				if (c.versao != VERSAO_CACHE) p.entradas = null; // força reler, mas mantém a data em que foi visto
				if (p.mapa == null) p.mapa = new HashMap<>();
				if (p.soTextura == null) p.soTextura = new ArrayList<>();
				m.put(p.arquivo, p);
			}
		} catch (Exception e) {
			TexturasMix.LOG.warn("Cache de índice inválido: {}", e.toString());
		}
		return m;
	}

	private static void salvarCache(List<Pack> l) {
		try {
			ArquivoCache c = new ArquivoCache();
			c.versao = VERSAO_CACHE;
			c.packs = l;
			Files.createDirectories(Config.cache());
			Files.writeString(Config.cache().resolve("indice.json"), new com.google.gson.Gson().toJson(c), StandardCharsets.UTF_8);
		} catch (IOException e) {
			TexturasMix.LOG.warn("Falha ao salvar cache: {}", e.toString());
		}
	}
}
