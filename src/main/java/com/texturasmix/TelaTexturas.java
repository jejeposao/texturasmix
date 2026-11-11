package com.texturasmix;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.texturasmix.TexturasMix.CFG;

/** Tela principal (tecla N): categorias, grade de variações, inspetor, Modrinth, créditos e configurações. */
public class TelaTexturas extends TelaBase {
	static final int BG = 0xFF0B0F1A, CARD = 0xFF141B2D, BORDA = 0xFF222A3E, LINHA = 0xFF1A2133, TXT = 0xFFE6EAF2,
			CINZA = 0xFF8A93A6, CINZA2 = 0xFF5D667A, ESCURO = 0xFF0F1526, VERDE = 0xFF22C55E, AMARELO = 0xFFF5C542,
			VERMELHO = 0xFFEF4444, CAMPO = 0xFF111829;
	static final int[] TEMAS = {0xFF9B5CF6, 0xFF3B82F6, 0xFF22C55E, 0xFFEC4899, 0xFFF59E0B};
	static final String[] NOMES_TEMAS = {"Roxo", "Azul", "Verde", "Rosa", "Âmbar"};
	static final String[] SUGESTOES = {"sword", "short sword", "pvp 16x", "crystal pvp", "totem", "armor durability", "overlay"};
	static final String[] ORDENS = {"A–Z", "Mais novos", "Mais antigos"};

	enum Fonte {
		TODOS("Todos"), MEUS("Meus packs"), MODRINTH("Modrinth"), FAVORITOS("★ Favoritos");
		final String rotulo;

		Fonte(String r) { rotulo = r; }
	}

	enum Vista { GRADE, EXPLORAR, CREDITOS, CONFIG }

	enum Modal { NENHUM, ZOOM, PRESETS, AVULSAS, BIBLIOTECA }

	record Area(float x, float y, float w, float h, Runnable esq, Runnable dir, boolean modal) {
		boolean dentro(float px, float py) { return px >= x && px < x + w && py >= y && py < y + h; }
	}

	static final class Campo {
		String txt = "";
		String dica;
		Runnable enter;

		Campo(String dica) { this.dica = dica; }
	}

	float f = 1, vw, vh, mx, my;
	float PX, PY, PW, PH, topo, base, sideW = 150, inspW = 182, cenX, cenW, inspX;
	int abaEsq, abaDir, ordem = 1;
	Categoria cat = Categoria.CRISTAL;
	Fonte fonte = Fonte.TODOS;
	Vista vista = Vista.GRADE;
	Modal modal = Modal.NENHUM;
	Indice.Variacao foco;
	List<Indice.Variacao> lista = new ArrayList<>();
	boolean multi;
	String chaveLista = "";
	float scGrade, scSide, scExp, scLista, scModal, maxGrade, maxSide, maxExp, maxLista, maxModal;
	final Campo busca = new Campo("Buscar estilos..."), buscaMod = new Campo("Buscar no Modrinth..."),
			nomePreset = new Campo("Nome do preset"), campoPasta = new Campo("Pasta de packs"), campoLimite = new Campo("MB"), campoUrl = new Campo("https://raw.githubusercontent.com/…/versao.json");
	Campo focado;
	final List<Area> areas = new ArrayList<>();
	boolean emModal;
	float[] clip;
	List<Modrinth.Resultado> resultados = List.of();
	Modrinth.Resultado focoMod;
	String estadoBusca = "", toast = "", dica;
	boolean buscouMod;
	long toastAte;
	List<String> presets = List.of();


	int acc() { return TEMAS[Math.floorMod(CFG.tema, TEMAS.length)]; }

	int accA(int a) { return (acc() & 0xFFFFFF) | a << 24; }

	@Override
	void aoAbrir() {
		Previews.abrir();
		Indice.garantir();
		Modrinth.medirCache();
		Ui.reavaliarFonte();
		campoPasta.txt = CFG.pastaPacks;
		campoPasta.dica = Config.pastaPadrao() + " (padrão)";
		campoLimite.txt = String.valueOf(CFG.limiteCacheMB);
		buscaMod.enter = () -> buscar(buscaMod.txt);
		nomePreset.enter = this::salvarPreset;
		campoPasta.enter = this::salvarConfig;
		campoLimite.enter = this::salvarConfig;
		campoUrl.txt = CFG.urlAtualizacao;
		campoUrl.enter = this::salvarConfig;
	}

	@Override
	void aoFechar() {
		Previews.fechar();
		CFG.salvar();
	}

	void aviso(String s) {
		toast = s;
		toastAte = agora() + 4000;
	}

	// ======================= render =======================
	@Override
	void desenhar(G c, int mouseX, int mouseY) {
		f = Math.min(width / 960f, height / 540f);
		vw = width / f;
		vh = height / f;
		mx = mouseX / f;
		my = mouseY / f;
		areas.clear();
		emModal = false;
		clip = null;
		dica = null;
		if (Indice.pedirBiblioteca && CFG.biblioteca == 0 && modal == Modal.NENHUM) {
			Indice.pedirBiblioteca = false; // pergunta uma vez por indexação
			modal = Modal.BIBLIOTECA;
		}
		c.gradiente(0, 0, width, height, 0xF2121A2C, 0xF2070A12);
		c.push();
		c.escala(f);
		PX = 16;
		PY = 14;
		PW = vw - 32;
		PH = vh - 28;
		topo = PY + 42;
		base = PY + PH - 30;
		cenX = PX + sideW;
		inspX = PX + PW - inspW;
		cenW = inspX - cenX;
		atualizarLista();
		Ui.caixa(c, PX, PY, PW, PH, 7, BG, 0xFF1D2538);
		Ui.rr(c, PX + 1, topo, PW - 2, 0.75f, 0, LINHA);
		Ui.rr(c, PX + 1, base, PW - 2, 0.75f, 0, LINHA);
		Ui.rr(c, cenX, topo, 0.75f, base - topo, 0, LINHA);
		Ui.rr(c, inspX, topo, 0.75f, base - topo, 0, LINHA);
		cabecalho(c);
		lateral(c);
		switch (vista) {
			case GRADE -> grade(c);
			case EXPLORAR -> explorar(c);
			case CREDITOS -> creditos(c);
			case CONFIG -> config(c);
		}
		inspetor(c);
		rodape(c);
		if (dica != null && modal == Modal.NENHUM) desenharDica(c);
		if (modal != Modal.NENHUM) {
			emModal = true;
			Ui.rr(c, 0, 0, vw, vh, 0, 0xB4000000);
			switch (modal) {
				case ZOOM -> zoom(c);
				case PRESETS -> modalPresets(c);
				case AVULSAS -> modalAvulsas(c);
				case BIBLIOTECA -> modalBiblioteca(c);
				default -> {}
			}
		}
		c.pop();
	}

