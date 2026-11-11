package com.texturasmix;

import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tudo que depende da API do Minecraft 1.20.1 e é usado pelo código comum (Indice, Gerador, Modrinth). */
final class Plataforma {
	/** pack_format desta versão. */
	static final int FORMATO = 15;
	static final String VERSAO = "1.20.1";

	static String packMeta(String desc) {
		return "{\"pack\":{\"pack_format\":" + FORMATO + ",\"description\":\"" + desc + "\"}}";
	}

	/** Caminho canônico (layout 1.20.1) -> caminho desta versão. */
	static String destino(String canonico) { return canonico; }

	static Path pastaResourcepacks() { return MinecraftClient.getInstance().getResourcePackDir(); }

	static void fecharJogo() { MinecraftClient.getInstance().scheduleStop(); }

	static void naThread(Runnable r) { MinecraftClient.getInstance().execute(r); }

	/** Lê do pack padrão; rel é relativo a assets/minecraft/. null se não existe. */
	static byte[] lerVanilla(String rel) {
		try {
			InputSupplier<InputStream> s = MinecraftClient.getInstance().getDefaultResourcePack()
					.open(ResourceType.CLIENT_RESOURCES, new Identifier("minecraft", rel));
			if (s == null) return null;
			try (InputStream in = s.get()) { return in.readAllBytes(); }
		} catch (Exception e) {
			return null;
		}
	}

	static boolean vanillaExiste(String rel) {
		try {
			return MinecraftClient.getInstance().getDefaultResourcePack()
					.open(ResourceType.CLIENT_RESOURCES, new Identifier("minecraft", rel)) != null;
		} catch (Exception e) {
			return false;
		}
	}

	static String traducao(String chave) {
		Language l = Language.getInstance();
		return l.hasTranslation(chave) ? l.get(chave) : null;
	}

	/** Liga/desliga o pack gerado (no topo da lista) e recarrega. Chamar na thread do jogo. */
	static void aplicarPack(String id, boolean ativo) {
		MinecraftClient mc = MinecraftClient.getInstance();
		ResourcePackManager mgr = mc.getResourcePackManager();
		mgr.scanPacks();
		List<String> antes = new ArrayList<>(mgr.getEnabledNames());
		List<String> depois = new ArrayList<>(antes);
		depois.remove(id);
		if (ativo && mgr.hasProfile(id)) depois.add(id); // último = topo da lista
		if (!depois.equals(antes)) {
			mgr.setEnabledProfiles(depois);
			mc.options.refreshResourcePacks(mgr);
		} else if (ativo) {
			mc.reloadResources();
		}
	}
}
