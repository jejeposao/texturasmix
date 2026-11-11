package com.texturasmix;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** API pública do Modrinth (sem chave). Toda a rede roda fora da thread do jogo. */
public final class Modrinth {
	static final String UA = "TexturasMix/1.0";
	static final HttpClient HTTP = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(10)).build();

	public static final class Resultado {
		public String id, slug, titulo, descricao, autor, licenca, icone;
		public long downloads;
		public volatile float progresso = -1;
		public volatile String estado = "";
		public volatile String arquivo;
		public volatile List<Previa> previas = List.of();
	}

	public record Previa(String chave, String entrada, byte[] bytes) {}

	public static volatile long usoCache = -1;

	static HttpRequest.Builder req(String url) {
		return HttpRequest.newBuilder(URI.create(url)).header("User-Agent", UA).timeout(Duration.ofSeconds(30));
	}

	static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

	static String str(JsonObject o, String k) {
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? "" : e.getAsString();
	}

	public static CompletableFuture<List<Resultado>> buscar(String q) {
		String facets = "[[\"project_type:resourcepack\"],[\"versions:1.20.1\"]]";
		String url = "https://api.modrinth.com/v2/search?limit=40&index=downloads&query=" + enc(q.trim()) + "&facets=" + enc(facets);
		return HTTP.sendAsync(req(url).GET().build(), HttpResponse.BodyHandlers.ofString()).thenApply(r -> {
			if (r.statusCode() != 200) throw new RuntimeException("HTTP " + r.statusCode());
			List<Resultado> l = new ArrayList<>();
			for (JsonElement e : JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("hits")) {
				JsonObject o = e.getAsJsonObject();
				Resultado x = new Resultado();
				x.id = str(o, "project_id");
				x.slug = str(o, "slug");
				x.titulo = str(o, "title");
				x.descricao = str(o, "description");
				x.autor = str(o, "author");
				x.licenca = str(o, "license").replace("LicenseRef-", "").replace("-", " ");
				x.icone = str(o, "icon_url");
				x.downloads = o.has("downloads") ? o.get("downloads").getAsLong() : 0;
				l.add(x);
			}
			return l;
		});
	}

	public static byte[] icone(Resultado r) throws Exception {
		if (r.icone == null || r.icone.isEmpty()) return null;
		Path f = Config.cache().resolve("icones").resolve(r.id.replaceAll("[^A-Za-z0-9]", "") + ".png");
		if (Files.exists(f)) return Files.readAllBytes(f);
		HttpResponse<byte[]> resp = HTTP.send(req(r.icone).build(), HttpResponse.BodyHandlers.ofByteArray());
		byte[] b = resp.body();
		if (resp.statusCode() != 200 || !Previews.png(b)) return null; // webp/gif/svg: sem prévia
		Files.createDirectories(f.getParent());
		Files.write(f, b);
		return b;
	}

	/** Prévia via HTTP Range (se possível) e download completo em segundo plano; depois indexa. */
	public static void baixar(Resultado r) {
		if (r.progresso >= 0 && r.progresso < 1) return;
		r.progresso = 0;
		r.estado = "Buscando versão 1.20.1…";
		TexturasMix.IO.submit(() -> {
			try {
				String vurl = "https://api.modrinth.com/v2/project/" + r.id + "/version?game_versions=" + enc("[\"1.20.1\"]");
				HttpResponse<String> vr = HTTP.send(req(vurl).build(), HttpResponse.BodyHandlers.ofString());
				if (vr.statusCode() != 200) throw new IOException("HTTP " + vr.statusCode());
				JsonArray vs = JsonParser.parseString(vr.body()).getAsJsonArray();
				if (vs.isEmpty()) throw new IOException("sem versão 1.20.1");
				JsonArray files = vs.get(0).getAsJsonObject().getAsJsonArray("files");
				JsonObject file = files.get(0).getAsJsonObject();
				for (JsonElement fe : files) if (fe.getAsJsonObject().has("primary") && fe.getAsJsonObject().get("primary").getAsBoolean()) file = fe.getAsJsonObject();
				String url = str(file, "url");
				String nome = str(file, "filename").replaceAll("[\\\\/:*?\"<>|]", "_");
				if (!nome.toLowerCase(Locale.ROOT).endsWith(".zip")) nome += ".zip";
				long tam = file.has("size") ? file.get("size").getAsLong() : -1;
				Path dir = TexturasMix.CFG.packs();
				Files.createDirectories(dir);
				Path destino = dir.resolve(nome);
				r.arquivo = nome;
				if (!Files.exists(destino)) {
					r.estado = "Prévia (HTTP Range)…";
					try {
						r.previas = previaRemota(url, 20);
					} catch (Exception e) {
						TexturasMix.LOG.debug("Range indisponível: {}", e.toString());
					}
					r.estado = "Baixando…";
					baixarArquivo(url, destino, tam, r);
				}
				if (r.previas.isEmpty()) r.previas = previaLocal(destino, 20);
				Config.MetaModrinth m = new Config.MetaModrinth();
				m.id = r.id;
				m.slug = r.slug;
				m.titulo = r.titulo;
				m.autor = r.autor;
				m.licenca = r.licenca;
				m.downloads = r.downloads;
				final String arq = nome;
				Plataforma.naThread(() -> {
					TexturasMix.CFG.modrinth.put(arq, m);
					TexturasMix.CFG.salvar();
					Indice.atualizar(false);
				});
				limitarCache(new HashSet<>(Set.of(nome)));
				r.progresso = 1;
				r.estado = "Pronto · indexado";
			} catch (Exception e) {
				r.progresso = -1;
				r.estado = "Erro: " + e.getMessage();
				TexturasMix.LOG.warn("Download falhou", e);
			}
		});
	}

	private static void baixarArquivo(String url, Path destino, long tam, Resultado r) throws Exception {
		HttpResponse<InputStream> resp = HTTP.send(req(url).timeout(Duration.ofMinutes(10)).build(), HttpResponse.BodyHandlers.ofInputStream());
		if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
		long total = resp.headers().firstValueAsLong("content-length").orElse(tam);
		Path tmp = destino.resolveSibling(destino.getFileName() + ".part");
		try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(tmp)) {
			byte[] buf = new byte[65536];
			long lido = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
				lido += n;
				if (total > 0) r.progresso = Math.min(0.99f, (float) lido / total);
			}
		} catch (Exception e) {
			Files.deleteIfExists(tmp);
			throw e;
		}
		Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING);
	}

	// ---------------- prévia via HTTP Range ----------------
	static int le16(byte[] b, int i) { return (b[i] & 255) | (b[i + 1] & 255) << 8; }

	static long le32(byte[] b, int i) { return (le16(b, i) | (long) le16(b, i + 2) << 16) & 0xFFFFFFFFL; }

	static byte[] intervalo(String url, long a, long z) throws Exception {
		HttpResponse<byte[]> r = HTTP.send(req(url).header("Range", "bytes=" + a + "-" + z).build(), HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() != 206) throw new IOException("sem Range");
		return r.body();
	}

	static List<Previa> previaRemota(String url, int max) throws Exception {
		HttpResponse<byte[]> r = HTTP.send(req(url).header("Range", "bytes=-65557").build(), HttpResponse.BodyHandlers.ofByteArray());
		if (r.statusCode() != 206) throw new IOException("servidor sem suporte a Range");
		String cr = r.headers().firstValue("content-range").orElseThrow();
		long total = Long.parseLong(cr.substring(cr.lastIndexOf('/') + 1).trim());
		byte[] t = r.body();
		long ini = total - t.length;
		int e = -1;
		for (int i = t.length - 22; i >= 0; i--) if (le32(t, i) == 0x06054b50L) { e = i; break; }
		if (e < 0) throw new IOException("EOCD não encontrado");
		long cdTam = le32(t, e + 12), cdOff = le32(t, e + 16);
		if (cdOff == 0xFFFFFFFFL) throw new IOException("zip64");
		byte[] cd = cdOff >= ini ? Arrays.copyOfRange(t, (int) (cdOff - ini), (int) (cdOff - ini + cdTam)) : intervalo(url, cdOff, cdOff + cdTam - 1);
		Map<String, long[]> ents = new LinkedHashMap<>();
		int p = 0;
		while (p + 46 <= cd.length && le32(cd, p) == 0x02014b50L) {
			int nl = le16(cd, p + 28), el = le16(cd, p + 30), cl = le16(cd, p + 32);
			String nome = new String(cd, p + 46, nl, StandardCharsets.UTF_8);
			ents.put(nome, new long[]{le16(cd, p + 10), le32(cd, p + 20), le32(cd, p + 42)});
			p += 46 + nl + el + cl;
		}
		List<CompletableFuture<Previa>> fs = new ArrayList<>();
		for (String n : escolher(ents.keySet(), max)) {
			long[] d = ents.get(n);
			long fim = Math.min(total - 1, d[2] + 30 + n.length() * 4L + 1024 + d[1]);
			HttpRequest q = req(url).header("Range", "bytes=" + d[2] + "-" + fim).build();
			fs.add(HTTP.sendAsync(q, HttpResponse.BodyHandlers.ofByteArray()).thenApply(resp -> {
				byte[] b = resp.body();
				if (resp.statusCode() != 206 || le32(b, 0) != 0x04034b50L) return null;
				int ini2 = 30 + le16(b, 26) + le16(b, 28);
				if (ini2 + d[1] > b.length) return null;
				byte[] comp = Arrays.copyOfRange(b, ini2, (int) (ini2 + d[1]));
				byte[] raw = d[0] == 8 ? inflar(comp) : comp;
				return raw == null ? null : new Previa(Indice.chave(n.substring(Indice.MC.length())), n, raw);
			}).exceptionally(x -> null));
		}
		List<Previa> out = new ArrayList<>();
		for (CompletableFuture<Previa> f : fs) {
			Previa pv = f.join();
			if (pv != null) out.add(pv);
		}
		return out;
	}

	static byte[] inflar(byte[] comp) {
		Inflater inf = new Inflater(true);
		try {
			inf.setInput(comp);
			ByteArrayOutputStream o = new ByteArrayOutputStream(comp.length * 2);
			byte[] buf = new byte[16384];
			while (!inf.finished()) {
				int n = inf.inflate(buf);
				if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
				o.write(buf, 0, n);
			}
			return o.toByteArray();
		} catch (Exception e) {
			return null;
		} finally {
			inf.end();
		}
	}

	static List<Previa> previaLocal(Path zip, int max) {
		List<Previa> out = new ArrayList<>();
		try (ZipFile z = Indice.abrirZip(zip)) {
			List<String> nomes = new ArrayList<>();
			z.stream().forEach(e -> nomes.add(e.getName()));
			for (String n : escolher(nomes, max)) {
				ZipEntry e = z.getEntry(n);
				try (InputStream in = z.getInputStream(e)) {
					out.add(new Previa(Indice.chave(n.substring(Indice.MC.length())), n, in.readAllBytes()));
				}
			}
		} catch (Exception ignored) {}
		return out;
	}

	/** Uma textura por item, priorizando as categorias de PvP. */
	static List<String> escolher(Collection<String> nomes, int max) {
		Map<String, List<String>> g = new HashMap<>();
		Map<String, String> real = new HashMap<>();
		for (String n : nomes) {
			if (!n.startsWith(Indice.MC) || !n.endsWith(".png")) continue;
			String v = Conversor.caminho(n, true); // prévia também para packs antigos
			real.putIfAbsent(v, n);
			String k = Indice.chave(v.substring(Indice.MC.length()));
			if (k != null) g.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
		}
		List<String> ks = new ArrayList<>(g.keySet());
		ks.sort(Comparator.comparingInt((String k) -> Categoria.de(k).ordinal()).thenComparing(k -> k));
		List<String> out = new ArrayList<>();
		for (String k : ks) {
			if (out.size() >= max) break;
			String pr = Indice.principal(k, g.get(k));
			if (pr != null) out.add(real.getOrDefault(pr, pr));
		}
		return out;
	}

	// ---------------- cache ----------------
	static List<Path> arquivosCache() {
		List<Path> l = new ArrayList<>();
		if (Files.isDirectory(Config.cache())) {
			try (Stream<Path> s = Files.walk(Config.cache())) { s.filter(Files::isRegularFile).forEach(l::add); } catch (IOException ignored) {}
		}
		for (String n : new ArrayList<>(TexturasMix.CFG.modrinth.keySet())) {
			Path p = TexturasMix.CFG.packs().resolve(n);
			if (Files.isRegularFile(p)) l.add(p);
		}
		return l;
	}

	static long tam(Path p) {
		try { return Files.size(p); } catch (IOException e) { return 0; }
	}

	static long mod(Path p) {
		try { return Files.getLastModifiedTime(p).toMillis(); } catch (IOException e) { return 0; }
	}

	public static void medirCache() {
		TexturasMix.IO.submit(() -> usoCache = arquivosCache().stream().mapToLong(Modrinth::tam).sum());
	}

	/** Apaga os arquivos mais antigos (ícones e packs baixados não usados) até caber no limite. */
	static void limitarCache(Set<String> protegidos) {
		long lim = TexturasMix.CFG.limiteCacheMB * 1048576L;
		List<Path> fs = arquivosCache();
		long tot = fs.stream().mapToLong(Modrinth::tam).sum();
		if (tot > lim) {
			Set<String> usados = new HashSet<>(protegidos);
			try { TexturasMix.CFG.selecao.values().forEach(s -> usados.add(s.pack)); } catch (Exception ignored) {}
			fs.sort(Comparator.comparingLong(Modrinth::mod));
			for (Path p : fs) {
				if (tot <= lim) break;
				String n = p.getFileName().toString();
				if (usados.contains(n) || n.equals("indice.json")) continue;
				long s = tam(p);
				try {
					Files.delete(p);
					tot -= s;
				} catch (IOException ignored) {}
			}
		}
		usoCache = tot;
	}

	public static void limparIcones() {
		TexturasMix.IO.submit(() -> {
			Path d = Config.cache().resolve("icones");
			if (Files.isDirectory(d)) try (Stream<Path> s = Files.list(d)) {
				s.forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
			} catch (IOException ignored) {}
			usoCache = arquivosCache().stream().mapToLong(Modrinth::tam).sum();
		});
	}
}
