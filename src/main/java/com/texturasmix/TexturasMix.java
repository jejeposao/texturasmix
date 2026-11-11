package com.texturasmix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Texturas Mix: navegador de texturas por item. Tudo pesado roda fora da thread do jogo. */
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
	/** Geração do pack (debounce). */
	public static final ScheduledExecutorService AGENDA = Executors.newSingleThreadScheduledExecutor(DAEMON);

	public static Config CFG;
	public static KeyBinding TECLA;

	@Override
	public void onInitializeClient() {
		CFG = Config.carregar();
		TECLA = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.texturasmix.abrir",
				InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.texturasmix"));
		ClientLifecycleEvents.CLIENT_STARTED.register(c -> { // índice incremental + observador de pastas
			Indice.garantir();
			Indice.observar();
			Atualizador.verificar();
		});
		ClientTickEvents.END_CLIENT_TICK.register(c -> {
			while (TECLA.wasPressed()) {
				if (c.currentScreen == null) c.setScreen(new TelaTexturas());
			}
		});
	}
}
