package com.texturasmix;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Conversão de packs de outras versões para 1.20.1 (pack_format 15):
 * caminhos antigos (items/, blocks/), nomes da "flattening" 1.13, armaduras de entity/equipment (1.21.2+),
 * definições items/*.json (1.21.4+) e modelos simples.
 */
final class Conversor {
	static final String MC = Indice.MC;
	private static final Map<String, String> ITENS = new HashMap<>(), BLOCOS = new HashMap<>(), NOVOS = new HashMap<>();
	private static final String[] CORES = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
	private static final String[][] MADEIRAS = {{"oak", "oak"}, {"spruce", "spruce"}, {"birch", "birch"}, {"jungle", "jungle"}, {"acacia", "acacia"}, {"big_oak", "dark_oak"}};

	private static void par(Map<String, String> m, String s) {
		String[] a = s.trim().split("\\s+");
		for (int i = 0; i + 1 < a.length; i += 2) m.put(a[i], a[i + 1]);
	}

	static {
		// ---- itens (textures/items, models/item) 1.12 -> 1.13+ ----
		par(ITENS, """
				apple_golden golden_apple apple_golden_enchanted enchanted_golden_apple beef_cooked cooked_beef beef_raw beef
				book_enchanted enchanted_book book_normal book book_writable writable_book book_written written_book
				bow_standby bow bucket_empty bucket bucket_lava lava_bucket bucket_milk milk_bucket bucket_water water_bucket
				carrot_golden golden_carrot chicken_cooked cooked_chicken chicken_raw chicken chorus_fruit_popped popped_chorus_fruit
				door_acacia acacia_door door_birch birch_door door_dark_oak dark_oak_door door_iron iron_door door_jungle jungle_door
				door_spruce spruce_door door_wood oak_door dye_powder_black ink_sac dye_powder_blue lapis_lazuli dye_powder_brown cocoa_beans
				dye_powder_cyan cyan_dye dye_powder_gray gray_dye dye_powder_green green_dye dye_powder_light_blue light_blue_dye
				dye_powder_lime lime_dye dye_powder_magenta magenta_dye dye_powder_orange orange_dye dye_powder_pink pink_dye
				dye_powder_purple purple_dye dye_powder_red red_dye dye_powder_silver light_gray_dye dye_powder_white bone_meal
				dye_powder_yellow yellow_dye elytra_broken broken_elytra fireball fire_charge fireworks firework_rocket
				fireworks_charge firework_star fireworks_charge_overlay firework_star_overlay fish_clownfish_raw tropical_fish
				fish_cod_cooked cooked_cod fish_cod_raw cod fish_pufferfish_raw pufferfish fish_salmon_cooked cooked_salmon
				fish_salmon_raw salmon fishing_rod_uncast fishing_rod
				gold_axe golden_axe gold_boots golden_boots gold_chestplate golden_chestplate gold_helmet golden_helmet gold_hoe golden_hoe
				gold_horse_armor golden_horse_armor gold_leggings golden_leggings gold_pickaxe golden_pickaxe gold_shovel golden_shovel
				gold_sword golden_sword wood_axe wooden_axe wood_hoe wooden_hoe wood_pickaxe wooden_pickaxe wood_shovel wooden_shovel
				wood_sword wooden_sword map_empty map map_filled filled_map map_filled_markings filled_map_markings
				melon melon_slice melon_speckled glistering_melon_slice minecart_chest chest_minecart minecart_command_block command_block_minecart
				minecart_furnace furnace_minecart minecart_hopper hopper_minecart minecart_normal minecart minecart_tnt tnt_minecart
				mutton_cooked cooked_mutton mutton_raw mutton netherbrick nether_brick porkchop_cooked cooked_porkchop porkchop_raw porkchop
				potato_baked baked_potato potato_poisonous poisonous_potato potion_bottle_drinkable potion potion_bottle_empty glass_bottle
				potion_bottle_lingering lingering_potion potion_bottle_splash splash_potion rabbit_cooked cooked_rabbit rabbit_raw rabbit
				record_11 music_disc_11 record_13 music_disc_13 record_blocks music_disc_blocks record_cat music_disc_cat
				record_chirp music_disc_chirp record_far music_disc_far record_mall music_disc_mall record_mellohi music_disc_mellohi
				record_stal music_disc_stal record_strad music_disc_strad record_wait music_disc_wait record_ward music_disc_ward
				redstone_dust redstone reeds sugar_cane seeds_beetroot beetroot_seeds seeds_melon melon_seeds seeds_pumpkin pumpkin_seeds
				seeds_wheat wheat_seeds sign oak_sign slimeball slime_ball spider_eye_fermented fermented_spider_eye totem totem_of_undying
				wooden_armorstand armor_stand boat oak_boat boat_oak oak_boat boat_spruce spruce_boat boat_birch birch_boat
				boat_jungle jungle_boat boat_acacia acacia_boat boat_darkoak dark_oak_boat
				web cobweb speckled_melon glistering_melon_slice emerald_ore emerald_ore
				golden_apple golden_apple ender_eye ender_eye experience_bottle experience_bottle
				""");
		// ---- blocos (textures/blocks, models/block) 1.12 -> 1.13+ ----
		par(BLOCOS, """
				web cobweb brick bricks stonebrick stone_bricks stonebrick_mossy mossy_stone_bricks stonebrick_cracked cracked_stone_bricks
				stonebrick_carved chiseled_stone_bricks cobblestone_mossy mossy_cobblestone grass_top grass_block_top grass_side grass_block_side
				grass_side_overlay grass_block_side_overlay grass_side_snowed grass_block_snow stone_granite granite stone_granite_smooth polished_granite
				stone_diorite diorite stone_diorite_smooth polished_diorite stone_andesite andesite stone_andesite_smooth polished_andesite
				sandstone_normal sandstone sandstone_carved chiseled_sandstone sandstone_smooth cut_sandstone
				red_sandstone_normal red_sandstone red_sandstone_carved chiseled_red_sandstone red_sandstone_smooth cut_red_sandstone
				ice_packed packed_ice furnace_front_off furnace_front anvil_base anvil anvil_top_damaged_0 anvil_top anvil_top_damaged_1 chipped_anvil_top
				anvil_top_damaged_2 damaged_anvil_top redstone_torch_on redstone_torch redstone_torch_off redstone_torch_off torch_on torch
				pumpkin_face_off carved_pumpkin pumpkin_face_on jack_o_lantern mushroom_block_skin_brown brown_mushroom_block
				mushroom_block_skin_red red_mushroom_block mushroom_block_skin_stem mushroom_stem mushroom_block_inside mushroom_block_inside
				tallgrass grass deadbush dead_bush reeds sugar_cane waterlily lily_pad fire_layer_0 fire_0 fire_layer_1 fire_1
				flower_rose poppy flower_dandelion dandelion flower_blue_orchid blue_orchid flower_allium allium flower_houstonia azure_bluet
				flower_tulip_red red_tulip flower_tulip_orange orange_tulip flower_tulip_white white_tulip flower_tulip_pink pink_tulip
				flower_oxeye_daisy oxeye_daisy noteblock note_block trapdoor oak_trapdoor iron_trapdoor iron_trapdoor rail_normal rail
				rail_normal_turned rail_corner rail_golden powered_rail rail_golden_powered powered_rail_on rail_detector detector_rail
				rail_detector_powered detector_rail_on rail_activator activator_rail rail_activator_powered activator_rail_on
				mob_spawner spawner sponge_wet wet_sponge nether_brick nether_bricks red_nether_brick red_nether_bricks
				prismarine_rough prismarine prismarine_dark dark_prismarine quartz_ore nether_quartz_ore endframe_top end_portal_frame_top
				endframe_side end_portal_frame_side endframe_eye end_portal_frame_eye door_wood_lower oak_door_bottom door_wood_upper oak_door_top
				door_iron_lower iron_door_bottom door_iron_upper iron_door_top trip_wire_source tripwire_hook trip_wire tripwire
				hardened_clay terracotta slime slime_block end_bricks end_stone_bricks glass_pane_top glass_pane_top
				hay_block_side hay_block_side hay_block_top hay_block_top melon_stem_disconnected melon_stem pumpkin_stem_disconnected pumpkin_stem
				comparator_off comparator repeater_off repeater dispenser_front_horizontal dispenser_front dropper_front_horizontal dropper_front
				piston_top_normal piston_top cauldron_top cauldron_top portal nether_portal lava_still lava_still water_still water_still
				cactus_side cactus_side itemframe_background item_frame enchanting_table_top enchanting_table_top crafting_table_front crafting_table_front
				snow snow quartz_block_lines quartz_pillar quartz_block_lines_top quartz_pillar_top quartz_block_chiseled chiseled_quartz_block
				quartz_block_chiseled_top chiseled_quartz_block_top
				""");
		for (String c : CORES) {
			String n = c.equals("silver") ? "light_gray" : c;
			BLOCOS.put("wool_colored_" + c, n + "_wool");
			BLOCOS.put("glass_" + c, n + "_stained_glass");
			BLOCOS.put("glass_pane_top_" + c, n + "_stained_glass_pane_top");
			BLOCOS.put("hardened_clay_stained_" + c, n + "_terracotta");
			BLOCOS.put("concrete_" + c, n + "_concrete");
			BLOCOS.put("concrete_powder_" + c, n + "_concrete_powder");
			BLOCOS.put("glazed_terracotta_" + c, n + "_glazed_terracotta");
			BLOCOS.put("shulker_top_" + c, n + "_shulker_box");
		}
		for (String[] m : MADEIRAS) {
			String a = m[0], b = m[1];
			BLOCOS.put("planks_" + a, b + "_planks");
			BLOCOS.put("log_" + a, b + "_log");
			BLOCOS.put("log_" + a + "_top", b + "_log_top");
			BLOCOS.put("leaves_" + a, b + "_leaves");
			BLOCOS.put("sapling_" + a, b + "_sapling");
			BLOCOS.put("door_" + (a.equals("big_oak") ? "dark_oak" : a) + "_lower", b + "_door_bottom");
			BLOCOS.put("door_" + (a.equals("big_oak") ? "dark_oak" : a) + "_upper", b + "_door_top");
		}
		// ---- nomes de versões mais novas que mudaram depois da 1.20.1 ----
		par(NOVOS, "short_grass grass turtle_scute scute armadillo_scute scute");
	}

