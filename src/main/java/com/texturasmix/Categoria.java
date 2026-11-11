package com.texturasmix;

public enum Categoria {
	CRISTAL("Cristal", "item/end_crystal"),
	ANCORA("Âncora", "block/respawn_anchor_top"),
	OBSIDIANA("Obsidiana", "block/obsidian"),
	TOTEM("Totem", "item/totem_of_undying"),
	PEROLA("Pérola", "item/ender_pearl"),
	MACA("Maçã Dourada", "item/golden_apple"),
	ESPADA("Espada", "item/diamond_sword"),
	MACHADO("Machado", "item/diamond_axe"),
	PICARETA("Picareta", "item/diamond_pickaxe"),
	ARMADURA("Armadura", "item/diamond_chestplate"),
	ESCUDO("Escudo", "entity/shield_base_nopattern"),
	ARCO("Arco", "item/bow"),
	POCAO("Poção", "item/potion"),
	BLOCOS("Blocos", "block/grass_block_side"),
	TEIA("Teia", "block/cobweb"),
	OUTROS("Outros", "item/ender_eye");

	public final String nome;
	/** Entrada completa do ícone no pack padrão. */
	public final String icone;

	Categoria(String nome, String tex) {
		this.nome = nome;
		this.icone = "assets/minecraft/textures/" + tex + ".png";
	}

	public static Categoria de(String chave) {
		if (chave.startsWith("armadura:")) return ARMADURA;
		boolean bloco = chave.startsWith("b:");
		String k = bloco ? chave.substring(2) : chave;
		switch (k) {
			case "end_crystal": return CRISTAL;
			case "cobweb": return TEIA;
			case "respawn_anchor": return ANCORA;
			case "obsidian": case "crying_obsidian": return OBSIDIANA;
			case "totem_of_undying": return TOTEM;
			case "ender_pearl": return PEROLA;
			case "golden_apple": case "enchanted_golden_apple": return MACA;
			case "shield": return ESCUDO;
			case "bow": case "crossbow": case "arrow": case "spectral_arrow": case "tipped_arrow": return ARCO;
			case "elytra": case "turtle_helmet": return ARMADURA;
			default: break;
		}
		if (bloco) return BLOCOS;
		if (k.endsWith("_sword")) return ESPADA;
		if (k.endsWith("_pickaxe")) return PICARETA;
		if (k.endsWith("_axe")) return MACHADO;
		if (k.endsWith("_helmet") || k.endsWith("_chestplate") || k.endsWith("_leggings") || k.endsWith("_boots")) return ARMADURA;
		if (k.contains("potion")) return POCAO;
		return OUTROS;
	}
}
