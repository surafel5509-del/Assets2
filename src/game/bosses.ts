/**
 * Boss AI state machines, telegraphed attack patterns, and phase logic
 */

import { ITEM_CATALOG } from './world';
import { Boss, BossId, DangerZone, Projectile, Vector2D } from './types';

export function createBoss(id: BossId, x: number, y: number): Boss {
  switch (id) {
    case 'blight_colossus':
      return {
        id,
        name: 'Blight Colossus',
        title: 'The Slime Sovereign of Dread Fen',
        biome: 'marsh',
        x,
        y,
        vx: 0,
        vy: 0,
        radius: 36,
        hp: 850,
        maxHp: 850,
        phase: 1,
        maxPhases: 2,
        state: 'idle',
        currentAttack: null,
        actionTimer: 2.0,
        windupDuration: 0,
        attackDuration: 0,
        recoveryDuration: 0,
        staggerTimer: 0,
        facing: 'down',
        arenaCenter: { x, y },
        arenaRadius: 360,
        embersReward: 400,
        itemReward: ITEM_CATALOG.vampire_crest,
        runeReward: 'marsh',
        enraged: false,
      };

    case 'ashfang_wyrm':
      return {
        id,
        name: 'Ashfang the Molten Wyrm',
        title: 'Scourge of the Obsidian Caldera',
        biome: 'caldera',
        x,
        y,
        vx: 0,
        vy: 0,
        radius: 40,
        hp: 1400,
        maxHp: 1400,
        phase: 1,
        maxPhases: 2,
        state: 'idle',
        currentAttack: null,
        actionTimer: 2.0,
        windupDuration: 0,
        attackDuration: 0,
        recoveryDuration: 0,
        staggerTimer: 0,
        facing: 'down',
        arenaCenter: { x, y },
        arenaRadius: 380,
        embersReward: 650,
        itemReward: ITEM_CATALOG.flame_claymore,
        runeReward: 'caldera',
        enraged: false,
      };

    case 'frost_vael':
      return {
        id,
        name: 'Cryomancer Lord Vael',
        title: 'Arch-Lich of the Glacial Peak',
        biome: 'frost',
        x,
        y,
        vx: 0,
        vy: 0,
        radius: 32,
        hp: 1900,
        maxHp: 1900,
        phase: 1,
        maxPhases: 2,
        state: 'idle',
        currentAttack: null,
        actionTimer: 2.0,
        windupDuration: 0,
        attackDuration: 0,
        recoveryDuration: 0,
        staggerTimer: 0,
        facing: 'down',
        arenaCenter: { x, y },
        arenaRadius: 380,
        embersReward: 850,
        itemReward: ITEM_CATALOG.moonlight_greatsword,
        runeReward: 'frost',
        enraged: false,
      };

    case 'malakor_titan':
      return {
        id,
        name: 'Malakor the Shattered Titan King',
        title: 'Lord of the Void Sanctum',
        biome: 'sanctum',
        x,
        y,
        vx: 0,
        vy: 0,
        radius: 42,
        hp: 3000,
        maxHp: 3000,
        phase: 1,
        maxPhases: 3,
        state: 'idle',
        currentAttack: null,
        actionTimer: 2.0,
        windupDuration: 0,
        attackDuration: 0,
        recoveryDuration: 0,
        staggerTimer: 0,
        facing: 'down',
        arenaCenter: { x, y },
        arenaRadius: 400,
        embersReward: 2000,
        itemReward: ITEM_CATALOG.titan_cleaver,
        enraged: false,
      };
  }
}

export interface BossUpdateResult {
  newProjectiles: Projectile[];
  newDangerZones: DangerZone[];
  spawnEnemies: { type: 'slime' | 'acid_slime' | 'fire_imp' | 'skeleton'; x: number; y: number }[];
  playBossRoar: boolean;
  screenShake: number;
}

