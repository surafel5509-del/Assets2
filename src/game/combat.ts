/**
 * Combat resolution, enemy AI loop, hitboxes, parry calculations, and damage formulas
 */

import { soundEngine } from './audio';
import { Boss, DangerZone, Enemy, EnemyType, FloatingText, Particle, Player, Projectile, Vector2D } from './types';

export function createEnemy(type: EnemyType, x: number, y: number): Enemy {
  let hp = 60;
  let speed = 65;
  let attackDamage = 16;
  let attackRange = 36;
  let aggroRange = 180;
  let radius = 13;
  let embers = 20;

  if (type === 'acid_slime') {
    hp = 85;
    speed = 75;
    attackDamage = 22;
    embers = 35;
  } else if (type === 'skeleton') {
    hp = 110;
    speed = 60;
    attackDamage = 26;
    attackRange = 42;
    embers = 45;
  } else if (type === 'fire_imp') {
    hp = 70;
    speed = 95;
    attackDamage = 28;
    attackRange = 90; // ranged fireball
    embers = 50;
  } else if (type === 'frost_wraith') {
    hp = 95;
    speed = 70;
    attackDamage = 30;
    embers = 60;
  } else if (type === 'magma_brute') {
    hp = 220;
    speed = 45;
    attackDamage = 45;
    attackRange = 48;
    radius = 18;
    embers = 100;
  }

  return {
    id: `enemy_${Math.random().toString(36).substring(2, 9)}`,
    type,
    x,
    y,
    vx: 0,
    vy: 0,
    radius,
    hp,
    maxHp: hp,
    speed,
    attackDamage,
    attackRange,
    aggroRange,
    attackCooldown: 1.8,
    currentCooldown: Math.random() * 1.5,
    isWindup: false,
    windupTimer: 0,
    isStaggered: false,
    staggerTimer: 0,
    hurtTimer: 0,
    facing: 'down',
    biome: 'verdant',
    embersReward: embers,
    spawnX: x,
    spawnY: y,
  };
}

export interface CombatUpdateResult {
  floatingTexts: FloatingText[];
  particles: Particle[];
  newProjectiles: Projectile[];
  screenShake: number;
}