	/** Pack de antes da 1.13 (pastas items/ e blocks/). */
	static boolean antigo(Indice.Pack p) { return p.formato >= 1 && p.formato <= 3 || p.formato <= 0 && p.temAntigas; }

	/** tipo: 0 = textura de item, 1 = bloco, 2 = modelo de item (itens de bloco usam a tabela de blocos). */
	static String nomeConv(String n, int tipo, boolean antigo) {
		if (n.contains("/")) return n;
		String r = null;
		if (antigo) {
			r = tipo == 1 ? BLOCOS.get(n) : ITENS.get(n);
			if (r == null && tipo == 2) r = BLOCOS.get(n);
		}
		return r != null ? r : NOVOS.getOrDefault(n, n);
	}

	private static String nome(String dir, String resto, String ext, int tipo, boolean antigo) {
		if (!resto.endsWith(ext)) return dir + resto;
		return dir + nomeConv(resto.substring(0, resto.length() - ext.length()), tipo, antigo) + ext;
	}

	/** Entrada real -> entrada 1.20.1. Retorna a própria entrada quando não muda. */
	static String caminho(String e, boolean antigo) {
		if (!e.startsWith(MC)) return e;
		if (e.endsWith(".mcmeta") && !e.endsWith("pack.mcmeta")) return caminho(e.substring(0, e.length() - 7), antigo) + ".mcmeta";
		String r = e.substring(MC.length());
		String v = r;
		if (r.startsWith("textures/items/")) v = nome("textures/item/", r.substring(15), ".png", 0, true);
		else if (r.startsWith("textures/blocks/")) v = nome("textures/block/", r.substring(16), ".png", 1, true);
		else if (r.startsWith("textures/item/")) v = nome("textures/item/", r.substring(14), ".png", 0, false);
		else if (r.startsWith("textures/block/")) v = nome("textures/block/", r.substring(15), ".png", 1, false);
		else if (r.startsWith("models/item/")) v = nome("models/item/", r.substring(12), ".json", 2, antigo);
		else if (r.startsWith("models/block/")) v = nome("models/block/", r.substring(13), ".json", 1, antigo);
		else if (r.equals("textures/entity/shield/shield_base_nopattern.png") || r.equals("textures/entity/shield/shield_base.png")) v = "textures/entity/" + r.substring(23); // 1.21.4+
		else if (r.startsWith("textures/entity/endercrystal/")) v = "textures/entity/end_crystal/" + r.substring(29).replace("endercrystal", "end_crystal");
		else if (r.startsWith("textures/entity/equipment/humanoid/") || r.startsWith("textures/entity/equipment/humanoid_leggings/")) {
			boolean pernas = r.startsWith("textures/entity/equipment/humanoid_leggings/");
			String n = r.substring(r.lastIndexOf('/') + 1);
			if (!n.endsWith(".png")) return e;
			n = n.substring(0, n.length() - 4);
			boolean overlay = n.endsWith("_overlay");
			if (overlay) n = n.substring(0, n.length() - 8);
			if (n.equals("turtle_scute")) n = "turtle";
			v = "textures/models/armor/" + n + "_layer_" + (pernas ? 2 : 1) + (overlay ? "_overlay" : "") + ".png";
		} else if (r.startsWith("items/") && r.endsWith(".json") && !r.substring(6).contains("/")) v = "models/item/" + r.substring(6);
		return v.equals(r) ? e : MC + v;
	}