export function updateBoss(
  boss: Boss,
  dt: number,
  playerPos: Vector2D,
  playerRollInvulnerable: boolean
): BossUpdateResult {
  const result: BossUpdateResult = {
    newProjectiles: [],
    newDangerZones: [],
    spawnEnemies: [],
    playBossRoar: false,
    screenShake: 0,
  };

  if (boss.state === 'defeated' || boss.hp <= 0) {
    boss.state = 'defeated';
    return result;
  }

  // Handle stagger (e.g. from perfect parry)
  if (boss.staggerTimer > 0) {
    boss.staggerTimer -= dt;
    boss.state = 'staggered';
    boss.vx = 0;
    boss.vy = 0;
    if (boss.staggerTimer <= 0) {
      boss.state = 'idle';
      boss.actionTimer = 1.0;
    }
    return result;
  }

  // Check Enrage & Phase triggers
  const hpRatio = boss.hp / boss.maxHp;
  if (!boss.enraged) {
    if ((boss.id === 'malakor_titan' && hpRatio <= 0.66 && boss.phase === 1) ||
        (boss.id !== 'malakor_titan' && hpRatio <= 0.5 && boss.phase === 1)) {
      boss.phase = 2;
      boss.enraged = true;
      result.playBossRoar = true;
      result.screenShake = 15;
      boss.staggerTimer = 0; // break out of any stagger
      boss.state = 'idle';
      boss.actionTimer = 1.5;
    }
  } else if (boss.id === 'malakor_titan' && hpRatio <= 0.33 && boss.phase === 2) {
    boss.phase = 3;
    result.playBossRoar = true;
    result.screenShake = 22;
    boss.actionTimer = 1.2;
  }

  // Vector to player
  const dx = playerPos.x - boss.x;
  const dy = playerPos.y - boss.y;
  const dist = Math.hypot(dx, dy);

  // Update facing
  if (Math.abs(dx) > Math.abs(dy)) {
    boss.facing = dx > 0 ? 'right' : 'left';
  } else {
    boss.facing = dy > 0 ? 'down' : 'up';
  }

  // Arena containment
  const arenaDx = boss.x - boss.arenaCenter.x;
  const arenaDy = boss.y - boss.arenaCenter.y;
  const arenaDist = Math.hypot(arenaDx, arenaDy);
  if (arenaDist > boss.arenaRadius - boss.radius) {
    const angle = Math.atan2(arenaDy, arenaDx);
    boss.x = boss.arenaCenter.x + Math.cos(angle) * (boss.arenaRadius - boss.radius);
    boss.y = boss.arenaCenter.y + Math.sin(angle) * (boss.arenaRadius - boss.radius);
  }

  // State Machine
  if (boss.state === 'idle') {
    boss.actionTimer -= dt;
    // Slow approach toward player if too far
    if (dist > 160) {
      const speed = boss.enraged ? 65 : 45;
      boss.vx = (dx / dist) * speed;
      boss.vy = (dy / dist) * speed;
      boss.x += boss.vx * dt;
      boss.y += boss.vy * dt;
    } else {
      boss.vx = 0;
      boss.vy = 0;
    }

    if (boss.actionTimer <= 0) {
      // Pick next attack
      pickBossAttack(boss, dist);
    }
  } else if (boss.state === 'windup') {
    boss.actionTimer -= dt;
    boss.vx = 0;
    boss.vy = 0;

    if (boss.actionTimer <= 0) {
      // Execute attack!
      executeBossAttack(boss, playerPos, result);
    }
  } else if (boss.state === 'attacking') {
    boss.actionTimer -= dt;
    if (boss.actionTimer <= 0) {
      boss.state = 'recovering';
      boss.actionTimer = boss.enraged ? 0.6 : 1.2;
    }
  } else if (boss.state === 'recovering') {
    boss.actionTimer -= dt;
    if (boss.actionTimer <= 0) {
      boss.state = 'idle';
      boss.actionTimer = boss.enraged ? 0.7 : 1.3;
      boss.currentAttack = null;
    }
  }

  return result;
}

