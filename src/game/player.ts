/**
 * Player creation, movement, stamina, and combat actions
 */

import { Direction, Item, Player, Vector2D } from './types';
import { ITEM_CATALOG } from './world';

export function createInitialPlayer(): Player {
  const tileSize = 32;
  const startX = 30 * tileSize + 16;
  const startY = 88 * tileSize + 16;

  const startingWeapon = ITEM_CATALOG.rusty_sword;
  const startingArmor = ITEM_CATALOG.knight_mail;

  const vigor = 10;
  const endurance = 10;
  const strength = 10;
  const arcane = 10;

  const maxHp = 180 + vigor * 12 + (startingArmor.bonusHp || 0);
  const maxStamina = 90 + endurance * 6;
  const maxMana = 60 + arcane * 8;

  return {
    x: startX,
    y: startY,
    vx: 0,
    vy: 0,
    radius: 12,
    facing: 'down',
    angle: Math.PI / 2,
    hp: maxHp,
    maxHp,
    stamina: maxStamina,
    maxStamina,
    mana: maxMana,
    maxMana,
    flasks: 4,
    maxFlasks: 4,
    isRolling: false,
    rollTimer: 0,
    rollDuration: 0.32,
    rollDirection: { x: 0, y: 1 },
    isAttacking: false,
    attackTimer: 0,
    attackCombo: 1,
    attackDuration: 0.28,
    attackHitRegistered: false,
    isBlocking: false,
    isParrying: false,
    parryTimer: 0,
    hurtTimer: 0,
    stats: {
      level: 1,
      vigor,
      endurance,
      strength,
      arcane,
      embers: 100, // starting embers
    },
    equipment: {
      weapon: startingWeapon,
      armor: startingArmor,
      ring: null,
    },
    inventory: [startingWeapon, startingArmor],
    unlockedRunes: {
      marsh: false,
      caldera: false,
      frost: false,
    },
    visitedShrines: ['glade_shrine'],
    activeShrineId: 'glade_shrine',
    defeatedBosses: [],
  };
}

export function recalculatePlayerStats(player: Player) {
  const v = player.stats.vigor;
  const e = player.stats.endurance;
  const a = player.stats.arcane;

  let bonusHp = player.equipment.armor.bonusHp || 0;
  if (player.equipment.ring?.bonusHp) bonusHp += player.equipment.ring.bonusHp;

  let bonusMana = (player.equipment.weapon.bonusMana || 0);
  if (player.equipment.ring?.bonusMana) bonusMana += player.equipment.ring.bonusMana;

  player.maxHp = 180 + v * 12 + bonusHp;
  player.maxStamina = 90 + e * 6;
  player.maxMana = 60 + a * 8 + bonusMana;

  // Clamp current
  if (player.hp > player.maxHp) player.hp = player.maxHp;
  if (player.stamina > player.maxStamina) player.stamina = player.maxStamina;
  if (player.mana > player.maxMana) player.mana = player.maxMana;
}