	// ---------------- modelos ----------------
	/** Converte o conteúdo de um modelo (ou definição items/*.json). null = não deu (usar só a textura). */
	static byte[] modelo(Indice.Pack p, String real, String virtual, byte[] b) {
		try {
			JsonObject o = JsonParser.parseString(new String(b, StandardCharsets.UTF_8).replace("﻿", "")).getAsJsonObject();
			if (real.startsWith(MC + "items/")) { // 1.21.4+: definição -> modelo que herda do modelo apontado
				String m = modeloDaDefinicao(o.get("model"));
				if (m == null) return null;
				String alvo = m.replace("minecraft:", "");
				if (alvo.equals("item/" + virtual.substring(virtual.lastIndexOf('/') + 1, virtual.length() - 5))) return null; // aponta pro próprio modelo
				JsonObject n = new JsonObject();
				n.addProperty("parent", alvo);
				return Config.GSON.toJson(n).getBytes(StandardCharsets.UTF_8);
			}
			boolean antigo = antigo(p);
			boolean de18 = p.formato == 1 || p.formato <= 0 && p.temAntigas;
			if (de18 && o.has("elements")) return null; // 3D da 1.8: transformações incompatíveis
			if (o.has("parent")) o.addProperty("parent", ref(o.get("parent").getAsString(), antigo, true));
			if (o.has("textures")) {
				JsonObject t = o.getAsJsonObject("textures");
				for (String k : t.keySet()) {
					String s = t.get(k).getAsString();
					if (!s.startsWith("#")) t.addProperty(k, ref(s, antigo, false));
				}
			}
			if (o.has("overrides")) for (JsonElement ov : o.getAsJsonArray("overrides")) {
				JsonObject x = ov.getAsJsonObject();
				if (x.has("model")) x.addProperty("model", ref(x.get("model").getAsString(), antigo, true));
			}
			if (de18) { // 2D da 1.8: display antigo -> usa os modelos base atuais
				o.remove("display");
				String par = o.has("parent") ? o.get("parent").getAsString() : "";
				if (par.equals("builtin/generated") || par.isEmpty()) {
					String n = virtual.substring(virtual.lastIndexOf('/') + 1).replace(".json", "");
					boolean mao = n.matches(".*_(sword|axe|pickaxe|shovel|hoe)") || n.equals("stick") || n.equals("blaze_rod") || n.startsWith("fishing_rod");
					o.addProperty("parent", mao ? "item/handheld" : "item/generated");
				}
			}
			return Config.GSON.toJson(o).getBytes(StandardCharsets.UTF_8);
		} catch (Exception e) {
			return null;
		}
	}

