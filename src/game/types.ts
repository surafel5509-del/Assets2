/**
 * Game data structures & type definitions for Aethelgard
 */

export type Direction = 'up' | 'down' | 'left' | 'right';

export type BiomeType = 'verdant' | 'marsh' | 'caldera' | 'frost' | 'sanctum';

export interface Vector2D {
  x: number;
  y: number;
}

export interface PlayerStats {
  level: number;
  vigor: number;      // Max HP
  endurance: number;  // Max Stamina & recovery
  strength: number;   // Physical attack
  arcane: number;     // Spell power & max mana
  embers: number;     // XP / Currency
}

export interface Equipment {
  weapon: Item;
  armor: Item;
  ring: Item | null;
}

export type ItemType = 'weapon' | 'armor' | 'ring' | 'consumable' | 'key_item';

export interface Item {
  id: string;
  name: string;
  type: ItemType;
  description: string;
  icon: string;
  rarity: 'common' | 'rare' | 'epic' | 'legendary';
  attack?: number;
  defense?: number;
  staminaRegen?: number;
  bonusHp?: number;
  bonusMana?: number;
  special?: string;
}

export interface Player {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  facing: Direction;
  angle: number; // For aiming
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  mana: number;
  maxMana: number;
  flasks: number;
  maxFlasks: number;
  isRolling: boolean;
  rollTimer: number;
  rollDuration: number;
  rollDirection: Vector2D;
  isAttacking: boolean;
  attackTimer: number;
  attackCombo: number; // 1, 2, 3
  attackDuration: number;
  attackHitRegistered: boolean;
  isBlocking: boolean;
  isParrying: boolean;
  parryTimer: number;
  hurtTimer: number;
  stats: PlayerStats;
  equipment: Equipment;
  inventory: Item[];
  unlockedRunes: {
    marsh: boolean;
    caldera: boolean;
    frost: boolean;
  };
  visitedShrines: string[];
  activeShrineId: string | null;
  defeatedBosses: string[];
}

export type EnemyType = 
  | 'slime'
  | 'acid_slime'
  | 'skeleton'
  | 'fire_imp'
  | 'frost_wraith'
  | 'magma_brute';

export interface Enemy {
  id: string;
  type: EnemyType;
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  hp: number;
  maxHp: number;
  speed: number;
  attackDamage: number;
  attackRange: number;
  aggroRange: number;
  attackCooldown: number;
  currentCooldown: number;
  isWindup: boolean;
  windupTimer: number;
  isStaggered: boolean;
  staggerTimer: number;
  hurtTimer: number;
  facing: Direction;
  biome: BiomeType;
  embersReward: number;
  spawnX: number;
  spawnY: number;
}

export type BossId = 
  | 'blight_colossus'
  | 'ashfang_wyrm'
  | 'frost_vael'
  | 'malakor_titan';

export interface BossAttack {
  name: string;
  type: 'slam' | 'projectile_burst' | 'breath' | 'dash' | 'nova' | 'summon' | 'beam';
  cooldown: number;
  windup: number;
}

export interface Boss {
  id: BossId;
  name: string;
  title: string;
  biome: BiomeType;
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  hp: number;
  maxHp: number;
  phase: number;
  maxPhases: number;
  state: 'idle' | 'approaching' | 'windup' | 'attacking' | 'recovering' | 'staggered' | 'defeated';
  currentAttack: BossAttack | null;
  actionTimer: number;
  windupDuration: number;
  attackDuration: number;
  recoveryDuration: number;
  staggerTimer: number;
  facing: Direction;
  arenaCenter: Vector2D;
  arenaRadius: number;
  embersReward: number;
  itemReward: Item;
  runeReward?: 'marsh' | 'caldera' | 'frost';
  enraged: boolean;
  dialogue?: string[];
}

export interface Projectile {
  id: string;
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  damage: number;
  source: 'player' | 'enemy' | 'boss';
  color: string;
  lifetime: number;
  piercing?: boolean;
  effect?: 'fire' | 'frost' | 'poison' | 'shock';
}

export interface DangerZone {
  id: string;
  x: number;
  y: number;
  radius: number;
  duration: number; // time until explosion
  maxDuration: number;
  damage: number;
  color: string;
  type: 'circle' | 'cone' | 'line';
  angle?: number;
  spreadAngle?: number;
  length?: number;
  effect?: 'fire' | 'frost' | 'acid';
}

export interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  color: string;
  alpha: number;
  decay: number;
  glow?: boolean;
}

export interface FloatingText {
  id: string;
  x: number;
  y: number;
  text: string;
  color: string;
  alpha: number;
  scale: number;
  life: number;
}

export interface Shrine {
  id: string;
  name: string;
  x: number;
  y: number;
  biome: BiomeType;
  discovered: boolean;
  description: string;
}

export interface Chest {
  id: string;
  x: number;
  y: number;
  opened: boolean;
  item: Item;
  embers: number;
}

export interface Destructible {
  id: string;
  x: number;
  y: number;
  type: 'pot' | 'barrel' | 'crystal';
  hp: number;
  destroyed: boolean;
}

export interface NPC {
  id: string;
  name: string;
  title: string;
  x: number;
  y: number;
  dialogue: string[];
  hasGivenReward?: boolean;
  reward?: Item;
}

export interface TileData {
  type: number; // 0: grass, 1: path, 2: water, 3: wall/cliff, 4: swamp, 5: lava, 6: ice, 7: ruin_floor, 8: bridge
  solid: boolean;
  variant: number;
  biome: BiomeType;
}

export interface WorldMap {
  width: number;  // in tiles
  height: number; // in tiles
  tileSize: number;
  tiles: TileData[][];
  shrines: Shrine[];
  chests: Chest[];
  destructibles: Destructible[];
  npcs: NPC[];
  bossArenas: {
    id: BossId;
    x: number;
    y: number;
    radius: number;
  }[];
}

export interface Spell {
  id: string;
  name: string;
  cost: number; // mana
  cooldown: number;
  currentCooldown: number;
  icon: string;
  description: string;
}

export type GameView = 
  | 'playing'
  | 'world_map'
  | 'inventory'
  | 'shrine'
  | 'game_over'
  | 'victory'
  | 'lore';
