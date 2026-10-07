/**
 * Open World Generator, Biome Layout, and Item Catalog for Aethelgard
 */

import { BiomeType, BossId, Chest, Destructible, Item, NPC, Shrine, TileData, WorldMap } from './types';

export const ITEM_CATALOG: Record<string, Item> = {
  // Weapons
  rusty_sword: {
    id: 'rusty_sword',
    name: 'Vanguard Broadsword',
    type: 'weapon',
    description: 'Standard forged steel blade of the royal vanguard. Balanced and reliable.',
    icon: '⚔️',
    rarity: 'common',
    attack: 24,
  },
  flame_claymore: {
    id: 'flame_claymore',
    name: 'Flame Claymore',
    type: 'weapon',
    description: 'Forged within the volcanic vents of the Obsidian Caldera. Slashes ignite foes.',
    icon: '🔥',
    rarity: 'rare',
    attack: 42,
    special: 'Ignites targets on combo finisher for burning tick damage.',
  },
  moonlight_greatsword: {
    id: 'moonlight_greatsword',
    name: 'Moonlight Greatsword',
    type: 'weapon',
    description: 'A luminous crystalline blade pulsing with celestial starlight.',
    icon: '✨',
    rarity: 'epic',
    attack: 58,
    bonusMana: 40,
    special: 'Heavy attack unleashes a crescent moon beam.',
  },
  sunfire_rapier: {
    id: 'sunfire_rapier',
    name: 'Sunfire Rapier',
    type: 'weapon',
    description: 'A lightning-quick dueling blade favored by ancient parry champions.',
    icon: '🗡️',
    rarity: 'rare',
    attack: 36,
    staminaRegen: 8,
    special: '+50% Critical Riposte Damage after a parry.',
  },
  titan_cleaver: {
    id: 'titan_cleaver',
    name: "Shattered Titan's Cleaver",
    type: 'weapon',
    description: 'Forged from the primordial core of the fallen Colossus. Devastating kinetic power.',
    icon: '⚡',
    rarity: 'legendary',
    attack: 85,
    special: 'Crushes enemy guard and staggers bosses twice as fast.',
  },

  // Armors
  knight_mail: {
    id: 'knight_mail',
    name: "Knight's Steel Mail",
    type: 'armor',
    description: 'Standard interlocking chainmail and plate cuirass.',
    icon: '🛡️',
    rarity: 'common',
    defense: 12,
    bonusHp: 20,
  },
  shadow_mantle: {
    id: 'shadow_mantle',
    name: 'Shadow Strider Tunic',
    type: 'armor',
    description: 'Lightweight leather imbued with silken dusk threads. Enhances agility.',
    icon: '🥷',
    rarity: 'rare',
    defense: 18,
    staminaRegen: 12,
  },
  titan_cuirass: {
    id: 'titan_cuirass',
    name: "Aegis Plate of the Colossus",
    type: 'armor',
    description: 'Heavy basalt-infused armor forged by ancient giants.',
    icon: '👑',
    rarity: 'legendary',
    defense: 35,
    bonusHp: 120,
  },

  // Rings & Relics
  ring_vitality: {
    id: 'ring_vitality',
    name: 'Ring of Verdant Life',
    type: 'ring',
    description: 'An emerald band infused with the living essence of the Whispering Woods.',
    icon: '💍',
    rarity: 'rare',
    bonusHp: 60,
  },
  ring_wind: {
    id: 'ring_wind',
    name: 'Ring of the Swift Wind',
    type: 'ring',
    description: 'Enchanted with Zephyr magic. Increases movement speed and roll distance.',
    icon: '🌪️',
    rarity: 'rare',
    staminaRegen: 15,
  },
  vampire_crest: {
    id: 'vampire_crest',
    name: 'Bloodstone Signet',
    type: 'ring',
    description: 'Extracted from the Sunken Crypts. Restores health on melee strikes.',
    icon: '🩸',
    rarity: 'epic',
    special: 'Restores 4 HP on every landed hit.',
  },
  aegis_sun: {
    id: 'aegis_sun',
    name: 'Sigil of the Sun Sovereign',
    type: 'ring',
    description: 'Dramatically widens the perfect parry timing window.',
    icon: '☀️',
    rarity: 'legendary',
    special: 'Extends perfect parry window by 75%.',
  },
};