export function updateEnemiesAndCombat(
  enemies: Enemy[],
  bosses: Boss[],
  player: Player,
  projectiles: Projectile[],
  dangerZones: DangerZone[],
  dt: number,
  isSolidTile: (x: number, y: number) => boolean
): CombatUpdateResult {
  const result: CombatUpdateResult = {
    floatingTexts: [],
    particles: [],
    newProjectiles: [],
    screenShake: 0,
  };

  // 1. UPDATE REGULAR ENEMIES
  for (let i = enemies.length - 1; i >= 0; i--) {
    const enemy = enemies[i];

    if (enemy.hp <= 0) {
      // Spawn death blood / embers
      player.stats.embers += enemy.embersReward;
      result.floatingTexts.push({
        id: `emb_${Date.now()}_${i}`,
        x: enemy.x,
        y: enemy.y - 10,
        text: `+${enemy.embersReward} Embers`,
        color: '#fbbf24',
        alpha: 1,
        scale: 1.1,
        life: 1.2,
      });

      for (let p = 0; p < 8; p++) {
        result.particles.push({
          x: enemy.x,
          y: enemy.y,
          vx: (Math.random() - 0.5) * 120,
          vy: (Math.random() - 0.5) * 120,
          radius: 3,
          color: '#fbbf24',
          alpha: 1,
          decay: 1.5,
        });
      }

      enemies.splice(i, 1);
      continue;
    }

    // Cooldown & Timers
    if (enemy.hurtTimer > 0) enemy.hurtTimer -= dt;
    if (enemy.currentCooldown > 0) enemy.currentCooldown -= dt;

    if (enemy.isStaggered) {
      enemy.staggerTimer -= dt;
      if (enemy.staggerTimer <= 0) {
        enemy.isStaggered = false;
      }
      continue;
    }

    const dx = player.x - enemy.x;
    const dy = player.y - enemy.y;
    const dist = Math.hypot(dx, dy);

    // Update facing
    enemy.facing = Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right' : 'left') : (dy > 0 ? 'down' : 'up');

    // Aggro Check
    if (dist < enemy.aggroRange) {
      if (enemy.isWindup) {
        enemy.windupTimer -= dt;
        if (enemy.windupTimer <= 0) {
          enemy.isWindup = false;
          // Attack lands!
          if (enemy.type === 'fire_imp') {
            // Shoots fireball
            const angle = Math.atan2(dy, dx);
            result.newProjectiles.push({
              id: `proj_imp_${Date.now()}_${i}`,
              x: enemy.x,
              y: enemy.y,
              vx: Math.cos(angle) * 160,
              vy: Math.sin(angle) * 160,
              radius: 6,
              damage: enemy.attackDamage,
              source: 'enemy',
              color: '#ea580c',
              lifetime: 2.5,
              effect: 'fire',
            });
          } else {
            // Melee strike hit check
            if (dist < enemy.attackRange + player.radius) {
              resolveEnemyHitOnPlayer(enemy, player, enemy.attackDamage, result);
            }
          }
          enemy.currentCooldown = enemy.attackCooldown;
        }
      } else if (dist <= enemy.attackRange && enemy.currentCooldown <= 0) {
        // Start windup
        enemy.isWindup = true;
        enemy.windupTimer = 0.45; // 450ms windup telegraph
      } else if (!enemy.isWindup) {
        // Approach player
        const moveX = (dx / dist) * enemy.speed * dt;
        const moveY = (dy / dist) * enemy.speed * dt;

        if (!isSolidTile(enemy.x + moveX, enemy.y)) enemy.x += moveX;
        if (!isSolidTile(enemy.x, enemy.y + moveY)) enemy.y += moveY;
      }
    }
  }

  // 2. CHECK PLAYER MELEE ATTACK ON ENEMIES & BOSSES
  if (player.isAttacking && !player.attackHitRegistered) {
    const weapon = player.equipment.weapon;
    const baseDamage = weapon.attack || 20;
    const strBonus = player.stats.strength * 2.5;
    const comboMult = player.attackCombo === 3 ? 1.75 : player.attackCombo === 2 ? 1.25 : 1.0;
    const finalDamage = Math.floor((baseDamage + strBonus) * comboMult);

    const hitArcRadius = player.attackCombo === 3 ? 55 : 42;
    const attackAngle = player.angle;

    let hitAny = false;

    // Check enemies
    for (const enemy of enemies) {
      const edx = enemy.x - player.x;
      const edy = enemy.y - player.y;
      const edist = Math.hypot(edx, edy);

      if (edist <= hitArcRadius + enemy.radius) {
        const angleToEnemy = Math.atan2(edy, edx);
        let angleDiff = Math.abs(angleToEnemy - attackAngle);
        if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;

        // 130 degree swing arc
        if (angleDiff <= 1.2) {
          hitAny = true;
          let dmg = finalDamage;
          // Bonus if staggered
          if (enemy.isStaggered) dmg = Math.floor(dmg * 1.8);

          enemy.hp -= dmg;
          enemy.hurtTimer = 0.2;
          enemy.isStaggered = true;
          enemy.staggerTimer = 0.35; // knockback / flinch

          // Knockback
          enemy.x += Math.cos(attackAngle) * 20;
          enemy.y += Math.sin(attackAngle) * 20;

          // Blood / hit particles
          for (let p = 0; p < 5; p++) {
            result.particles.push({
              x: enemy.x,
              y: enemy.y,
              vx: Math.cos(attackAngle + (Math.random() - 0.5)) * 110,
              vy: Math.sin(attackAngle + (Math.random() - 0.5)) * 110,
              radius: 2.5,
              color: enemy.isStaggered ? '#facc15' : '#ef4444',
              alpha: 1,
              decay: 2.0,
            });
          }

          // Damage text
          result.floatingTexts.push({
            id: `dmg_${Date.now()}_${Math.random()}`,
            x: enemy.x,
            y: enemy.y - 12,
            text: enemy.isStaggered ? `CRIT ${dmg}` : `${dmg}`,
            color: enemy.isStaggered ? '#fbbf24' : '#ffffff',
            alpha: 1,
            scale: enemy.isStaggered ? 1.3 : 1.0,
            life: 0.9,
          });

          // Vampire Crest healing
          if (player.equipment.ring?.id === 'vampire_crest') {
            player.hp = Math.min(player.maxHp, player.hp + 4);
          }
        }
      }
    }

    // Check bosses
    for (const boss of bosses) {
      if (boss.state === 'defeated' || boss.hp <= 0) continue;

      const bdx = boss.x - player.x;
      const bdy = boss.y - player.y;
      const bdist = Math.hypot(bdx, bdy);

      if (bdist <= hitArcRadius + boss.radius) {
        const angleToBoss = Math.atan2(bdy, bdx);
        let angleDiff = Math.abs(angleToBoss - attackAngle);
        if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;

        if (angleDiff <= 1.3) {
          hitAny = true;
          let dmg = finalDamage;
          // Staggered boss takes critical riposte
          if (boss.state === 'staggered') {
            dmg = Math.floor(dmg * 2.2);
            result.screenShake = 12;
          }

          boss.hp -= dmg;
          result.floatingTexts.push({
            id: `boss_dmg_${Date.now()}_${Math.random()}`,
            x: boss.x + (Math.random() - 0.5) * 30,
            y: boss.y - 25,
            text: boss.state === 'staggered' ? `RIPOSTE ${dmg}!` : `${dmg}`,
            color: boss.state === 'staggered' ? '#fbbf24' : '#f87171',
            alpha: 1,
            scale: boss.state === 'staggered' ? 1.5 : 1.1,
            life: 1.1,
          });

          for (let p = 0; p < 8; p++) {
            result.particles.push({
              x: boss.x,
              y: boss.y,
              vx: (Math.random() - 0.5) * 160,
              vy: (Math.random() - 0.5) * 160,
              radius: 3,
              color: '#fbbf24',
              alpha: 1,
              decay: 1.8,
            });
          }

          // Vampire Crest on boss
          if (player.equipment.ring?.id === 'vampire_crest') {
            player.hp = Math.min(player.maxHp, player.hp + 4);
          }
        }
      }
    }

    if (hitAny) {
      player.attackHitRegistered = true;
      soundEngine.playHit();
      result.screenShake = Math.max(result.screenShake, player.attackCombo === 3 ? 8 : 4);
    }
  }

  // 3. UPDATE PROJECTILES
  for (let i = projectiles.length - 1; i >= 0; i--) {
    const p = projectiles[i];
    p.lifetime -= dt;
    p.x += p.vx * dt;
    p.y += p.vy * dt;

    // Check lifetime or solid wall
    if (p.lifetime <= 0 || isSolidTile(p.x, p.y)) {
      projectiles.splice(i, 1);
      continue;
    }

    // Check hit player
    if (p.source !== 'player') {
      const pdist = Math.hypot(player.x - p.x, player.y - p.y);
      if (pdist < player.radius + p.radius) {
        resolveEnemyHitOnPlayer(null, player, p.damage, result);
        projectiles.splice(i, 1);
        continue;
      }
    } else {
      // Player projectile hitting boss or enemies
      let hit = false;
      for (const enemy of enemies) {
        if (Math.hypot(enemy.x - p.x, enemy.y - p.y) < enemy.radius + p.radius) {
          enemy.hp -= p.damage;
          enemy.hurtTimer = 0.2;
          result.floatingTexts.push({
            id: `p_dmg_${Date.now()}_${Math.random()}`,
            x: enemy.x,
            y: enemy.y - 10,
            text: `${p.damage}`,
            color: '#38bdf8',
            alpha: 1,
            scale: 1.1,
            life: 0.9,
          });
          hit = true;
          break;
        }
      }
      if (!hit) {
        for (const boss of bosses) {
          if (boss.hp > 0 && Math.hypot(boss.x - p.x, boss.y - p.y) < boss.radius + p.radius) {
            boss.hp -= p.damage;
            result.floatingTexts.push({
              id: `p_boss_dmg_${Date.now()}_${Math.random()}`,
              x: boss.x,
              y: boss.y - 20,
              text: `${p.damage}`,
              color: '#38bdf8',
              alpha: 1,
              scale: 1.2,
              life: 1.0,
            });
            hit = true;
            break;
          }
        }
      }
      if (hit) {
        projectiles.splice(i, 1);
        continue;
      }
    }
  }

  // 4. UPDATE DANGER ZONES (AOE / Boss Telegraphs)
  for (let i = dangerZones.length - 1; i >= 0; i--) {
    const zone = dangerZones[i];
    zone.duration -= dt;

    if (zone.duration <= 0) {
      // Detonate!
      // Check if player is inside
      const pdist = Math.hypot(player.x - zone.x, player.y - zone.y);
      let isInside = false;

      if (zone.type === 'circle') {
        isInside = pdist <= zone.radius;
      } else if (zone.type === 'cone' && zone.angle !== undefined && zone.spreadAngle !== undefined) {
        if (pdist <= zone.radius) {
          const angleToP = Math.atan2(player.y - zone.y, player.x - zone.x);
          let diff = Math.abs(angleToP - zone.angle);
          if (diff > Math.PI) diff = 2 * Math.PI - diff;
          isInside = diff <= zone.spreadAngle;
        }
      }

      if (isInside) {
        resolveEnemyHitOnPlayer(null, player, zone.damage, result);
      }

      // Explosion particle shockwave
      for (let p = 0; p < 12; p++) {
        const ang = (p * Math.PI * 2) / 12;
        result.particles.push({
          x: zone.x,
          y: zone.y,
          vx: Math.cos(ang) * 140,
          vy: Math.sin(ang) * 140,
          radius: 3.5,
          color: zone.effect === 'acid' ? '#84cc16' : zone.effect === 'frost' ? '#38bdf8' : '#ea580c',
          alpha: 1,
          decay: 1.6,
        });
      }

      dangerZones.splice(i, 1);
    }
  }

  return result;
}