function pickBossAttack(boss: Boss, distToPlayer: number) {
  const attacks: string[] = [];

  if (boss.id === 'blight_colossus') {
    attacks.push('slam');
    attacks.push('projectile_burst');
    if (boss.enraged) attacks.push('summon');
  } else if (boss.id === 'ashfang_wyrm') {
    attacks.push('breath');
    attacks.push('meteor');
    attacks.push('tail_sweep');
    if (boss.enraged) attacks.push('magma_dash');
  } else if (boss.id === 'frost_vael') {
    attacks.push('frost_lances');
    attacks.push('teleport_slash');
    attacks.push('blizzard_ring');
    if (boss.enraged) attacks.push('frost_beam');
  } else if (boss.id === 'malakor_titan') {
    attacks.push('blade_combo');
    attacks.push('titan_slam');
    attacks.push('parriable_thrust');
    if (boss.phase >= 2) attacks.push('void_nova');
    if (boss.phase >= 3) attacks.push('cataclysm');
  }

  const chosen = attacks[Math.floor(Math.random() * attacks.length)];
  const windup = boss.enraged ? 0.7 : 1.0;

  boss.currentAttack = {
    name: chosen,
    type: 'slam',
    cooldown: 3,
    windup,
  };
  boss.state = 'windup';
  boss.actionTimer = windup;
}