export function createWorldMap(): WorldMap {
  const width = 120; // 120 tiles
  const height = 120; // 120 tiles
  const tileSize = 32; // 32 px per tile (3840 x 3840 world size)

  const tiles: TileData[][] = [];

  // Helper to determine biome based on coordinate
  function getBiome(tx: number, ty: number): BiomeType {
    // Center is Sanctum
    const dx = tx - 60;
    const dy = ty - 60;
    const distToCenter = Math.sqrt(dx * dx + dy * dy);
    if (distToCenter < 14) {
      return 'sanctum';
    }

    if (ty < 55) {
      return tx < 60 ? 'marsh' : 'caldera';
    } else {
      return tx < 60 ? 'verdant' : 'frost';
    }
  }

  // Generate tiles
  for (let y = 0; y < height; y++) {
    const row: TileData[] = [];
    for (let x = 0; x < width; x++) {
      const biome = getBiome(x, y);
      let type = 0; // default grass/ground
      let solid = false;
      const variant = (x * 7 + y * 13) % 4;

      // Outer world boundaries (unbreakable cliffs)
      if (x <= 2 || x >= width - 3 || y <= 2 || y >= height - 3) {
        type = 3; // cliff/wall
        solid = true;
      } else if (biome === 'sanctum') {
        // Ancient Flagstones in Sanctum
        const dist = Math.hypot(x - 60, y - 60);
        if (Math.abs(dist - 13) < 1.2 && !(x > 58 && x < 62)) {
          type = 3; // Sanctum perimeter wall
          solid = true;
        } else {
          type = 7; // Ruin floor
          solid = false;
        }
      } else if (biome === 'marsh') {
        // Swamp pools
        const noise = Math.sin(x * 0.3) + Math.cos(y * 0.3);
        if (noise > 1.2) {
          type = 4; // Swamp deep water
          solid = true;
        } else if (noise > 0.8) {
          type = 4; // shallow swamp
          solid = false;
        } else {
          type = 0; // marsh ground
          solid = false;
        }
      } else if (biome === 'caldera') {
        // Lava streams
        const river = Math.sin(x * 0.25) * 4 + 35;
        if (Math.abs(y - river) < 2) {
          // Check for bridge
          if (x === 85 || x === 105) {
            type = 8; // Bridge
            solid = false;
          } else {
            type = 5; // Lava
            solid = true;
          }
        } else {
          type = 0;
          solid = false;
        }
      } else if (biome === 'frost') {
        // Ice sheets
        const noise = Math.cos(x * 0.2) * Math.sin(y * 0.2);
        if (noise > 0.6) {
          type = 6; // Ice
          solid = false;
        } else {
          type = 0;
          solid = false;
        }
      } else {
        // Verdant
        // Main path from starting glade to shrines
        if ((x >= 28 && x <= 32 && y >= 70 && y <= 95) || (y >= 78 && y <= 80 && x >= 30 && x <= 60)) {
          type = 1; // path
          solid = false;
        } else if ((x >= 15 && x <= 18 && y >= 80 && y <= 100)) {
          // River
          if (y === 88 || y === 89) {
            type = 8; // bridge
            solid = false;
          } else {
            type = 2; // water
            solid = true;
          }
        }
      }

      // Scatter natural cliff obstacles
      if (!solid && type === 0) {
        if ((x % 17 === 0 && y % 13 === 0) || (x % 23 === 0 && y % 19 === 0)) {
          type = 3;
          solid = true;
        }
      }

      row.push({
        type,
        solid,
        variant,
        biome,
      });
    }
    tiles.push(row);
  }

  // Shrines (Bonfires)
  const shrines: Shrine[] = [
    {
      id: 'glade_shrine',
      name: 'Sanctuary Glade',
      x: 30 * tileSize + 16,
      y: 85 * tileSize + 16,
      biome: 'verdant',
      discovered: true, // Starting shrine
      description: 'The ancient bonfire where weary knights kindle their resolve.',
    },
    {
      id: 'marsh_shrine',
      name: 'Rotting Fen Gate',
      x: 35 * tileSize + 16,
      y: 35 * tileSize + 16,
      biome: 'marsh',
      discovered: false,
      description: 'Overlooks the poisonous mist hiding the Blight Colossus.',
    },
    {
      id: 'caldera_shrine',
      name: 'Obsidian Overlook',
      x: 95 * tileSize + 16,
      y: 35 * tileSize + 16,
      biome: 'caldera',
      discovered: false,
      description: 'Heated by subterranean magma vents beneath the Wyrm caldera.',
    },
    {
      id: 'frost_shrine',
      name: 'Glacial Crest',
      x: 95 * tileSize + 16,
      y: 85 * tileSize + 16,
      biome: 'frost',
      discovered: false,
      description: 'Perched upon permafrost cliffs near the Frost Sovereign spire.',
    },
    {
      id: 'sanctum_shrine',
      name: "Titan's Threshold",
      x: 60 * tileSize + 16,
      y: 72 * tileSize + 16,
      biome: 'sanctum',
      discovered: false,
      description: 'The grand gates guarding the Shattered Titan King.',
    },
  ];

  // Boss Arenas
  const bossArenas = [
    {
      id: 'blight_colossus' as BossId,
      x: 25 * tileSize + 16,
      y: 20 * tileSize + 16,
      radius: 12 * tileSize,
    },
    {
      id: 'ashfang_wyrm' as BossId,
      x: 100 * tileSize + 16,
      y: 20 * tileSize + 16,
      radius: 12 * tileSize,
    },
    {
      id: 'frost_vael' as BossId,
      x: 100 * tileSize + 16,
      y: 100 * tileSize + 16,
      radius: 12 * tileSize,
    },
    {
      id: 'malakor_titan' as BossId,
      x: 60 * tileSize + 16,
      y: 58 * tileSize + 16,
      radius: 13 * tileSize,
    },
  ];

  // Chests
  const chests: Chest[] = [
    {
      id: 'chest_glade_intro',
      x: 35 * tileSize,
      y: 82 * tileSize,
      opened: false,
      item: ITEM_CATALOG.sunfire_rapier,
      embers: 50,
    },
    {
      id: 'chest_glade_relic',
      x: 48 * tileSize,
      y: 92 * tileSize,
      opened: false,
      item: ITEM_CATALOG.ring_vitality,
      embers: 100,
    },
    {
      id: 'chest_marsh_vampire',
      x: 18 * tileSize,
      y: 40 * tileSize,
      opened: false,
      item: ITEM_CATALOG.vampire_crest,
      embers: 200,
    },
    {
      id: 'chest_caldera_flame',
      x: 85 * tileSize,
      y: 22 * tileSize,
      opened: false,
      item: ITEM_CATALOG.flame_claymore,
      embers: 250,
    },
    {
      id: 'chest_frost_moonlight',
      x: 82 * tileSize,
      y: 98 * tileSize,
      opened: false,
      item: ITEM_CATALOG.moonlight_greatsword,
      embers: 300,
    },
    {
      id: 'chest_sanctum_shadow',
      x: 60 * tileSize,
      y: 65 * tileSize,
      opened: false,
      item: ITEM_CATALOG.shadow_mantle,
      embers: 400,
    },
  ];

  // Destructible pots & crystals
  const destructibles: Destructible[] = [];
  const spots = [
    { x: 33, y: 83, type: 'pot' },
    { x: 33, y: 84, type: 'pot' },
    { x: 37, y: 81, type: 'barrel' },
    { x: 42, y: 78, type: 'pot' },
    { x: 22, y: 38, type: 'crystal' },
    { x: 28, y: 28, type: 'pot' },
    { x: 92, y: 38, type: 'crystal' },
    { x: 88, y: 88, type: 'crystal' },
    { x: 92, y: 92, type: 'crystal' },
  ];
  spots.forEach((s, idx) => {
    destructibles.push({
      id: `destructible_${idx}`,
      x: s.x * tileSize + 16,
      y: s.y * tileSize + 16,
      type: s.type as 'pot' | 'barrel' | 'crystal',
      hp: 1,
      destroyed: false,
    });
  });

  // NPCs
  const npcs: NPC[] = [
    {
      id: 'elder_alistair',
      name: 'Elder Alistair',
      title: 'Keeper of the Whispering Glade',
      x: 32 * tileSize + 16,
      y: 86 * tileSize + 16,
      dialogue: [
        'Greetings, Wanderer. The ancient Titans have awakened from their millennia of slumber.',
        'To challenge the Shattered King Malakor in the Sanctum at the heart of Aethelgard, you must first gather the three Titan Runes.',
        'The Blight Colossus festers in the Sunken Marsh to the northwest.',
        'Ashfang the Molten Wyrm reigns over the Obsidian Caldera to the northeast.',
        'Cryomancer Vael casts frozen blizzards across the Glacial Peak to the southeast.',
        'Tip: Time your block right before an attack hits to trigger a Perfect Parry. It staggers even the mightiest Titans!',
      ],
    },
    {
      id: 'wandering_smith',
      name: 'Vorn Ironhand',
      title: 'Master Bladesmith',
      x: 27 * tileSize + 16,
      y: 82 * tileSize + 16,
      dialogue: [
        'Hmph. That broadsword has seen better days, warrior.',
        'Gather Titan Embers from defeated beasts and rest at the Whisperstone Shrines to temper your stats—Vigor, Endurance, Strength, and Arcane.',
        'Search the hidden ruins in each biome; legendary arms are sealed away in ancient chests.',
        'Stay light on your feet. A well-timed roll gives you invulnerability through any blow!',
      ],
    },
  ];

  return {
    width,
    height,
    tileSize,
    tiles,
    shrines,
    chests,
    destructibles,
    npcs,
    bossArenas,
  };
}