function resolveEnemyHitOnPlayer(
  attacker: Enemy | null,
  player: Player,
  rawDamage: number,
  result: CombatUpdateResult
) {
  // If player is rolling -> Invulnerability frames!
  if (player.isRolling) {
    result.floatingTexts.push({
      id: `dodge_${Date.now()}`,
      x: player.x,
      y: player.y - 15,
      text: 'DODGE',
      color: '#38bdf8',
      alpha: 1,
      scale: 1.0,
      life: 0.8,
    });
    soundEngine.playRoll();
    return;
  }

  // If player is in PERFECT PARRY window!
  if (player.isParrying) {
    soundEngine.playParry();
    result.screenShake = 15;
    result.floatingTexts.push({
      id: `parry_${Date.now()}`,
      x: player.x,
      y: player.y - 20,
      text: 'PERFECT PARRY!',
      color: '#facc15',
      alpha: 1,
      scale: 1.5,
      life: 1.4,
    });

    // Stagger attacker
    if (attacker) {
      attacker.isStaggered = true;
      attacker.staggerTimer = 2.0; // 2 seconds staggered!
      attacker.x -= Math.cos(Math.atan2(player.y - attacker.y, player.x - attacker.x)) * 40;
      attacker.y -= Math.sin(Math.atan2(player.y - attacker.y, player.x - attacker.x)) * 40;
    }

    // Golden burst shockwave
    for (let p = 0; p < 16; p++) {
      const ang = (p * Math.PI * 2) / 16;
      result.particles.push({
        x: player.x,
        y: player.y,
        vx: Math.cos(ang) * 170,
        vy: Math.sin(ang) * 170,
        radius: 4,
        color: '#fbbf24',
        alpha: 1,
        decay: 1.8,
      });
    }
    return;
  }

  // If player is blocking
  if (player.isBlocking) {
    const staminaCost = 25;
    if (player.stamina >= staminaCost) {
      player.stamina -= staminaCost;
      const blockedDamage = Math.max(1, Math.floor(rawDamage * 0.2)); // 80% damage reduction
      player.hp -= blockedDamage;
      soundEngine.playBlock();
      result.floatingTexts.push({
        id: `block_${Date.now()}`,
        x: player.x,
        y: player.y - 15,
        text: `BLOCKED -${blockedDamage}`,
        color: '#93c5fd',
        alpha: 1,
        scale: 1.0,
        life: 0.8,
      });
      return;
    }
  }

  // Normal Hit Taken
  const armorDefense = player.equipment.armor.defense || 0;
  const netDamage = Math.max(5, Math.floor(rawDamage - armorDefense * 0.5));
  player.hp = Math.max(0, player.hp - netDamage);
  player.hurtTimer = 0.3;
  soundEngine.playHit();
  result.screenShake = 12;

  result.floatingTexts.push({
    id: `hurt_${Date.now()}`,
    x: player.x,
    y: player.y - 15,
    text: `-${netDamage}`,
    color: '#ef4444',
    alpha: 1,
    scale: 1.2,
    life: 0.9,
  });

  for (let p = 0; p < 6; p++) {
    result.particles.push({
      x: player.x,
      y: player.y,
      vx: (Math.random() - 0.5) * 100,
      vy: (Math.random() - 0.5) * 100,
      radius: 3,
      color: '#dc2626',
      alpha: 1,
      decay: 1.6,
    });
  }
}
