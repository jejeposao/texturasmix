package com.texturasmix;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Texturas Mix (26.1.2): navegador de texturas por item. Tudo pesado roda fora da thread do jogo. */
public class TexturasMix implements ClientModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("TexturasMix");
	private static final AtomicInteger N = new AtomicInteger();
	private static final ThreadFactory DAEMON = r -> {
		Thread t = new Thread(r, "TexturasMix-" + N.incrementAndGet());
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	};
	/** Leitura de zips, rede e prévias. */
	public static final ExecutorService IO = Executors.newFixedThreadPool(3, DAEMON);
	/** Geração do pack (debounce) e observador de pastas. */
	public static final ScheduledExecutorService AGENDA = Executors.newSingleThreadScheduledExecutor(DAEMON);

	public static Config CFG;
	public static KeyMapping TECLA;

	@Override
	public void onInitializeClient() {
		CFG = Config.carregar();
		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("texturasmix", "main"));
		TECLA = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.texturasmix.abrir", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, cat));
		ClientLifecycleEvents.CLIENT_STARTED.register(c -> { // índice incremental + observador de pastas
			Indice.garantir();
			Indice.observar();
			Atualizador.verificar();
		});
		ClientTickEvents.END_CLIENT_TICK.register(c -> {
			while (TECLA.consumeClick()) {
				if (c.screen == null) c.setScreen(new TelaTexturas());
			}
		});
	}
}
