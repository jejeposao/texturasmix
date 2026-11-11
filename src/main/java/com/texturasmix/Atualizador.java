package com.texturasmix;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Atualização automática: lê um JSON de versão (URL configurável, ex.: GitHub raw), baixa o .jar novo em segundo plano,
 * confere o SHA256 e troca o arquivo ao fechar o jogo (vale na próxima abertura). Sem internet: não faz nada.
 * JSON: {"versao":"1.0.1","url":"https://…/texturasmix.jar","sha256":"…"} ou o mesmo objeto dentro de {"1.20.1":{…}}.
 */
final class Atualizador {
	static volatile String novaVersao;
	static volatile boolean pronto;
	private static Path novo, atual;
	private static boolean gancho;

	static void verificar() {
		String url = TexturasMix.CFG.urlAtualizacao;
		if (url == null || url.isBlank()) return;
		TexturasMix.IO.submit(() -> {
			try { executar(url.trim()); } catch (Exception e) { TexturasMix.LOG.info("Sem atualização: {}", e.toString()); }
		});
	}

	private static void executar(String url) throws Exception {
		ModContainer mod = FabricLoader.getInstance().getModContainer("texturasmix").orElse(null);
		if (mod == null || mod.getOrigin().getPaths().isEmpty()) return;
		Path jar = mod.getOrigin().getPaths().get(0);
		if (!Files.isRegularFile(jar) || !jar.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) return; // ambiente de dev
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
				.header("User-Agent", "TexturasMix/1.0").GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (r.statusCode() != 200) return;
		JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
		if (o.has(Plataforma.VERSAO) && o.get(Plataforma.VERSAO).isJsonObject()) o = o.getAsJsonObject(Plataforma.VERSAO);
		String ver = o.has("versao") ? o.get("versao").getAsString() : o.has("version") ? o.get("version").getAsString() : null;
		String link = o.has("url") ? o.get("url").getAsString() : null;
		String sha = o.has("sha256") ? o.get("sha256").getAsString().trim().toLowerCase(Locale.ROOT) : null;
		if (ver == null || link == null || sha == null || sha.length() != 64) return;
		if (!maisNova(ver, mod.getMetadata().getVersion().getFriendlyString())) return;
		novaVersao = ver;
		Path destino = jar.resolveSibling(jar.getFileName() + ".novo"); // .novo não é carregado pelo Fabric
		Path part = jar.resolveSibling(jar.getFileName() + ".part");
		HttpResponse<InputStream> d = http.send(HttpRequest.newBuilder(URI.create(link)).timeout(Duration.ofMinutes(5))
				.header("User-Agent", "TexturasMix/1.0").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
		if (d.statusCode() != 200) return;
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		try (InputStream in = d.body(); OutputStream out = Files.newOutputStream(part)) {
			byte[] buf = new byte[65536];
			for (int n; (n = in.read(buf)) > 0; ) {
				md.update(buf, 0, n);
				out.write(buf, 0, n);
			}
		}
		if (!HexFormat.of().formatHex(md.digest()).equals(sha)) {
			Files.deleteIfExists(part);
			TexturasMix.LOG.warn("Atualização {} descartada: SHA256 não confere", ver);
			novaVersao = null;
			return;
		}
		Files.move(part, destino, StandardCopyOption.REPLACE_EXISTING);
		novo = destino;
		atual = jar;
		pronto = true;
		instalarGancho();
	}

	/** "1.0.10" > "1.0.9"; compara os números em ordem. */
	static boolean maisNova(String a, String b) {
		String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0, q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
			if (p != q) return p > q;
		}
		return false;
	}

	/** Ao fechar o jogo, troca o .jar (no Windows por um .bat que espera o jogo liberar o arquivo). */
	private static synchronized void instalarGancho() {
		if (gancho) return;
		gancho = true;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			if (!pronto || novo == null || !Files.exists(novo)) return;
			try {
				if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
					Path bat = Config.dir().resolve("atualizar.bat");
					Files.writeString(bat, "@echo off\r\nping -n 4 127.0.0.1 >nul\r\nmove /y \"" + novo + "\" \"" + atual + "\" >nul\r\ndel \"%~f0\"\r\n", StandardCharsets.UTF_8);
					new ProcessBuilder("cmd", "/c", bat.toString()).start();
				} else {
					Files.move(novo, atual, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (Exception ignored) {}
		}, "TexturasMix-Atualizar"));
	}
}
