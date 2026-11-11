package com.texturasmix;

import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tudo que depende da API do Minecraft 26.1.2 (nomes oficiais da Mojang) e é usado pelo código comum. */
final class Plataforma {
	/** resource pack format da 26.1.2 (pack_version.resource_major). */
	static final int FORMATO = 84;
	static final String VERSAO = "26.1.2";
	private static final String MC = "assets/minecraft/";
	private static final Pattern ARMADURA = Pattern.compile("(.+?)_layer_([12])(_overlay)?\\.png");

	static String packMeta(String desc) {
		return "{\"pack\":{\"description\":\"" + desc + "\",\"min_format\":" + FORMATO + ",\"max_format\":" + FORMATO + "}}";
	}

	/** Caminho canônico (layout 1.20.1, usado no índice) -> layout da 26.1.2. */
	static String destino(String canonico) {
		if (canonico.endsWith(".png.mcmeta")) return destino(canonico.substring(0, canonico.length() - 7)) + ".mcmeta";
		if (!canonico.startsWith(MC)) return canonico;
		String r = canonico.substring(MC.length());
		if (r.startsWith("textures/models/armor/")) { // 1.21.2+: textures/entity/equipment/
			Matcher m = ARMADURA.matcher(r.substring(22));
			if (m.matches()) {
				String mat = m.group(1).equals("turtle") ? "turtle_scute" : m.group(1);
				return MC + "textures/entity/equipment/" + (m.group(2).equals("1") ? "humanoid" : "humanoid_leggings") + "/" + mat
						+ (m.group(3) != null ? "_overlay" : "") + ".png";
			}
		}
		if (r.equals("textures/entity/shield_base_nopattern.png") || r.equals("textures/entity/shield_base.png"))
			return MC + "textures/entity/shield/" + r.substring(16);
		return canonico;
	}

	static Path pastaResourcepacks() { return Minecraft.getInstance().getResourcePackDirectory(); }

	static void fecharJogo() { Minecraft.getInstance().stop(); }

	static void naThread(Runnable r) { Minecraft.getInstance().execute(r); }

	private static IoSupplier<InputStream> vanilla(String rel) {
		try {
			String d = destino(MC + rel).substring(MC.length());
			return Minecraft.getInstance().getVanillaPackResources().getResource(PackType.CLIENT_RESOURCES, Identifier.withDefaultNamespace(d));
		} catch (Exception e) {
			return null;
		}
	}

	/** Lê do pack padrão; rel é relativo a assets/minecraft/ (layout canônico). null se não existe. */
	static byte[] lerVanilla(String rel) {
		IoSupplier<InputStream> s = vanilla(rel);
		if (s == null) return null;
		try (InputStream in = s.get()) { return in.readAllBytes(); } catch (Exception e) { return null; }
	}

	static boolean vanillaExiste(String rel) { return vanilla(rel) != null; }

	static String traducao(String chave) {
		Language l = Language.getInstance();
		return l.has(chave) ? l.getOrDefault(chave) : null;
	}

	/** Liga/desliga o pack gerado (no topo da lista) e recarrega. Chamar na thread do jogo. */
	static void aplicarPack(String id, boolean ativo) {
		Minecraft mc = Minecraft.getInstance();
		PackRepository mgr = mc.getResourcePackRepository();
		mgr.reload();
		List<String> antes = new ArrayList<>(mgr.getSelectedIds());
		List<String> depois = new ArrayList<>(antes);
		depois.remove(id);
		if (ativo && mgr.isAvailable(id)) depois.add(id); // último = topo da lista
		if (!depois.equals(antes)) {
			mgr.setSelected(depois);
			mc.options.updateResourcePacks(mgr);
		} else if (ativo) {
			mc.reloadResourcePacks();
		}
	}
}