	// ---------- utilitários de interação ----------
	boolean hov(float x, float y, float w, float h) {
		if (modal != Modal.NENHUM && !emModal) return false;
		if (clip != null && (mx < clip[0] || my < clip[1] || mx >= clip[2] || my >= clip[3])) return false;
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	void area(float x, float y, float w, float h, Runnable esq, Runnable dir) {
		if (clip != null) {
			float x1 = Math.max(x, clip[0]), y1 = Math.max(y, clip[1]), x2 = Math.min(x + w, clip[2]), y2 = Math.min(y + h, clip[3]);
			if (x2 <= x1 || y2 <= y1) return;
			x = x1; y = y1; w = x2 - x1; h = y2 - y1;
		}
		areas.add(new Area(x, y, w, h, esq, dir, emModal));
	}

	void recortar(G c, float x, float y, float w, float h) {
		clip = new float[]{x, y, x + w, y + h};
		c.recorte((int) Math.floor(x * f), (int) Math.floor(y * f), (int) Math.ceil((x + w) * f), (int) Math.ceil((y + h) * f));
	}

	void soltar(G c) {
		clip = null;
		c.semRecorte();
	}

	void barra(G c, float x, float y, float h, float sc, float max) {
		if (max <= 0) return;
		float th = Math.max(16, h * h / (h + max));
		Ui.rr(c, x, y, 2, h, 1, 0x22FFFFFF);
		Ui.rr(c, x, y + (h - th) * Math.min(1, sc / max), 2, th, 1, accA(0xAA));
	}

	static float limitar(float v, float max) { return Math.max(0, Math.min(v, Math.max(0, max))); }

	/** Botão: estilo 0 contorno, 1 cheio (destaque), 2 contorno destaque. Retorna largura. */
	float botao(G c, float x, float y, float w, float h, String s, int estilo, Runnable acao) {
		boolean hv = hov(x, y, w, h);
		float e = 0.68f;
		if (w <= 0) w = Ui.largEsp(s, true, e, 1.2f) + 16;
		if (estilo == 1) Ui.rr(c, x, y, w, h, 3.5f, hv ? brilho(acc(), 18) : acc());
		else if (estilo == 2) Ui.caixa(c, x, y, w, h, 3.5f, hv ? accA(0x40) : accA(0x22), acc());
		else Ui.caixa(c, x, y, w, h, 3.5f, hv ? 0xFF1A2236 : 0xFF121A2B, hv ? 0xFF3A4560 : 0xFF2A3348);
		float tw = Ui.largEsp(s, true, e, 1.2f);
		Ui.txtEsp(c, s, x + (w - tw) / 2, y + (h - 9 * e) / 2 + 0.6f, estilo == 0 ? 0xFFD6DCE8 : 0xFFFFFFFF, true, e, 1.2f);
		area(x, y, w, h, acao, null);
		return w;
	}

	static int brilho(int c, int d) {
		int r = Math.min(255, (c >> 16 & 255) + d), g = Math.min(255, (c >> 8 & 255) + d), b = Math.min(255, (c & 255) + d);
		return c & 0xFF000000 | r << 16 | g << 8 | b;
	}

	float chip(G c, String s, float x, float y, boolean ativo, Runnable acao) {
		float e = 0.7f, w = Ui.larg(s, false, e) + 14, h = 15;
		boolean hv = hov(x, y, w, h);
		if (ativo) Ui.caixa(c, x, y, w, h, 3, accA(0x30), acc());
		else Ui.caixa(c, x, y, w, h, 3, CARD, hv ? 0xFF3A4560 : BORDA);
		Ui.txt(c, s, x + 7, y + 4, ativo ? TXT : CINZA, false, e);
		area(x, y, w, h, acao, null);
		return w;
	}

	float aba(G c, String s, float x, float y, boolean ativo, Runnable acao) {
		float e = 0.66f, w = Ui.largEsp(s, false, e, 1.4f);
		boolean hv = hov(x - 2, y - 3, w + 4, 15);
		Ui.txtEsp(c, s, x, y, ativo ? acc() : hv ? TXT : CINZA, false, e, 1.4f);
		if (ativo) Ui.rr(c, x, y + 9.5f, w, 1, 0, acc());
		area(x - 2, y - 3, w + 4, 15, acao, null);
		return w;
	}

	void campo(G c, Campo cp, float x, float y, float w, float h) {
		boolean fz = focado == cp;
		Ui.caixa(c, x, y, w, h, 4, CAMPO, fz ? acc() : hov(x, y, w, h) ? 0xFF3A4560 : BORDA);
		boolean vazio = cp.txt.isEmpty();
		float e = 0.72f;
		String s = vazio && !fz ? cp.dica : cp.txt;
		while (s.length() > 1 && Ui.larg(s, false, e) > w - 14) s = s.substring(1);
		Ui.txt(c, s, x + 6, y + (h - 9 * e) / 2 + 0.6f, vazio && !fz ? CINZA2 : TXT, false, e);
		if (fz && agora() / 500 % 2 == 0) Ui.rr(c, x + 6.5f + (vazio ? 0 : Ui.larg(s, false, e)), y + 4, 0.8f, h - 8, 0, TXT);
		area(x, y, w, h, () -> focado = cp, null);
	}

	float tag(G c, String s, float x, float y, int bg, int fg, int borda, float limite) {
		float e = 0.4f, w = Ui.larg(s, true, e) + 4;
		if (x + w > limite) return x;
		if (borda != 0) Ui.caixa(c, x, y, w, 6.5f, 1.2f, bg, borda, 0.5f);
		else Ui.rr(c, x, y, w, 6.5f, 1.2f, bg);
		Ui.txt(c, s, x + 2, y + 1.6f, fg, true, e);
		return x + w + 2;
	}

	Previews.Tex previa(Indice.Variacao v) {
		return Previews.de("v:" + v.id, () -> Indice.ler(v.texVanilla ? null : v.pack, v.principal));
	}

	/** Armadura e blocos ganham prévia 3D (cacheada); o resto usa a textura. */
	static boolean tem3d(Indice.Variacao v) { return v.chave.startsWith("armadura:") || v.chave.startsWith("b:") && !v.chave.equals("b:cobweb"); }

	Previews.Tex previa3d(Indice.Variacao v) {
		return Previews.deImagem("3d:" + v.id, () -> v.chave.startsWith("armadura:") ? Render3D.armadura(v) : Render3D.bloco(v));
	}

	/** Prévia do card/inspetor: 3D (vitrine/isométrico) ou textura grande e nítida com brilho roxo atrás. */
	void desenharPrevia(G c, Indice.Variacao v, float x, float y, float w, float h, int quadro) {
		if (tem3d(v)) {
			Previews.Tex t = previa3d(v);
			if (!t.falhou) {
				float s = Math.min(w, h) * (v.chave.startsWith("armadura:") ? 0.98f : 0.82f);
				tex(c, t, "", x + (w - s) / 2, y + (h - s) / 2, s, 0);
				return;
			}
		}
		float s = Math.min(w, h) * 0.70f, cx = x + w / 2, cy = y + h / 2;
		for (int i = 4; i >= 1; i--) {
			float g = s * (0.5f + 0.17f * i);
			Ui.rr(c, cx - g / 2, cy - g / 2, g, g, g / 2, accA(0x12));
		}
		tex(c, previa(v), v.chave, cx - s / 2, cy - s / 2, s, quadro);
	}

	Previews.Tex icone(Categoria k) { return Previews.de("c:" + k, () -> Indice.ler(null, k.icone)); }

	/** Desenha uma prévia centralizada em s×s (1º quadro se animada; recorte do escudo). */
	void tex(G c, Previews.Tex t, String chave, float x, float y, float s, int quadro) {
		if (t == null || t.id == null) {
			if (t != null && !t.falhou) Ui.rr(c, x + s / 2 - 1.5f, y + s / 2 - 1.5f, 3, 3, 1.5f, (agora() / 300 % 2 == 0) ? CINZA2 : BORDA);
			return;
		}
		int w = t.w, h = t.h, rw = w, rh = h;
		float u = 0, v = 0;
		if ("shield".equals(chave) && w == h && w >= 64) {
			float k = w / 64f;
			u = k; v = k;
			rw = Math.round(12 * k);
			rh = Math.round(22 * k);
		} else if (h > w && h % w == 0) {
			rh = w;
			v = (float) (quadro % (h / w)) * w;
		}
		float esc = Math.min(s / rw, s / rh), dw = rw * esc, dh = rh * esc;
		c.textura(t, x + (s - dw) / 2, y + (s - dh) / 2, esc, rw, rh, u, v, w, h);
	}

	void desenharDica(G c) {
		float e = 0.66f, w = Ui.larg(dica, false, e) + 10, x = Math.min(mx + 8, vw - w - 4), y = my + 10;
		Ui.caixa(c, x, y, w, 13, 3, 0xF0101626, 0xFF2A3348);
		Ui.txt(c, dica, x + 5, y + 3.5f, TXT, false, e);
	}

	// ---------- dados ----------
	void atualizarLista() {
		String k = cat + "|" + fonte + "|" + ordem + "|" + busca.txt + "|" + Indice.versao + "|" + CFG.favoritos.size() + "|" + CFG.modrinth.size();
		if (k.equals(chaveLista)) return;
		chaveLista = k;
		String q = busca.txt.toLowerCase(Locale.ROOT).trim();
		List<Indice.Variacao> out = new ArrayList<>();
		Set<String> chaves = new HashSet<>();
		for (Indice.Variacao v : Indice.lista(cat)) {
			switch (fonte) {
				case MEUS -> { if (v.vanilla() || v.pack.modrinth()) continue; }
				case MODRINTH -> { if (v.vanilla() || !v.pack.modrinth()) continue; }
				case FAVORITOS -> { if (!CFG.favoritos.contains(v.id)) continue; }
				default -> {}
			}
			if (!q.isEmpty() && !v.nomePack().toLowerCase(Locale.ROOT).contains(q) && !v.nomeItem.toLowerCase(Locale.ROOT).contains(q)) continue;
			out.add(v);
			chaves.add(v.chave);
		}
		Comparator<Indice.Variacao> cmp = switch (ordem) {
			case 0 -> Comparator.comparing((Indice.Variacao v) -> v.nomePack().toLowerCase(Locale.ROOT));
			case 2 -> Comparator.comparingLong((Indice.Variacao v) -> v.mtime());
			default -> Comparator.comparingLong((Indice.Variacao v) -> -v.mtime());
		};
		out.sort(Comparator.comparing((Indice.Variacao v) -> !v.vanilla()).thenComparing(cmp).thenComparing(v -> v.chave));
		lista = out;
		multi = chaves.size() > 1;
		if (foco != null) foco = Indice.porId(foco.id);
	}

	boolean equipado(Indice.Variacao v) {
		Config.Selecao s = CFG.selecao.get(v.chave);
		return v.vanilla() ? s == null : s != null && s.pack.equals(v.pack.arquivo);
	}

	String atualChave(String chave) {
		Config.Selecao s = CFG.selecao.get(chave);
		if (s == null) return "Vanilla";
		Indice.Pack p = Indice.pack(s.pack);
		return p != null ? p.nome : s.pack;
	}

	String atual(Categoria k) {
		String um = null;
		int n = 0;
		for (String ch : CFG.selecao.keySet()) if (Categoria.de(ch) == k) { n++; um = ch; }
		return n == 0 ? "Vanilla" : n == 1 ? atualChave(um) : n + " itens";
	}

	void selecionar(Indice.Variacao v) {
		foco = v;
		Config.Selecao s = CFG.selecao.get(v.chave);
		if (v.vanilla()) {
			if (s == null) return;
			CFG.selecao.remove(v.chave);
		} else {
			if (s != null && s.pack.equals(v.pack.arquivo)) return;
			CFG.selecao.put(v.chave, new Config.Selecao(v.pack.arquivo));
		}
		CFG.salvar();
		Gerador.agendar();
		aviso(v.nomeItem + ": " + v.nomePack());
	}

	void favoritar(Indice.Variacao v) {
		if (!CFG.favoritos.remove(v.id)) CFG.favoritos.add(v.id);
		CFG.salvar();
		chaveLista = "";
	}

	void abrir(Indice.Variacao v) {
		cat = v.cat;
		vista = Vista.GRADE;
		abaEsq = 0;
		foco = v;
		chaveLista = "";
	}

	// ======================= cabeçalho =======================
	void cabecalho(G c) {
		Ui.rr(c, PX + 12, PY + 10, 22, 22, 5, acc());
		Ui.txtC(c, "T", PX + 23, PY + 15.5f, 0xFFFFFFFF, true, 1.15f);
		Ui.txtEsp(c, "TEXTURAS MIX", PX + 42, PY + 11, TXT, true, 0.92f, 1.2f);
		Ui.txtEsp(c, "NAVEGADOR DE TEXTURAS", PX + 42, PY + 24, CINZA, false, 0.56f, 1.6f);
		String st = agora() < toastAte ? toast : Atualizador.pronto ? "Atualização " + Atualizador.novaVersao + " disponível" : Indice.indexando ? "Indexando packs…" : !Indice.aviso.isEmpty() ? Indice.aviso : Gerador.status;
		// barra de equipados
		List<Map.Entry<String, Config.Selecao>> sel = new ArrayList<>(CFG.selecao.entrySet());
		float sz = 18, gap = 3, dir = PX + PW - 12;
		int max = Math.max(1, (int) ((PW - 420) / (sz + gap)));
		int n = Math.min(sel.size(), max);
		float x0 = dir - Math.max(1, n) * (sz + gap) + gap, y = PY + 12;
		Ui.txtEsp(c, "EQUIPADOS", x0 - 10 - Ui.largEsp("EQUIPADOS", false, 0.6f, 1.6f), y + 6, CINZA, false, 0.6f, 1.6f);
		if (st != null && !st.isEmpty()) {
			float lim = x0 - 20 - Ui.largEsp("EQUIPADOS", false, 0.6f, 1.6f) - (PX + 200);
			Ui.txt(c, Ui.cortar(st, lim, false, 0.66f), PX + 200, y + 6, accA(0xFF), false, 0.66f);
			if (Atualizador.pronto && agora() >= toastAte)
				botao(c, PX + 200 + Math.min(lim, Ui.larg(st, false, 0.66f)) + 8, y + 1, 0, 16, "REINICIAR AGORA", 1, Plataforma::fecharJogo);
		}
		if (n == 0) {
			Ui.caixa(c, x0, y, sz, sz, 3, CARD, BORDA);
			Ui.txtC(c, "–", x0 + sz / 2, y + 5, CINZA2, false, 0.8f);
		}
		for (int i = 0; i < n; i++) {
			Map.Entry<String, Config.Selecao> e = sel.get(i);
			Indice.Variacao v = Indice.variacao(e.getValue().pack, e.getKey());
			float x = x0 + i * (sz + gap);
			boolean hv = hov(x, y, sz, sz);
			Ui.caixa(c, x, y, sz, sz, 3, hv ? accA(0x30) : CARD, hv || (v != null && v == foco) ? acc() : BORDA);
			if (v != null) {
				tex(c, previa(v), v.chave, x + 2.5f, y + 2.5f, sz - 5, 0);
				if (hv) dica = v.nomeItem + " · " + v.nomePack();
				area(x, y, sz, sz, () -> abrir(v), null);
			}
		}
	}

	// ======================= lateral =======================
	void lateral(G c) {
		float x = PX, y = topo, w = sideW;
		float ax = x + 14;
		ax += aba(c, "COLEÇÕES", ax, y + 12, abaEsq == 0, () -> abaEsq = 0) + 12;
		aba(c, "EXTRAS", ax, y + 12, abaEsq == 1, () -> abaEsq = 1);
		float ly = y + 30, lh = base - ly - 4;
		recortar(c, x + 1, ly, w - 2, lh);
		float ry = ly + 2 - scSide;
		if (abaEsq == 0) {
			for (Categoria k : Categoria.values()) {
				linhaCat(c, k, x + 7, ry, w - 14, 22);
				ry += 23.5f;
			}
		} else {
			ry = linhaExtra(c, "Explorar Modrinth", "Buscar e baixar packs 1.20.1", x + 7, ry, w - 14, vista == Vista.EXPLORAR, () -> vista = Vista.EXPLORAR);
			ry = linhaExtra(c, "Créditos", "Pack, autor e licença", x + 7, ry, w - 14, vista == Vista.CREDITOS, () -> vista = Vista.CREDITOS);
			ry = linhaExtra(c, "Configurações", "Pasta, cache e limites", x + 7, ry, w - 14, vista == Vista.CONFIG, () -> vista = Vista.CONFIG);
			ry = linhaExtra(c, "Atualizar packs", "Reindexar a pasta", x + 7, ry, w - 14, false, () -> { Indice.atualizar(true); aviso("Reindexando…"); });
			ry = linhaExtra(c, "Abrir pasta de packs", Ui.cortar(CFG.packs().toString(), 110, false, 0.6f), x + 7, ry, w - 14, false, this::abrirPasta);
			ry = linhaExtra(c, "Voltar às coleções", "Grade de texturas", x + 7, ry, w - 14, false, () -> { vista = Vista.GRADE; abaEsq = 0; });
		}
		soltar(c);
		maxSide = ry + scSide - (ly + lh);
		scSide = limitar(scSide, maxSide);
		barra(c, x + w - 4, ly, lh, scSide, maxSide);
	}

	void linhaCat(G c, Categoria k, float x, float y, float w, float h) {
		boolean sel = vista == Vista.GRADE && cat == k, hv = hov(x, y, w, h);
		if (sel) {
			Ui.caixa(c, x, y, w, h, 4, accA(0x26), accA(0x80));
			Ui.rr(c, x, y + 3, 2, h - 6, 1, acc());
		} else if (hv) Ui.rr(c, x, y, w, h, 4, 0xFF131A2A);
		Ui.rr(c, x + 6, y + 4, 14, 14, 3, 0xFF1A2236);
		tex(c, icone(k), k == Categoria.ESCUDO ? "shield" : "", x + 7.5f, y + 5.5f, 11, 0);
		Ui.txt(c, k.nome, x + 26, y + 3, TXT, false, 0.78f);
		String sub = Indice.limpar(atual(k));
		if (!sub.isEmpty()) Ui.txt(c, Ui.cortar(sub, w - 60, false, 0.6f), x + 26, y + 12.5f, 0xFFA0A8C0, false, 0.6f);
		Ui.txtD(c, String.valueOf(Indice.lista(k).size()), x + w - 6, y + 7.5f, CINZA, false, 0.66f);
		area(x, y, w, h, () -> { cat = k; vista = Vista.GRADE; scGrade = 0; foco = null; }, null);
	}

	float linhaExtra(G c, String t, String sub, float x, float y, float w, boolean sel, Runnable acao) {
		float h = 26;
		boolean hv = hov(x, y, w, h);
		if (sel) {
			Ui.caixa(c, x, y, w, h, 4, accA(0x26), accA(0x80));
			Ui.rr(c, x, y + 3, 2, h - 6, 1, acc());
		} else if (hv) Ui.rr(c, x, y, w, h, 4, 0xFF131A2A);
		Ui.txt(c, t, x + 9, y + 4, TXT, false, 0.78f);
		Ui.txt(c, sub, x + 9, y + 14.5f, CINZA2, false, 0.6f);
		area(x, y, w, h, acao, null);
		return y + h + 3;
	}

	void abrirPasta() {
		try {
			Files.createDirectories(CFG.packs());
			abrir(CFG.packs().toFile());
		} catch (Exception e) {
			aviso("Não foi possível abrir a pasta");
		}
	}

	// ======================= grade =======================
	void grade(G c) {
		float x = cenX + 14, y = topo + 10, w = cenW - 28;
		Ui.txt(c, cat.nome, x, y, TXT, true, 1.35f);
		Ui.txt(c, lista.size() + " variações · Atual: " + atual(cat), x, y + 16, CINZA, false, 0.72f);
		float bw = Math.min(170, w * 0.36f);
		campo(c, busca, x + w - bw, y, bw, 18);
		float cy = y + 32, cx = x;
		for (Fonte fo : Fonte.values()) cx += chip(c, fo.rotulo, cx, cy, fonte == fo, () -> { fonte = fo; scGrade = 0; }) + 5;
		float ox = x + w;
		for (int i = ORDENS.length - 1; i >= 0; i--) {
			final int o = i;
			float cw = Ui.larg(ORDENS[i], false, 0.7f) + 14;
			ox -= cw;
			if (ox < cx + 6) break;
			chip(c, ORDENS[i], ox, cy, ordem == i, () -> ordem = o);
			ox -= 5;
		}
		float gy = cy + 22, gh = base - 6 - gy;
		if (lista.isEmpty()) {
			String msg = Indice.indexando ? "Indexando packs…" : fonte == Fonte.FAVORITOS ? "Nenhum favorito aqui (tecla F no inspetor)."
					: "Nenhum pack encontrado. Coloque packs em " + CFG.packs() + " ou use a aba Modrinth.";
			List<String> ls = Ui.quebrar(msg, w - 20, false, 0.8f, 5);
			float my0 = gy + (gh - ls.size() * 12) / 2;
			for (String l : ls) { Ui.txtC(c, l, cenX + cenW / 2, my0, CINZA, false, 0.8f); my0 += 12; }
			if (!Indice.indexando && fonte != Fonte.FAVORITOS) {
				float bw2 = Ui.largEsp("ABRIR MODRINTH", true, 0.68f, 1.2f) + 16;
				botao(c, cenX + (cenW - bw2) / 2, my0 + 6, bw2, 18, "ABRIR MODRINTH", 1, () -> { vista = Vista.EXPLORAR; abaEsq = 1; });
			}
			maxGrade = 0;
			return;
		}
		int cols = Math.max(1, (int) ((w + 7) / (106 + 7)));
		float cw = (w - 7 * (cols - 1)) / cols, ch = cw * 0.9f + (multi ? 10 : 0);
		recortar(c, cenX + 1, gy, cenW - 2, gh);
		for (int i = 0; i < lista.size(); i++) {
			float yy = gy + (i / cols) * (ch + 7) - scGrade;
			if (yy + ch < gy || yy > gy + gh) continue;
			card(c, lista.get(i), x + (i % cols) * (cw + 7), yy, cw, ch);
		}
		soltar(c);
		int linhas = (lista.size() + cols - 1) / cols;
		maxGrade = linhas * (ch + 7) - 7 - gh;
		scGrade = limitar(scGrade, maxGrade);
		barra(c, inspX - 5, gy, gh, scGrade, maxGrade);
	}

	void card(G c, Indice.Variacao v, float x, float y, float w, float h) {
		if (clip != null && (y + h < clip[1] || y > clip[3])) return; // fora da tela: nem pede prévia
		boolean hv = hov(x, y, w, h), eq = equipado(v), fc = v == foco;
		int borda = fc ? acc() : eq ? accA(0x90) : hv ? 0xFF34405A : BORDA;
		Ui.caixa(c, x, y, w, h, 4, fc ? 0xFF171B33 : CARD, borda, fc ? 1.1f : 0.75f);
		float ix = x + 6, iy = y + 6, iw = w - 12, ih = h - (multi ? 32 : 24);
		Ui.rr(c, ix, iy, iw, ih, 3, ESCURO);
		Ui.gradiente(c, ix + 1, iy + 1, iw - 2, ih - 2, 0xFF161C2E, 0xFF080B13);
		desenharPrevia(c, v, ix, iy + 3, iw, ih - 4, 0);
		float tx = ix + 2, ty = iy + 2, lim = ix + iw - 10;
		if (v.vanilla()) tag(c, "PADRÃO", tx, ty, 0xFF2A3142, TXT, 0, lim);
		else {
			if (v.pack.convertido()) tx = tag(c, v.soTextura ? "CONVERTIDO (SÓ TEXTURA)" : "CONVERTIDO", tx, ty, 0xFF3A2A08, 0xFFFBBF24, 0xFFFBBF24, lim);
			if (v.pack.novo()) tx = tag(c, "NOVO", tx, ty, VERDE, 0xFF052410, 0, lim);
			if (v.pack.modrinth()) tx = tag(c, "MODRINTH", tx, ty, 0xFF0B2A18, VERDE, VERDE, lim);
			else tx = tag(c, "MEU PACK", tx, ty, 0xFF2A3142, TXT, 0, lim);
			if (v.texVanilla) tag(c, "MODELO", tx, ty, 0xFF1E2A44, 0xFF93C5FD, 0, lim);
		}
		if (CFG.favoritos.contains(v.id)) Ui.txt(c, "★", ix + iw - 9, iy + 2.5f, AMARELO, false, 0.8f);
		if (eq && !v.vanilla()) Ui.txt(c, "✔", ix + iw - 9, iy + ih - 10, acc(), false, 0.75f);
		String nome = v.nomePack(), nc = Ui.cortar(nome, w - 10, true, 0.84f);
		Ui.txtC(c, nc, x + w / 2, y + h - (multi ? 25 : 16), 0xFFFFFFFF, true, 0.84f);
		if (multi) Ui.txtC(c, Ui.cortar(v.nomeItem, w - 10, false, 0.62f), x + w / 2, y + h - 13, 0xFFA0A8C0, false, 0.62f);
		if (hv && !nc.equals(nome)) dica = nome + (multi ? " · " + v.nomeItem : "");
		area(x, y, w, h, () -> selecionar(v), () -> { foco = v; modal = Modal.ZOOM; });
	}

	// ======================= Modrinth =======================
	void buscar(String q) {
		estadoBusca = "Buscando…";
		Modrinth.buscar(q).whenComplete((res, err) -> Plataforma.naThread(() -> {
			if (err != null) estadoBusca = "Erro: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
			else {
				resultados = res;
				estadoBusca = res.size() + " resultados";
				scExp = 0;
			}
		}));
	}

	Set<String> baixados() {
		Set<String> s = new HashSet<>();
		for (Config.MetaModrinth m : CFG.modrinth.values()) if (m.id != null) s.add(m.id);
		return s;
	}

	void explorar(G c) {
		if (!buscouMod) {
			buscouMod = true;
			buscar("");
		}
		float x = cenX + 14, y = topo + 10, w = cenW - 28;
		Ui.txt(c, "Explorar Modrinth", x, y, TXT, true, 1.35f);
		Ui.txt(c, "Resource packs 1.20.1 · mais baixados · " + estadoBusca, x, y + 16, CINZA, false, 0.72f);
		float bw = Math.min(150, w * 0.32f);
		campo(c, buscaMod, x + w - bw - 52, y, bw, 18);
		botao(c, x + w - 48, y, 48, 18, "BUSCAR", 1, () -> buscar(buscaMod.txt));
		float cy = y + 32, cx = x;
		for (String s : SUGESTOES) {
			float cw = Ui.larg(s, false, 0.7f) + 14;
			if (cx + cw > x + w) { cx = x; cy += 19; }
			cx += chip(c, s, cx, cy, s.equals(buscaMod.txt), () -> { buscaMod.txt = s; buscar(s); }) + 5;
		}
		float gy = cy + 22, gh = base - 6 - gy;
		int cols = Math.max(1, (int) ((w + 7) / (106 + 7)));
		float cw = (w - 7 * (cols - 1)) / cols, ch = 104;
		Set<String> feitos = baixados();
		recortar(c, cenX + 1, gy, cenW - 2, gh);
		List<Modrinth.Resultado> rs = resultados;
		for (int i = 0; i < rs.size(); i++) {
			float yy = gy + (i / cols) * (ch + 7) - scExp;
			if (yy + ch < gy || yy > gy + gh) continue;
			cardMod(c, rs.get(i), x + (i % cols) * (cw + 7), yy, cw, ch, feitos.contains(rs.get(i).id));
		}
		soltar(c);
		maxExp = ((rs.size() + cols - 1) / cols) * (ch + 7) - 7 - gh;
		scExp = limitar(scExp, maxExp);
		barra(c, inspX - 5, gy, gh, scExp, maxExp);
	}

	void cardMod(G c, Modrinth.Resultado r, float x, float y, float w, float h, boolean baixado) {
		boolean hv = hov(x, y, w, h), fc = r == focoMod;
		Ui.caixa(c, x, y, w, h, 4, fc ? 0xFF171B33 : CARD, fc ? acc() : hv ? 0xFF34405A : BORDA, fc ? 1.1f : 0.75f);
		Ui.rr(c, x + 6, y + 6, w - 12, 50, 3, ESCURO);
		Previews.Tex t = Previews.de("i:" + r.id, () -> Modrinth.icone(r));
		if (t.falhou || t.id == null && (r.icone == null || r.icone.isEmpty())) {
			Ui.rr(c, x + w / 2 - 17, y + 13, 34, 34, 6, accA(0x40));
			Ui.txtC(c, r.titulo.isEmpty() ? "?" : r.titulo.substring(0, 1).toUpperCase(Locale.ROOT), x + w / 2, y + 24, TXT, true, 1.3f);
		} else tex(c, t, "", x + w / 2 - 18, y + 12, 36, 0);
		float tx = x + 9.5f;
		if (baixado) tx = tag(c, "BAIXADO", tx, y + 9.5f, VERDE, 0xFF052410, 0, x + w - 8);
		tag(c, "MODRINTH", tx, y + 9.5f, 0xFF0B2A18, VERDE, VERDE, x + w - 8);
		Ui.txt(c, Ui.cortar(r.titulo, w - 12, true, 0.76f), x + 6, y + 61, TXT, true, 0.76f);
		Ui.txt(c, Ui.cortar("por " + r.autor, w - 12, false, 0.62f), x + 6, y + 72, CINZA, false, 0.62f);
		Ui.txt(c, "⬇ " + numero(r.downloads), x + 6, y + 82, CINZA2, false, 0.62f);
		if (r.progresso >= 0 && r.progresso < 1) {
			Ui.rr(c, x + 6, y + h - 9, w - 12, 3, 1.5f, 0xFF1F2738);
			Ui.rr(c, x + 6, y + h - 9, (w - 12) * Math.max(0.03f, r.progresso), 3, 1.5f, acc());
		} else if (!r.estado.isEmpty()) Ui.txt(c, Ui.cortar(r.estado, w - 12, false, 0.56f), x + 6, y + h - 11, r.estado.startsWith("Erro") ? VERMELHO : VERDE, false, 0.56f);
		area(x, y, w, h, () -> { focoMod = r; if (!baixado) Modrinth.baixar(r); else if (r.previas.isEmpty() && r.progresso < 0) Modrinth.baixar(r); }, () -> focoMod = r);
	}

	static String numero(long n) {
		if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
		if (n >= 1_000) return String.format(Locale.ROOT, "%.1fk", n / 1e3);
		return String.valueOf(n);
	}

	// ======================= créditos =======================
	void creditos(G c) {
		float x = cenX + 14, y = topo + 10, w = cenW - 28;
		Ui.txt(c, "Créditos", x, y, TXT, true, 1.35f);
		Ui.txt(c, "Pack, autor, licença e link de cada textura usada · também em resourcepacks/" + Gerador.NOME + "/creditos.json", x, y + 16, CINZA, false, 0.66f);
		float[] col = {0, 0.27f, 0.52f, 0.68f, 0.84f};
		String[] cab = {"ITEM", "PACK", "AUTOR", "LICENÇA", "LINK"};
		float hy = y + 34;
		for (int i = 0; i < cab.length; i++) Ui.txtEsp(c, cab[i], x + w * col[i], hy, CINZA2, false, 0.58f, 1.4f);
		float ly = hy + 12, lh = base - 6 - ly;
		recortar(c, cenX + 1, ly, cenW - 2, lh);
		float ry = ly - scLista;
		int i = 0;
		for (Map.Entry<String, Config.Selecao> e : new ArrayList<>(CFG.selecao.entrySet())) {
			Indice.Pack p = Indice.pack(e.getValue().pack);
			Config.MetaModrinth m = CFG.modrinth.get(e.getValue().pack);
			Indice.Variacao v = Indice.variacao(e.getValue().pack, e.getKey());
			if (i++ % 2 == 0) Ui.rr(c, x - 4, ry, w + 8, 20, 3, 0xFF111727);
			String[] cel = {v != null ? v.nomeItem : e.getKey(), p != null ? p.nome : e.getValue().pack,
					m != null ? m.autor : "—", m != null ? m.licenca : "—", m != null ? "modrinth ↗" : "pasta ↗"};
			for (int k = 0; k < cel.length; k++) {
				float cwid = w * ((k + 1 < col.length ? col[k + 1] : 1) - col[k]) - 6;
				Ui.txt(c, Ui.cortar(cel[k], cwid, false, 0.68f), x + w * col[k], ry + 6.5f, k == 4 ? acc() : k == 0 ? TXT : CINZA, false, 0.68f);
			}
			String alvo = m != null ? m.link() : p != null ? p.caminho : null;
			if (alvo != null) {
				final boolean web = m != null;
				area(x + w * col[4], ry, w * 0.16f, 20, () -> {
					if (web) abrir(alvo);
					else abrir(new File(alvo).getParentFile());
				}, null);
			}
			ry += 21;
		}
		if (CFG.selecao.isEmpty()) Ui.txt(c, "Nenhuma textura aplicada — tudo Vanilla.", x, ry + 6, CINZA, false, 0.75f);
		soltar(c);
		maxLista = ry + scLista - (ly + lh);
		scLista = limitar(scLista, maxLista);
		barra(c, inspX - 5, ly, lh, scLista, maxLista);
	}

	// ======================= configurações =======================
	void salvarConfig() {
		CFG.pastaPacks = campoPasta.txt.trim();
		boolean urlNova = !campoUrl.txt.trim().equals(CFG.urlAtualizacao);
		CFG.urlAtualizacao = campoUrl.txt.trim();
		if (urlNova) Atualizador.verificar();
		try {
			CFG.limiteCacheMB = Math.max(64, Integer.parseInt(campoLimite.txt.trim()));
		} catch (NumberFormatException ignored) {}
		campoLimite.txt = String.valueOf(CFG.limiteCacheMB);
		CFG.salvar();
		Indice.atualizar(true);
		TexturasMix.IO.submit(() -> Modrinth.limitarCache(Set.of()));
		aviso("Configurações salvas");
	}

	void config(G c) {
		float x = cenX + 14, y = topo + 10, w = cenW - 28;
		Ui.txt(c, "Configurações", x, y, TXT, true, 1.35f);
		Ui.txt(c, "Tudo pesado roda em segundo plano; prévias só existem com a tela aberta.", x, y + 16, CINZA, false, 0.7f);
		float yy = y + 40;
		Ui.txtEsp(c, "PASTA DE PACKS", x, yy, CINZA2, false, 0.6f, 1.4f);
		campo(c, campoPasta, x, yy + 10, w - 60, 18);
		botao(c, x + w - 54, yy + 10, 54, 18, "SALVAR", 1, this::salvarConfig);
		yy += 40;
		Ui.txtEsp(c, "LIMITE DE CACHE (MB)", x, yy, CINZA2, false, 0.6f, 1.4f);
		campo(c, campoLimite, x, yy + 10, 80, 18);
		long uso = Modrinth.usoCache;
		float yu = yy + 40;
		Ui.txtEsp(c, "URL DE ATUALIZAÇÃO (JSON)", x, yu, CINZA2, false, 0.6f, 1.4f);
		campo(c, campoUrl, x, yu + 10, w - 60, 18);
		botao(c, x + w - 54, yu + 10, 54, 18, "SALVAR", 1, this::salvarConfig);
		Ui.txt(c, "Uso: " + (uso < 0 ? "…" : String.format(Locale.ROOT, "%.1f MB", uso / 1048576.0)) + " de " + CFG.limiteCacheMB + " MB (ícones + packs baixados do Modrinth)", x + 88, yy + 15, CINZA, false, 0.68f);
		yy += 40;
		yy += 40;
		Ui.txtEsp(c, "FONTE DO MENU", x, yy, CINZA2, false, 0.6f, 1.4f);
		float fx = x;
		fx += chip(c, "Personalizada", fx, yy + 10, CFG.fonteMenu == 0, () -> { CFG.fonteMenu = 0; CFG.salvar(); }) + 5;
		fx += chip(c, "Padrão do Minecraft", fx, yy + 10, CFG.fonteMenu == 1, () -> { CFG.fonteMenu = 1; CFG.salvar(); }) + 8;
		if (CFG.fonteMenu == 0 && Ui.fontePadrao())
			Ui.txt(c, Ui.glEs() ? "GL ES/Android detectado: usando a padrão" : "TTF falhou: usando a padrão", fx, yy + 14, AMARELO, false, 0.64f);
		yy += 34;
		Ui.txt(c, "Pastas lidas: " + CFG.packs() + " e resourcepacks/", x, yy, CINZA, false, 0.64f);
		yy += 14;
		float bx = x;
		bx += botao(c, bx, yy, 0, 18, "ATUALIZAR PACKS", 0, () -> { Indice.atualizar(true); aviso("Reindexando…"); }) + 6;
		bx += botao(c, bx, yy, 0, 18, "ABRIR PASTA", 0, this::abrirPasta) + 6;
		bx += botao(c, bx, yy, 0, 18, "LIMPAR ÍCONES", 0, () -> { Modrinth.limparIcones(); aviso("Ícones apagados"); }) + 6;
		botao(c, bx, yy, 0, 18, "PASTA DO JOGO", 0, () -> abrir(Plataforma.pastaResourcepacks().toFile()));
		yy += 32;
		int tot = 0, inc = 0;
		for (Indice.Pack p : Indice.packs()) { tot++; if (!p.compativel) inc++; }
		Ui.txt(c, tot + " packs indexados · " + (tot - inc) + " compatíveis (pack_format " + Indice.FORMATO + ") · " + inc + " convertidos", x, yy, TXT, false, 0.72f);
		yy += 14;
		recortar(c, cenX + 1, yy, cenW - 2, base - 6 - yy);
		float ry = yy - scLista;
		List<Indice.Pack> ps = new ArrayList<>(Indice.packs());
		ps.sort(Comparator.comparing(p -> p.nome.toLowerCase(Locale.ROOT)));
		for (Indice.Pack p : ps) {
			Ui.txt(c, Ui.cortar(p.nome, w * 0.55f, false, 0.66f), x, ry, CINZA, false, 0.66f);
			Ui.txt(c, (p.compativel ? "ok" : "↻ convertido (formato " + p.formato + ")") + " · " + p.entradas.size() + " arquivos", x + w * 0.58f, ry, p.compativel ? CINZA2 : 0xFFFBBF24, false, 0.62f);
			ry += 11;
		}
		soltar(c);
		maxLista = ry + scLista - (base - 6);
		scLista = limitar(scLista, maxLista);
	}

	// ======================= inspetor =======================
	void inspetor(G c) {
		float x = inspX + 12, y = topo, w = inspW - 24;
		float ax = x + 2;
		ax += aba(c, "INSPETOR", ax, y + 12, abaDir == 0, () -> abaDir = 0) + 12;
		aba(c, "TEMAS", ax, y + 12, abaDir == 1, () -> abaDir = 1);
		float py = y + 30;
		if (abaDir == 1) { temas(c, x, py, w); return; }
		if (vista == Vista.EXPLORAR) { inspMod(c, x, py, w); return; }
		Indice.Variacao v = foco != null ? foco : lista.isEmpty() ? null : lista.get(0);
		if (v == null) {
			Ui.txt(c, "Selecione uma textura.", x, py + 4, CINZA, false, 0.72f);
			return;
		}
		float ph = Math.min(w * 1.02f, base - py - 158);
		Ui.caixa(c, x, py, w, ph, 4, 0xFF6FA8DC, BORDA);
		Ui.gradiente(c, x + 0.75f, py + 0.75f, w - 1.5f, ph - 1.5f, 0xFF6FA6D8, 0xFF6FAE5A);
		float d = Math.min(w, ph) * 0.64f;
		Ui.rr(c, x + (w - d) / 2, py + (ph - d) / 2, d, d, 2, 0xFF1C1236 & 0xE6FFFFFF);
		desenharPrevia(c, v, x + (w - d) / 2, py + (ph - d) / 2, d, d, (int) (agora() / 100));
		area(x, py, w, ph, () -> modal = Modal.ZOOM, () -> modal = Modal.ZOOM);
		float ty = py + ph + 9;
		Ui.txt(c, Ui.cortar(v.nomePack(), w, true, 0.98f), x, ty, TXT, true, 0.98f);
		ty += 13;
		Config.MetaModrinth m = v.vanilla() ? null : v.pack.meta();
		List<String> l = new ArrayList<>();
		l.add("Item: " + v.nomeItem);
		l.add("Pack: " + (v.vanilla() ? "Minecraft (padrão)" : v.pack.arquivo) + (m != null ? " · por " + m.autor : ""));
		l.add("Fonte: " + (v.vanilla() ? "Vanilla" : m != null ? "Modrinth" : "Meu pack") + " · Atual: " + atualChave(v.chave));
		if (m != null) l.add("Licença: " + m.licenca + " · ⬇ " + numero(m.downloads));
		for (String s : l) { Ui.txt(c, Ui.cortar(s, w, false, 0.64f), x, ty, CINZA, false, 0.64f); ty += 9.5f; }
		if (!v.vanilla() && v.pack.convertido()) { Ui.txt(c, Ui.cortar("↻ Convertido de pack_format " + v.pack.formato + " para 1.20.1" + (v.soTextura ? " (só textura)" : ""), w, false, 0.64f), x, ty, 0xFFFBBF24, false, 0.64f); ty += 9.5f; }
		if (equipado(v)) Ui.txt(c, "✔ Equipado", x, ty, acc(), false, 0.66f);
		float by = base - 12 - 19 * 3 - 12;
		botao(c, x, by, w, 19, equipado(v) ? "EQUIPADO" : "SELECIONAR", 1, () -> selecionar(v));
		by += 25;
		float hw = (w - 6) / 2;
		botao(c, x, by, hw, 19, "ZOOM", 0, () -> modal = Modal.ZOOM);
		boolean fav = CFG.favoritos.contains(v.id);
		botao(c, x + hw + 6, by, hw, 19, fav ? "★ FAVORITO" : "★ FAVORITAR", fav ? 2 : 0, () -> favoritar(v));
		by += 25;
		botao(c, x, by, w, 19, "Texturas avulsas", 0, () -> {
			if (v.vanilla()) aviso("Escolha um pack para ver as texturas avulsas");
			else modal = Modal.AVULSAS;
		});
	}

	void temas(G c, float x, float y, float w) {
		Ui.txt(c, "Cor de destaque", x, y + 2, TXT, true, 0.8f);
		float ry = y + 16;
		for (int i = 0; i < TEMAS.length; i++) {
			final int t = i;
			boolean sel = Math.floorMod(CFG.tema, TEMAS.length) == i, hv = hov(x, ry, w, 20);
			Ui.caixa(c, x, ry, w, 20, 4, sel ? 0xFF171B33 : hv ? 0xFF131A2A : CARD, sel ? TEMAS[i] : BORDA);
			Ui.rr(c, x + 6, ry + 5, 10, 10, 3, TEMAS[i]);
			Ui.txt(c, NOMES_TEMAS[i], x + 22, ry + 6, TXT, false, 0.72f);
			area(x, ry, w, 20, () -> { CFG.tema = t; CFG.salvar(); }, null);
			ry += 24;
		}
	}

	void inspMod(G c, float x, float y, float w) {
		Modrinth.Resultado r = focoMod;
		if (r == null) {
			for (String s : Ui.quebrar("Clique num pack para baixar em segundo plano e indexar. A prévia usa HTTP Range quando o servidor permite.", w, false, 0.68f, 6)) {
				Ui.txt(c, s, x, y + 4, CINZA, false, 0.68f);
				y += 10;
			}
			return;
		}
		Ui.rr(c, x, y, 44, 44, 5, ESCURO);
		Previews.Tex t = Previews.de("i:" + r.id, () -> Modrinth.icone(r));
		tex(c, t, "", x + 4, y + 4, 36, 0);
		float ty = y + 2;
		for (String s : Ui.quebrar(r.titulo, w - 52, true, 0.82f, 2)) { Ui.txt(c, s, x + 50, ty, TXT, true, 0.82f); ty += 10.5f; }
		Ui.txt(c, Ui.cortar("por " + r.autor, w - 52, false, 0.64f), x + 50, Math.max(ty, y + 24), CINZA, false, 0.64f);
		ty = y + 52;
		Ui.txt(c, Ui.cortar("Licença: " + (r.licenca.isEmpty() ? "—" : r.licenca), w, false, 0.64f), x, ty, CINZA, false, 0.64f);
		Ui.txt(c, "Downloads: " + numero(r.downloads), x, ty + 9.5f, CINZA, false, 0.64f);
		ty += 22;
		for (String s : Ui.quebrar(r.descricao, w, false, 0.62f, 4)) { Ui.txt(c, s, x, ty, CINZA2, false, 0.62f); ty += 9; }
		ty += 4;
		if (r.progresso >= 0 && r.progresso < 1) {
			Ui.rr(c, x, ty, w, 4, 2, 0xFF1F2738);
			Ui.rr(c, x, ty, w * Math.max(0.03f, r.progresso), 4, 2, acc());
			ty += 7;
		}
		if (!r.estado.isEmpty()) { Ui.txt(c, Ui.cortar(r.estado + (r.progresso > 0 && r.progresso < 1 ? " " + (int) (r.progresso * 100) + "%" : ""), w, false, 0.62f), x, ty, r.estado.startsWith("Erro") ? VERMELHO : VERDE, false, 0.62f); ty += 11; }
		List<Modrinth.Previa> pv = r.previas;
		if (!pv.isEmpty()) {
			Ui.txtEsp(c, "PRÉVIA", x, ty, CINZA2, false, 0.56f, 1.4f);
			ty += 9;
			int cols = 5;
			float s = (w - 4 * (cols - 1)) / cols;
			float limY = base - 12 - 19 * 2 - 18;
			for (int i = 0; i < pv.size(); i++) {
				float px = x + (i % cols) * (s + 4), py = ty + (i / cols) * (s + 4);
				if (py + s > limY) break;
				Modrinth.Previa p = pv.get(i);
				boolean hv = hov(px, py, s, s);
				Ui.caixa(c, px, py, s, s, 3, ESCURO, hv ? acc() : BORDA);
				tex(c, Previews.de("p:" + r.id + ":" + p.entrada(), p::bytes), p.chave(), px + 3, py + 3, s - 6, 0);
				if (hv) dica = p.chave() == null ? p.entrada() : Indice.nomeItem(p.chave());
			}
		}
		float by = base - 12 - 19 * 2 - 6;
		botao(c, x, by, w, 19, existe(r) ? "VER TEXTURAS" : "BAIXAR E INDEXAR", 1, () -> {
			if (existe(r)) { fonte = Fonte.MODRINTH; vista = Vista.GRADE; abaEsq = 0; } else Modrinth.baixar(r);
		});
		botao(c, x, by + 25, w, 19, "ABRIR NO MODRINTH", 0, () -> abrir("https://modrinth.com/resourcepack/" + (r.slug.isEmpty() ? r.id : r.slug)));
	}

	boolean existe(Modrinth.Resultado r) { Path p = arquivo(r); return p != null && Files.exists(p); }

	Path arquivo(Modrinth.Resultado r) {
		String a = r.arquivo;
		if (a == null) for (Map.Entry<String, Config.MetaModrinth> e : CFG.modrinth.entrySet()) if (r.id.equals(e.getValue().id)) a = e.getKey();
		return a == null ? null : CFG.packs().resolve(a);
	}

	// ======================= rodapé =======================
	void rodape(G c) {
		float y = base + 6, x = PX + 12;
		x += botao(c, x, y, 0, 18, "ENVIAR", 0, this::exportar) + 6;
		x += botao(c, x, y, 0, 18, "PRESETS", 0, () -> { presets = Config.presets(); modal = Modal.PRESETS; }) + 6;
		x += botao(c, x, y, 0, 18, "RESETAR", 0, () -> { Gerador.resetar(); aviso("Voltando ao original…"); }) + 6;
		String k = "ABRIR: " + nomeTecla().toUpperCase(Locale.ROOT);
		float kw = Ui.largEsp(k, true, 0.68f, 1.2f) + 16;
		botao(c, PX + PW - 12 - kw, y, kw, 18, k, 2, null);
		String h = "CLIQUE ESQ. SELECIONAR · CLIQUE DIR. ZOOM · F FAVORITAR · / BUSCAR";
		float hw = Ui.largEsp(h, false, 0.6f, 1.3f), livre = PX + PW - 12 - kw - x;
		if (hw < livre - 10) Ui.txtEsp(c, h, x + (livre - hw) / 2, y + 6, CINZA2, false, 0.6f, 1.3f);
	}

	void exportar() {
		TexturasMix.IO.submit(() -> {
			try {
				Path z = Gerador.exportar();
				Plataforma.naThread(() -> {
					aviso("Exportado: " + z.getFileName());
					abrir(z.getParent().toFile());
				});
			} catch (Exception e) {
				Plataforma.naThread(() -> aviso("Nada para enviar: " + e.getMessage()));
			}
		});
	}

	// ======================= modais =======================
	float[] painel(G c, float w, float h, String titulo) {
		float x = (vw - w) / 2, y = (vh - h) / 2;
		Ui.caixa(c, x, y, w, h, 7, BG, 0xFF2A3348);
		area(x, y, w, h, null, null);
		Ui.txt(c, titulo, x + 14, y + 12, TXT, true, 1.05f);
		botao(c, x + w - 30, y + 9, 18, 16, "✕", 0, () -> modal = Modal.NENHUM);
		return new float[]{x, y};
	}

	void zoom(G c) {
		Indice.Variacao v = foco;
		if (v == null) { modal = Modal.NENHUM; return; }
		float w = Math.min(360, vw - 40), h = Math.min(380, vh - 30);
		float[] p = painel(c, w, h, v.nomePack());
		float x = p[0] + 14, y = p[1] + 32, bw = w - 28, bh = h - 64;
		Ui.caixa(c, x, y, bw, bh, 5, 0xFF6FA8DC, BORDA);
		Ui.gradiente(c, x + 0.75f, y + 0.75f, bw - 1.5f, bh - 1.5f, 0xFF6FA6D8, 0xFF6FAE5A);
		float s = Math.min(bw, bh) * 0.82f;
		tex(c, previa(v), v.chave, x + (bw - s) / 2, y + (bh - s) / 2, s, (int) (agora() / 100));
		Ui.txt(c, Ui.cortar(v.nomeItem + " · " + v.principal.replace(Indice.MC, ""), bw, false, 0.66f), x, y + bh + 8, CINZA, false, 0.66f);
	}

	void salvarPreset() {
		if (CFG.salvarPreset(nomePreset.txt)) {
			aviso("Preset salvo: " + Config.limpar(nomePreset.txt));
			nomePreset.txt = "";
			presets = Config.presets();
		} else aviso("Nome inválido");
	}

	void modalBiblioteca(G c) {
		float w = 340, h = 128;
		float[] p = painel(c, w, h, "Economizar disco?");
		float x = p[0] + 14, y = p[1] + 34;
		for (String l : Ui.quebrar("Extrair só as texturas usadas (itens, blocos, armaduras, teia e .mcmeta) para config/texturasmix/biblioteca/ e APAGAR os .zip originais. Nome, autor e link continuam nos créditos.", w - 28, false, 0.7f, 4)) {
			Ui.txt(c, l, x, y, CINZA, false, 0.7f);
			y += 10;
		}
		float by = p[1] + h - 30;
		float bw = botao(c, x, by, 0, 19, "SIM, EXTRAIR E APAGAR", 1, () -> {
			CFG.biblioteca = 1;
			CFG.salvar();
			modal = Modal.NENHUM;
			Indice.atualizar(false);
			aviso("Extraindo texturas…");
		});
		botao(c, x + bw + 8, by, 0, 19, "NÃO, MANTER .ZIP", 0, () -> { CFG.biblioteca = 2; CFG.salvar(); modal = Modal.NENHUM; });
	}

	void modalPresets(G c) {
		float w = 320, h = Math.min(300, vh - 30);
		float[] p = painel(c, w, h, "Presets");
		float x = p[0] + 14, y = p[1] + 34, iw = w - 28;
		campo(c, nomePreset, x, y, iw - 66, 18);
		botao(c, x + iw - 60, y, 60, 18, "SALVAR", 1, this::salvarPreset);
		float ly = y + 26, lh = p[1] + h - 12 - ly;
		recortar(c, x - 2, ly, iw + 4, lh);
		float ry = ly - scModal;
		if (presets.isEmpty()) Ui.txt(c, "Nenhum preset salvo. Equipe texturas e salve com um nome.", x, ry + 4, CINZA, false, 0.68f);
		for (String n : presets) {
			Ui.caixa(c, x, ry, iw, 22, 4, CARD, BORDA);
			Ui.txt(c, Ui.cortar(n, iw - 110, false, 0.76f), x + 8, ry + 7, TXT, false, 0.76f);
			botao(c, x + iw - 100, ry + 3, 72, 16, "CARREGAR", 2, () -> {
				if (CFG.carregarPreset(n)) { Gerador.agendar(); aviso("Preset carregado: " + n); modal = Modal.NENHUM; chaveLista = ""; }
			});
			botao(c, x + iw - 24, ry + 3, 20, 16, "✕", 0, () -> { Config.excluirPreset(n); presets = Config.presets(); });
			ry += 26;
		}
		soltar(c);
		maxModal = ry + scModal - (ly + lh);
		scModal = limitar(scModal, maxModal);
	}

	void modalAvulsas(G c) {
		Indice.Variacao v = foco;
		if (v == null || v.vanilla()) { modal = Modal.NENHUM; return; }
		float w = 360, h = Math.min(320, vh - 30);
		float[] p = painel(c, w, h, "Texturas avulsas");
		float x = p[0] + 14, y = p[1] + 30, iw = w - 28;
		Ui.txt(c, Ui.cortar("Marque os arquivos de " + v.nomePack() + " que entram no pack gerado", iw, false, 0.64f), x, y, CINZA, false, 0.64f);
		float ly = y + 14, lh = p[1] + h - 12 - ly;
		recortar(c, x - 2, ly, iw + 4, lh);
		float ry = ly - scModal;
		Config.Selecao s = equipado(v) ? CFG.selecao.get(v.chave) : null;
		for (String a : v.arquivos) {
			boolean on = s != null && !s.excluidos.contains(a);
			boolean hv = hov(x, ry, iw, 18);
			Ui.caixa(c, x, ry, iw, 18, 3, hv ? 0xFF161D30 : CARD, BORDA);
			Ui.caixa(c, x + 5, ry + 4.5f, 9, 9, 2, on ? acc() : CAMPO, on ? acc() : 0xFF3A4560);
			if (on) Ui.txt(c, "✔", x + 6.3f, ry + 5.3f, 0xFFFFFFFF, false, 0.6f);
			Ui.txt(c, Ui.cortar(a.replace(Indice.MC, ""), iw - 26, false, 0.66f), x + 20, ry + 5.5f, on ? TXT : CINZA, false, 0.66f);
			area(x, ry, iw, 18, () -> alternarAvulsa(v, a), null);
			ry += 20;
		}
		soltar(c);
		maxModal = ry + scModal - (ly + lh);
		scModal = limitar(scModal, maxModal);
	}

	void alternarAvulsa(Indice.Variacao v, String a) {
		if (!equipado(v)) {
			selecionar(v);
			if (!equipado(v)) return;
		}
		Config.Selecao s = CFG.selecao.get(v.chave);
		if (!s.excluidos.remove(a)) s.excluidos.add(a);
		CFG.salvar();
		Gerador.agendar();
	}

	// ======================= entrada =======================
	@Override
	boolean clique(double x, double y, int b) {
		float px = (float) (x / f), py = (float) (y / f);
		Campo antes = focado;
		focado = null;
		for (int i = areas.size() - 1; i >= 0; i--) {
			Area a = areas.get(i);
			if (modal != Modal.NENHUM && !a.modal()) continue;
			if (!a.dentro(px, py)) continue;
			Runnable r = b == 1 ? a.dir() : b == 0 ? a.esq() : null;
			if (r != null) {
				r.run();
				som();
				if (modal == Modal.NENHUM) scModal = 0;
			}
			return true;
		}
		if (modal != Modal.NENHUM) {
			modal = Modal.NENHUM;
			return true;
		}
		if (antes != null) return true;
		return false;
	}

	@Override
	boolean rolar(double x, double y, double amount) {
		float px = (float) (x / f), d = (float) (-amount * 30);
		if (modal != Modal.NENHUM) scModal = limitar(scModal + d, maxModal);
		else if (px < cenX) scSide = limitar(scSide + d, maxSide);
		else if (px < inspX) {
			switch (vista) {
				case GRADE -> scGrade = limitar(scGrade + d, maxGrade);
				case EXPLORAR -> scExp = limitar(scExp + d, maxExp);
				default -> scLista = limitar(scLista + d, maxLista);
			}
		}
		return true;
	}

	@Override
	boolean tecla(int key, int scan, boolean ctrl, boolean colar) {
		if (focado != null) {
			Campo cp = focado;
			switch (key) {
				case GLFW.GLFW_KEY_ESCAPE -> focado = null;
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!cp.txt.isEmpty()) cp.txt = ctrl ? "" : cp.txt.substring(0, cp.txt.length() - 1);
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
					focado = null;
					if (cp.enter != null) cp.enter.run();
				}
				default -> {
					if (colar) { String cb = clipboard().replaceAll("[\\r\\n]", ""); cp.txt = (cp.txt + cb).substring(0, Math.min(300, cp.txt.length() + cb.length())); }
				}
			}
			return true;
		}
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (modal != Modal.NENHUM) modal = Modal.NENHUM;
			else fechar();
			return true;
		}
		if (ehTeclaAbrir(key, scan) && modal == Modal.NENHUM) {
			fechar();
			return true;
		}
		if (key == GLFW.GLFW_KEY_F && vista == Vista.GRADE && modal == Modal.NENHUM) {
			Indice.Variacao v = foco != null ? foco : lista.isEmpty() ? null : lista.get(0);
			if (v != null) favoritar(v);
			return true;
		}
		return false;
	}

	@Override
	boolean caractere(char ch) {
		if (focado != null) {
			if (ch >= ' ' && ch != 127 && focado.txt.length() < 300) focado.txt += ch;
			return true;
		}
		if (ch == '/' && modal == Modal.NENHUM) {
			if (vista == Vista.EXPLORAR) focado = buscaMod;
			else { vista = Vista.GRADE; focado = busca; }
			return true;
		}
		return false;
	}
}