function executeBossAttack(boss: Boss, playerPos: Vector2D, result: BossUpdateResult) {
  if (!boss.currentAttack) return;
  const attackName = boss.currentAttack.name;

  boss.state = 'attacking';
  boss.actionTimer = 0.8;

  const dx = playerPos.x - boss.x;
  const dy = playerPos.y - boss.y;
  const baseAngle = Math.atan2(dy, dx);

  // ----------------------------------------------------
  // Blight Colossus Attacks
  // ----------------------------------------------------
  if (boss.id === 'blight_colossus') {
    if (attackName === 'slam') {
      // Create massive warning circle under player, then leap slam
      result.newDangerZones.push({
        id: `zone_${Date.now()}`,
        x: playerPos.x,
        y: playerPos.y,
        radius: 95,
        duration: 0.9,
        maxDuration: 0.9,
        damage: 48,
        color: 'rgba(34, 197, 94, 0.45)',
        type: 'circle',
        effect: 'acid',
      });
      // Ring of 8 acid projectiles radiating outward
      setTimeout(() => {
        for (let i = 0; i < 8; i++) {
          const angle = (i * Math.PI * 2) / 8;
          result.newProjectiles.push({
            id: `proj_${Date.now()}_${i}`,
            x: boss.x,
            y: boss.y,
            vx: Math.cos(angle) * 160,
            vy: Math.sin(angle) * 160,
            radius: 9,
            damage: 28,
            source: 'boss',
            color: '#22c55e',
            lifetime: 3.5,
            effect: 'poison',
          });
        }
      }, 850);
      result.screenShake = 12;
    } else if (attackName === 'projectile_burst') {
      // Fan of 5 toxic blobs
      for (let i = -2; i <= 2; i++) {
        const angle = baseAngle + i * 0.22;
        result.newProjectiles.push({
          id: `proj_${Date.now()}_${i}`,
          x: boss.x,
          y: boss.y,
          vx: Math.cos(angle) * 190,
          vy: Math.sin(angle) * 190,
          radius: 8,
          damage: 26,
          source: 'boss',
          color: '#84cc16',
          lifetime: 3.0,
          effect: 'poison',
        });
      }
    } else if (attackName === 'summon') {
      // Spawn two acid slimes
      result.spawnEnemies.push({ type: 'acid_slime', x: boss.x - 50, y: boss.y });
      result.spawnEnemies.push({ type: 'acid_slime', x: boss.x + 50, y: boss.y });
    }
  }

  // ----------------------------------------------------
  // Ashfang the Molten Wyrm Attacks
  // ----------------------------------------------------
  else if (boss.id === 'ashfang_wyrm') {
    if (attackName === 'breath') {
      // Wide sweeping fire cone
      result.newDangerZones.push({
        id: `zone_cone_${Date.now()}`,
        x: boss.x,
        y: boss.y,
        radius: 170,
        duration: 1.1,
        maxDuration: 1.1,
        damage: 55,
        color: 'rgba(234, 88, 12, 0.45)',
        type: 'cone',
        angle: baseAngle,
        spreadAngle: 0.9,
        effect: 'fire',
      });
      result.screenShake = 10;
    } else if (attackName === 'meteor') {
      // Spawns 4 meteor strike danger circles around player
      for (let i = 0; i < 4; i++) {
        const offsetAngle = Math.random() * Math.PI * 2;
        const offsetDist = Math.random() * 110;
        result.newDangerZones.push({
          id: `zone_meteor_${Date.now()}_${i}`,
          x: playerPos.x + Math.cos(offsetAngle) * offsetDist,
          y: playerPos.y + Math.sin(offsetAngle) * offsetDist,
          radius: 55,
          duration: 1.0 + i * 0.15,
          maxDuration: 1.0 + i * 0.15,
          damage: 50,
          color: 'rgba(220, 38, 38, 0.5)',
          type: 'circle',
          effect: 'fire',
        });
      }
    } else if (attackName === 'tail_sweep') {
      // 360-degree close range knockback
      result.newDangerZones.push({
        id: `zone_sweep_${Date.now()}`,
        x: boss.x,
        y: boss.y,
        radius: 110,
        duration: 0.7,
        maxDuration: 0.7,
        damage: 42,
        color: 'rgba(185, 28, 28, 0.45)',
        type: 'circle',
      });
      result.screenShake = 14;
    } else if (attackName === 'magma_dash') {
      // Fast charge toward player
      boss.x += Math.cos(baseAngle) * 140;
      boss.y += Math.sin(baseAngle) * 140;
      result.screenShake = 16;
      // 12-directional fireball nova
      for (let i = 0; i < 12; i++) {
        const angle = (i * Math.PI * 2) / 12;
        result.newProjectiles.push({
          id: `proj_wyrm_${Date.now()}_${i}`,
          x: boss.x,
          y: boss.y,
          vx: Math.cos(angle) * 180,
          vy: Math.sin(angle) * 180,
          radius: 9,
          damage: 32,
          source: 'boss',
          color: '#f97316',
          lifetime: 3.5,
          effect: 'fire',
        });
      }
    }
  }

  // ----------------------------------------------------
  // Cryomancer Lord Vael Attacks
  // ----------------------------------------------------
  else if (boss.id === 'frost_vael') {
    if (attackName === 'frost_lances') {
      // 5 piercing ice spears
      for (let i = -2; i <= 2; i++) {
        const angle = baseAngle + i * 0.25;
        result.newProjectiles.push({
          id: `lance_${Date.now()}_${i}`,
          x: boss.x,
          y: boss.y,
          vx: Math.cos(angle) * 220,
          vy: Math.sin(angle) * 220,
          radius: 8,
          damage: 34,
          source: 'boss',
          color: '#38bdf8',
          lifetime: 3.2,
          effect: 'frost',
        });
      }
    } else if (attackName === 'teleport_slash') {
      // Teleports behind player and creates immediate slash zone
      const teleportDist = 70;
      boss.x = playerPos.x - Math.cos(baseAngle) * teleportDist;
      boss.y = playerPos.y - Math.sin(baseAngle) * teleportDist;
      result.newDangerZones.push({
        id: `zone_teleport_${Date.now()}`,
        x: playerPos.x,
        y: playerPos.y,
        radius: 65,
        duration: 0.5,
        maxDuration: 0.5,
        damage: 45,
        color: 'rgba(56, 189, 248, 0.5)',
        type: 'circle',
        effect: 'frost',
      });
      result.screenShake = 10;
    } else if (attackName === 'blizzard_ring') {
      // Expanding frost ring - 16 projectiles
      for (let i = 0; i < 16; i++) {
        const angle = (i * Math.PI * 2) / 16;
        result.newProjectiles.push({
          id: `ring_${Date.now()}_${i}`,
          x: boss.x,
          y: boss.y,
          vx: Math.cos(angle) * 140,
          vy: Math.sin(angle) * 140,
          radius: 8,
          damage: 30,
          source: 'boss',
          color: '#e0f2fe',
          lifetime: 4.0,
          effect: 'frost',
        });
      }
    }
  }

  // ----------------------------------------------------
  // Titan King Malakor Attacks
  // ----------------------------------------------------
  else if (boss.id === 'malakor_titan') {
    if (attackName === 'blade_combo') {
      // 3 rapid slashes towards player
      result.newDangerZones.push({
        id: `zone_combo_${Date.now()}`,
        x: boss.x + Math.cos(baseAngle) * 55,
        y: boss.y + Math.sin(baseAngle) * 55,
        radius: 75,
        duration: 0.6,
        maxDuration: 0.6,
        damage: 55,
        color: 'rgba(234, 179, 8, 0.5)',
        type: 'circle',
      });
      result.screenShake = 14;
    } else if (attackName === 'titan_slam') {
      // Massive overhead slam shockwave
      result.newDangerZones.push({
        id: `zone_titan_slam_${Date.now()}`,
        x: playerPos.x,
        y: playerPos.y,
        radius: 120,
        duration: 0.95,
        maxDuration: 0.95,
        damage: 75,
        color: 'rgba(99, 102, 241, 0.55)',
        type: 'circle',
      });
      result.screenShake = 20;
    } else if (attackName === 'parriable_thrust') {
      // Telegraphed linear thrust - perfect for parrying!
      result.newDangerZones.push({
        id: `zone_thrust_${Date.now()}`,
        x: boss.x + Math.cos(baseAngle) * 70,
        y: boss.y + Math.sin(baseAngle) * 70,
        radius: 50,
        duration: 0.7,
        maxDuration: 0.7,
        damage: 60,
        color: 'rgba(251, 191, 36, 0.6)',
        type: 'circle',
      });
    } else if (attackName === 'void_nova') {
      // 20 void projectiles spiral outward
      for (let i = 0; i < 20; i++) {
        const angle = (i * Math.PI * 2) / 20;
        result.newProjectiles.push({
          id: `void_nova_${Date.now()}_${i}`,
          x: boss.x,
          y: boss.y,
          vx: Math.cos(angle) * 170,
          vy: Math.sin(angle) * 170,
          radius: 10,
          damage: 42,
          source: 'boss',
          color: '#a855f7',
          lifetime: 4.0,
        });
      }
      result.screenShake = 16;
    } else if (attackName === 'cataclysm') {
      // Ultimate cataclysm: 6 huge bombardment zones
      for (let i = 0; i < 6; i++) {
        const offsetAngle = (i * Math.PI * 2) / 6;
        const offsetDist = 130;
        result.newDangerZones.push({
          id: `cataclysm_${Date.now()}_${i}`,
          x: boss.arenaCenter.x + Math.cos(offsetAngle) * offsetDist,
          y: boss.arenaCenter.y + Math.sin(offsetAngle) * offsetDist,
          radius: 80,
          duration: 1.3,
          maxDuration: 1.3,
          damage: 85,
          color: 'rgba(239, 68, 68, 0.6)',
          type: 'circle',
          effect: 'fire',
        });
      }
      result.screenShake = 24;
    }
  }
}