export function updatePlayerMovement(
  player: Player,
  input: {
    up: boolean;
    down: boolean;
    left: boolean;
    right: boolean;
    aimX: number;
    aimY: number;
  },
  dt: number,
  isSolidTile: (x: number, y: number) => boolean
) {
  // Update aiming angle
  player.angle = Math.atan2(input.aimY - player.y, input.aimX - player.x);

  // Recovery timers
  if (player.hurtTimer > 0) player.hurtTimer -= dt;
  if (player.parryTimer > 0) {
    player.parryTimer -= dt;
    if (player.parryTimer <= 0) player.isParrying = false;
  }

  // Rolling State
  if (player.isRolling) {
    player.rollTimer -= dt;
    const rollSpeed = 260 + (player.equipment.ring?.id === 'ring_wind' ? 50 : 0);
    const nextX = player.x + player.rollDirection.x * rollSpeed * dt;
    const nextY = player.y + player.rollDirection.y * rollSpeed * dt;

    if (!isSolidTile(nextX, player.y)) player.x = nextX;
    if (!isSolidTile(player.x, nextY)) player.y = nextY;

    if (player.rollTimer <= 0) {
      player.isRolling = false;
    }
    return;
  }

  // Attack timer
  if (player.isAttacking) {
    player.attackTimer -= dt;
    // Decelerate movement during swing
    player.vx *= 0.7;
    player.vy *= 0.7;
    player.x += player.vx * dt;
    player.y += player.vy * dt;

    if (player.attackTimer <= 0) {
      player.isAttacking = false;
      player.attackHitRegistered = false;
    }
    return;
  }

  // Calculate direction vector
  let dx = 0;
  let dy = 0;
  if (input.up) dy -= 1;
  if (input.down) dy += 1;
  if (input.left) dx -= 1;
  if (input.right) dx += 1;

  if (dx !== 0 && dy !== 0) {
    const inv = 1 / Math.SQRT2;
    dx *= inv;
    dy *= inv;
  }

  // Update facing
  if (Math.abs(dx) > Math.abs(dy)) {
    player.facing = dx > 0 ? 'right' : 'left';
  } else if (dy !== 0) {
    player.facing = dy > 0 ? 'down' : 'up';
  }

  // Base speed
  let speed = 145;
  if (player.equipment.ring?.id === 'ring_wind') speed += 35;
  if (player.isBlocking) speed *= 0.55; // slower while blocking

  player.vx = dx * speed;
  player.vy = dy * speed;

  // Collision with terrain
  const newX = player.x + player.vx * dt;
  const newY = player.y + player.vy * dt;

  // Check collision circle
  const r = player.radius;
  if (!isSolidTile(newX - r, player.y) && !isSolidTile(newX + r, player.y)) {
    player.x = newX;
  }
  if (!isSolidTile(player.x, newY - r) && !isSolidTile(player.x, newY + r)) {
    player.y = newY;
  }

  // Stamina regeneration (when not rolling, attacking, or blocking)
  if (!player.isRolling && !player.isAttacking && !player.isBlocking) {
    let regenRate = 28 + player.stats.endurance * 2;
    if (player.equipment.armor.staminaRegen) regenRate += player.equipment.armor.staminaRegen;
    if (player.equipment.ring?.staminaRegen) regenRate += player.equipment.ring.staminaRegen;

    player.stamina = Math.min(player.maxStamina, player.stamina + regenRate * dt);
  }
}

export function startPlayerRoll(player: Player): boolean {
  if (player.isRolling || player.stamina < 20) return false;

  player.stamina -= 20;
  player.isRolling = true;
  player.rollTimer = player.rollDuration;

  // Roll in movement direction or facing direction
  let rdx = player.vx;
  let rdy = player.vy;
  if (rdx === 0 && rdy === 0) {
    if (player.facing === 'left') rdx = -1;
    else if (player.facing === 'right') rdx = 1;
    else if (player.facing === 'up') rdy = -1;
    else rdy = 1;
  }

  const len = Math.hypot(rdx, rdy) || 1;
  player.rollDirection = { x: rdx / len, y: rdy / len };
  return true;
}

export function startPlayerAttack(player: Player): boolean {
  if (player.isRolling || player.isAttacking || player.stamina < 15) return false;

  player.stamina -= 15;
  player.isAttacking = true;
  player.attackTimer = player.attackDuration;
  player.attackHitRegistered = false;

  // Step forward slightly in aim direction
  player.vx = Math.cos(player.angle) * 80;
  player.vy = Math.sin(player.angle) * 80;

  // Cycle combo 1 -> 2 -> 3
  player.attackCombo = (player.attackCombo % 3) + 1;
  return true;
}

export function startPlayerBlock(player: Player, block: boolean) {
  if (block && !player.isBlocking) {
    // Just started blocking: activate parry window
    player.isParrying = true;
    // Perfect parry window: default 0.18s, or 0.32s with Aegis of the Sun
    const window = player.equipment.ring?.id === 'aegis_sun' ? 0.32 : 0.18;
    player.parryTimer = window;
  } else if (!block) {
    player.isParrying = false;
    player.parryTimer = 0;
  }
  player.isBlocking = block;
}

export function usePlayerFlask(player: Player): boolean {
  if (player.flasks <= 0 || player.hp >= player.maxHp) return false;

  player.flasks -= 1;
  const healAmount = Math.floor(player.maxHp * 0.65);
  player.hp = Math.min(player.maxHp, player.hp + healAmount);
  return true;
}

export function equipItem(player: Player, item: Item) {
  if (item.type === 'weapon') {
    player.equipment.weapon = item;
  } else if (item.type === 'armor') {
    player.equipment.armor = item;
  } else if (item.type === 'ring') {
    player.equipment.ring = item;
  }
  recalculatePlayerStats(player);
}