	private static String modeloDaDefinicao(JsonElement el) {
		if (el == null || !el.isJsonObject()) return null;
		JsonObject o = el.getAsJsonObject();
		String tipo = o.has("type") ? o.get("type").getAsString().replace("minecraft:", "") : "";
		if (tipo.equals("model") && o.has("model")) return o.get("model").getAsString();
		if (o.has("fallback")) { String s = modeloDaDefinicao(o.get("fallback")); if (s != null) return s; }
		for (String k : new String[]{"on_false", "on_true", "base"}) if (o.has(k)) { String s = modeloDaDefinicao(o.get(k)); if (s != null) return s; }
		for (String k : new String[]{"cases", "entries", "models"}) {
			if (!o.has(k) || !o.get(k).isJsonArray()) continue;
			for (JsonElement x : o.getAsJsonArray(k)) {
				String s = modeloDaDefinicao(x.isJsonObject() && x.getAsJsonObject().has("model") && x.getAsJsonObject().get("model").isJsonObject() ? x.getAsJsonObject().get("model") : x);
				if (s != null) return s;
			}
		}
		return null;
	}

	/** Referência de modelo/textura ("items/gold_sword") no formato 1.20.1 ("item/golden_sword"). */
	static String ref(String s, boolean antigo, boolean modelo) {
		String ns = "", p = s;
		int i = s.indexOf(':');
		if (i >= 0) { ns = s.substring(0, i + 1); p = s.substring(i + 1); }
		if (!ns.isEmpty() && !ns.equals("minecraft:")) return s;
		if (p.startsWith("items/")) p = "item/" + nomeConv(p.substring(6), modelo ? 2 : 0, true);
		else if (p.startsWith("blocks/")) p = "block/" + nomeConv(p.substring(7), 1, true);
		else if (p.startsWith("item/")) p = "item/" + nomeConv(p.substring(5), modelo ? 2 : 0, antigo);
		else if (p.startsWith("block/")) p = "block/" + nomeConv(p.substring(6), 1, antigo);
		return ns + p;
	}

	static boolean ehModelo(String e) { return e.endsWith(".json") && (e.startsWith(MC + "models/") || e.startsWith(MC + "items/")); }
}
