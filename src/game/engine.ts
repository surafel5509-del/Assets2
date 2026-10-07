/**
 * Main Game Engine: Game Loop, Camera Lerp, Rendering, Lighting, and State Management
 */

import { soundEngine } from './audio';
import { createBoss, updateBoss } from './bosses';
import { CombatUpdateResult, createEnemy, updateEnemiesAndCombat } from './combat';
import { PixelArt } from './pixelArt';
import { createInitialPlayer, equipItem, recalculatePlayerStats, startPlayerAttack, startPlayerBlock, startPlayerRoll, updatePlayerMovement, usePlayerFlask } from './player';
import { BiomeType, Boss, Chest, DangerZone, Destructible, Enemy, FloatingText, GameView, Item, NPC, Particle, Player, Projectile, Shrine, WorldMap } from './types';
import { createWorldMap, ITEM_CATALOG } from './world';

export class GameEngine {
  public player: Player;
  public worldMap: WorldMap;
  public enemies: Enemy[] = [];
  public bosses: Boss[] = [];
  public activeBoss: Boss | null = null;
  public projectiles: Projectile[] = [];
  public dangerZones: DangerZone[] = [];
  public particles: Particle[] = [];
  public floatingTexts: FloatingText[] = [];

  public camera = { x: 0, y: 0, shake: 0 };
  public dayTime: number = 8.0; // 0 to 24 hours
  public gameView: GameView = 'playing';

  public currentInteraction: {
    type: 'shrine' | 'chest' | 'npc' | 'gate';
    target: Shrine | Chest | NPC | null;
    label: string;
  } | null = null;

  public activeDialogue: {
    speaker: string;
    text: string;
  } | null = null;
  private dialogueStep: number = 0;

  // Input states
  public keys = {
    up: false,
    down: false,
    left: false,
    right: false,
    attack: false,
    block: false,
    roll: false,
    interact: false,
  };
  public mousePos = { x: 0, y: 0 };

  private animFrameId: number | null = null;
  private lastTime: number = 0;
  private onStateChangeCallback: (() => void) | null = null;

  constructor() {
    this.worldMap = createWorldMap();
    this.player = createInitialPlayer();
    this.initEntities();
  }

  public setOnStateChange(cb: () => void) {
    this.onStateChangeCallback = cb;
  }

  private notify() {
    if (this.onStateChangeCallback) {
      this.onStateChangeCallback();
    }
  }

  private initEntities() {
    // Instantiate Bosses
    this.bosses = [
      createBoss('blight_colossus', 25 * 32 + 16, 20 * 32 + 16),
      createBoss('ashfang_wyrm', 100 * 32 + 16, 20 * 32 + 16),
      createBoss('frost_vael', 100 * 32 + 16, 100 * 32 + 16),
      createBoss('malakor_titan', 60 * 32 + 16, 58 * 32 + 16),
    ];

    // Seed regular enemies across biomes
    const enemySpawns: { type: any; x: number; y: number }[] = [
      // Verdant
      { type: 'slime', x: 28 * 32, y: 76 * 32 },
      { type: 'slime', x: 40 * 32, y: 84 * 32 },
      { type: 'slime', x: 44 * 32, y: 92 * 32 },
      { type: 'slime', x: 22 * 32, y: 95 * 32 },
      { type: 'skeleton', x: 48 * 32, y: 75 * 32 },
      // Marsh
      { type: 'acid_slime', x: 28 * 32, y: 38 * 32 },
      { type: 'acid_slime', x: 20 * 32, y: 46 * 32 },
      { type: 'skeleton', x: 38 * 32, y: 32 * 32 },
      { type: 'acid_slime', x: 22 * 32, y: 28 * 32 },
      // Caldera
      { type: 'fire_imp', x: 88 * 32, y: 32 * 32 },
      { type: 'fire_imp', x: 92 * 32, y: 24 * 32 },
      { type: 'magma_brute', x: 82 * 32, y: 28 * 32 },
      { type: 'fire_imp', x: 104 * 32, y: 35 * 32 },
      // Frost
      { type: 'frost_wraith', x: 88 * 32, y: 78 * 32 },
      { type: 'frost_wraith', x: 96 * 32, y: 94 * 32 },
      { type: 'skeleton', x: 84 * 32, y: 90 * 32 },
      { type: 'frost_wraith', x: 105 * 32, y: 85 * 32 },
    ];

    this.enemies = enemySpawns.map((s) => createEnemy(s.type, s.x, s.y));
  }

  public isSolidTile = (px: number, py: number): boolean => {
    const tx = Math.floor(px / this.worldMap.tileSize);
    const ty = Math.floor(py / this.worldMap.tileSize);

    if (tx < 0 || tx >= this.worldMap.width || ty < 0 || ty >= this.worldMap.height) {
      return true;
    }
    const tile = this.worldMap.tiles[ty]?.[tx];
    return tile ? tile.solid : false;
  };

  // --------------------------------------------------------------------------
  // CORE LOOP
  // --------------------------------------------------------------------------
  public update(dt: number) {
    if (this.gameView !== 'playing') return;

    // Check Player Death
    if (this.player.hp <= 0) {
      this.gameView = 'game_over';
      soundEngine.stopMusic();
      this.notify();
      return;
    }

    // Progress Day/Night Cycle (24 hours over ~8 minutes)
    this.dayTime = (this.dayTime + dt * 0.05) % 24;

    // Convert screen mouse to world coordinates for aiming
    const aimWorldX = this.mousePos.x + this.camera.x;
    const aimWorldY = this.mousePos.y + this.camera.y;

    // 1. Update Player Movement
    updatePlayerMovement(
      this.player,
      {
        up: this.keys.up,
        down: this.keys.down,
        left: this.keys.left,
        right: this.keys.right,
        aimX: aimWorldX,
        aimY: aimWorldY,
      },
      dt,
      this.isSolidTile
    );

    // 2. Update Bosses
    let engagingBoss: Boss | null = null;
    for (const boss of this.bosses) {
      if (boss.state === 'defeated' || boss.hp <= 0) {
        if (!this.player.defeatedBosses.includes(boss.id)) {
          this.handleBossDefeated(boss);
        }
        continue;
      }

      const distToPlayer = Math.hypot(this.player.x - boss.x, this.player.y - boss.y);
      if (distToPlayer <= boss.arenaRadius) {
        engagingBoss = boss;

        const bossResult = updateBoss(
          boss,
          dt,
          { x: this.player.x, y: this.player.y },
          this.player.isRolling
        );

        if (bossResult.newProjectiles.length) {
          this.projectiles.push(...bossResult.newProjectiles);
        }
        if (bossResult.newDangerZones.length) {
          this.dangerZones.push(...bossResult.newDangerZones);
        }
        if (bossResult.spawnEnemies.length) {
          for (const se of bossResult.spawnEnemies) {
            this.enemies.push(createEnemy(se.type, se.x, se.y));
          }
        }
        if (bossResult.playBossRoar) {
          soundEngine.playBossRoar();
        }
        if (bossResult.screenShake > 0) {
          this.camera.shake = Math.max(this.camera.shake, bossResult.screenShake);
        }
      }
    }
    this.activeBoss = engagingBoss;

    // Update Ambient Music based on location & boss
    if (this.activeBoss) {
      soundEngine.playMusic('boss');
    } else {
      const pBiome = this.getCurrentBiome();
      if (pBiome === 'marsh') soundEngine.playMusic('swamp');
      else if (pBiome === 'caldera') soundEngine.playMusic('caldera');
      else if (pBiome === 'frost') soundEngine.playMusic('frost');
      else soundEngine.playMusic('explore');
    }

    // 3. Update Regular Enemies & Combat
    const combatResult: CombatUpdateResult = updateEnemiesAndCombat(
      this.enemies,
      this.bosses,
      this.player,
      this.projectiles,
      this.dangerZones,
      dt,
      this.isSolidTile
    );

    if (combatResult.floatingTexts.length) {
      this.floatingTexts.push(...combatResult.floatingTexts);
    }
    if (combatResult.particles.length) {
      this.particles.push(...combatResult.particles);
    }
    if (combatResult.newProjectiles.length) {
      this.projectiles.push(...combatResult.newProjectiles);
    }
    if (combatResult.screenShake > 0) {
      this.camera.shake = Math.max(this.camera.shake, combatResult.screenShake);
    }

    // 4. Update Particles
    for (let i = this.particles.length - 1; i >= 0; i--) {
      const p = this.particles[i];
      p.alpha -= p.decay * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      if (p.alpha <= 0) {
        this.particles.splice(i, 1);
      }
    }

    // 5. Update Floating Texts
    for (let i = this.floatingTexts.length - 1; i >= 0; i--) {
      const ft = this.floatingTexts[i];
      ft.life -= dt;
      ft.y -= 25 * dt;
      ft.alpha = Math.max(0, ft.life);
      if (ft.life <= 0) {
        this.floatingTexts.splice(i, 1);
      }
    }

    // 6. Check Nearby Interactions
    this.checkInteractions();

    // 7. Update Camera (Smooth Lerp + Screen Shake)
    const targetCamX = this.player.x - window.innerWidth / 2;
    const targetCamY = this.player.y - window.innerHeight / 2;
    this.camera.x += (targetCamX - this.camera.x) * 0.1;
    this.camera.y += (targetCamY - this.camera.y) * 0.1;

    // Decay Screen Shake
    if (this.camera.shake > 0) {
      this.camera.shake = Math.max(0, this.camera.shake - dt * 25);
    }
  }

  public getCurrentBiome(): BiomeType {
    const tx = Math.floor(this.player.x / 32);
    const ty = Math.floor(this.player.y / 32);
    return this.worldMap.tiles[ty]?.[tx]?.biome || 'verdant';
  }

  private handleBossDefeated(boss: Boss) {
    this.player.defeatedBosses.push(boss.id);
    this.player.stats.embers += boss.embersReward;
    this.player.inventory.push(boss.itemReward);

    if (boss.runeReward) {
      this.player.unlockedRunes[boss.runeReward] = true;
    }

    soundEngine.playVictory();
    this.camera.shake = 25;

    this.floatingTexts.push({
      id: `vic_${Date.now()}`,
      x: boss.x,
      y: boss.y - 40,
      text: `${boss.name} DEFEATED!`,
      color: '#fbbf24',
      alpha: 1,
      scale: 1.8,
      life: 3.5,
    });

    if (boss.id === 'malakor_titan') {
      setTimeout(() => {
        this.gameView = 'victory';
        this.notify();
      }, 2500);
    }
  }

  private checkInteractions() {
    const p = this.player;
    let foundInteraction: any = null;

    // Check Shrines
    for (const shrine of this.worldMap.shrines) {
      if (Math.hypot(p.x - shrine.x, p.y - shrine.y) < 45) {
        if (!shrine.discovered) {
          shrine.discovered = true;
          if (!p.visitedShrines.includes(shrine.id)) {
            p.visitedShrines.push(shrine.id);
          }
        }
        foundInteraction = {
          type: 'shrine',
          target: shrine,
          label: `Rest at ${shrine.name} [E]`,
        };
        break;
      }
    }

    // Check Chests
    if (!foundInteraction) {
      for (const chest of this.worldMap.chests) {
        if (!chest.opened && Math.hypot(p.x - chest.x, p.y - chest.y) < 40) {
          foundInteraction = {
            type: 'chest',
            target: chest,
            label: `Open Ancient Chest [E]`,
          };
          break;
        }
      }
    }

    // Check NPCs
    if (!foundInteraction) {
      for (const npc of this.worldMap.npcs) {
        if (Math.hypot(p.x - npc.x, p.y - npc.y) < 50) {
          foundInteraction = {
            type: 'npc',
            target: npc,
            label: `Talk with ${npc.name} [E]`,
          };
          break;
        }
      }
    }

    this.currentInteraction = foundInteraction;
  }

  public triggerInteract() {
    if (!this.currentInteraction) return;

    if (this.currentInteraction.type === 'shrine') {
      const shrine = this.currentInteraction.target as Shrine;
      this.player.activeShrineId = shrine.id;
      // Refill health & flasks
      this.player.hp = this.player.maxHp;
      this.player.flasks = this.player.maxFlasks;
      soundEngine.playShrineRest();
      this.gameView = 'shrine';
      this.notify();
    } else if (this.currentInteraction.type === 'chest') {
      const chest = this.currentInteraction.target as Chest;
      chest.opened = true;
      this.player.inventory.push(chest.item);
      this.player.stats.embers += chest.embers;
      soundEngine.playPotion(); // mystic chime

      this.floatingTexts.push({
        id: `chest_item_${Date.now()}`,
        x: chest.x,
        y: chest.y - 25,
        text: `Acquired: ${chest.item.name}!`,
        color: '#facc15',
        alpha: 1,
        scale: 1.3,
        life: 2.0,
      });
      this.notify();
    } else if (this.currentInteraction.type === 'npc') {
      const npc = this.currentInteraction.target as NPC;
      const text = npc.dialogue[this.dialogueStep % npc.dialogue.length];
      this.activeDialogue = {
        speaker: npc.name,
        text,
      };
      this.dialogueStep++;
      this.notify();
    }
  }

  public closeDialogue() {
    this.activeDialogue = null;
    this.notify();
  }

  // --------------------------------------------------------------------------
  // PLAYER ACTIONS
  // --------------------------------------------------------------------------
  public triggerAttack() {
    if (this.gameView !== 'playing') return;
    const ok = startPlayerAttack(this.player);
    if (ok) {
      soundEngine.playAttack(this.player.attackCombo);
      this.notify();
    }
  }

  public triggerRoll() {
    if (this.gameView !== 'playing') return;
    const ok = startPlayerRoll(this.player);
    if (ok) {
      soundEngine.playRoll();
      this.notify();
    }
  }

  public triggerBlock(block: boolean) {
    if (this.gameView !== 'playing') return;
    startPlayerBlock(this.player, block);
    this.notify();
  }

  public triggerFlask() {
    if (this.gameView !== 'playing') return;
    const ok = usePlayerFlask(this.player);
    if (ok) {
      soundEngine.playPotion();
      this.floatingTexts.push({
        id: `heal_${Date.now()}`,
        x: this.player.x,
        y: this.player.y - 20,
        text: `+HEAL`,
        color: '#4ade80',
        alpha: 1,
        scale: 1.2,
        life: 1.0,
      });
      this.notify();
    }
  }

  public castSpell(type: 'fire' | 'frost' | 'thunder') {
    if (this.gameView !== 'playing') return;
    const p = this.player;

    if (type === 'fire') {
      if (p.mana < 25) return;
      p.mana -= 25;
      soundEngine.playSpell('fire');
      const baseAng = p.angle;
      for (let i = -1; i <= 1; i++) {
        const ang = baseAng + i * 0.25;
        this.projectiles.push({
          id: `spell_fire_${Date.now()}_${i}`,
          x: p.x,
          y: p.y,
          vx: Math.cos(ang) * 260,
          vy: Math.sin(ang) * 260,
          radius: 9,
          damage: 45 + p.stats.arcane * 3,
          source: 'player',
          color: '#f97316',
          lifetime: 1.8,
          effect: 'fire',
        });
      }
    } else if (type === 'frost') {
      if (p.mana < 35) return;
      p.mana -= 35;
      soundEngine.playSpell('frost');
      // Freeze all nearby enemies
      for (const enemy of this.enemies) {
        if (Math.hypot(enemy.x - p.x, enemy.y - p.y) < 160) {
          enemy.isStaggered = true;
          enemy.staggerTimer = 2.5; // Frozen for 2.5s
          enemy.hp -= 20 + p.stats.arcane * 2;
        }
      }
      // Frost ring particle
      for (let i = 0; i < 20; i++) {
        const ang = (i * Math.PI * 2) / 20;
        this.particles.push({
          x: p.x,
          y: p.y,
          vx: Math.cos(ang) * 140,
          vy: Math.sin(ang) * 140,
          radius: 4,
          color: '#38bdf8',
          alpha: 1,
          decay: 1.8,
        });
      }
    } else if (type === 'thunder') {
      if (p.mana < 45) return;
      p.mana -= 45;
      soundEngine.playSpell('thunder');
      this.projectiles.push({
        id: `spell_thunder_${Date.now()}`,
        x: p.x,
        y: p.y,
        vx: Math.cos(p.angle) * 420,
        vy: Math.sin(p.angle) * 420,
        radius: 12,
        damage: 90 + p.stats.arcane * 5,
        source: 'player',
        color: '#facc15',
        lifetime: 1.2,
        piercing: true,
      });
      this.camera.shake = 10;
    }
    this.notify();
  }

  public fastTravelToShrine(shrineId: string) {
    const shrine = this.worldMap.shrines.find((s) => s.id === shrineId);
    if (!shrine) return;

    this.player.x = shrine.x;
    this.player.y = shrine.y + 24;
    this.player.activeShrineId = shrine.id;
    this.player.hp = this.player.maxHp;
    this.player.flasks = this.player.maxFlasks;
    this.gameView = 'playing';
    soundEngine.playShrineRest();
    this.notify();
  }

  public respawn() {
    const shrine = this.worldMap.shrines.find((s) => s.id === this.player.activeShrineId) || this.worldMap.shrines[0];
    this.player.x = shrine.x;
    this.player.y = shrine.y + 24;
    this.player.hp = this.player.maxHp;
    this.player.stamina = this.player.maxStamina;
    this.player.flasks = this.player.maxFlasks;
    this.gameView = 'playing';
    this.notify();
  }

  public levelUpStat(stat: 'vigor' | 'endurance' | 'strength' | 'arcane'): boolean {
    const cost = 50 + this.player.stats.level * 30;
    if (this.player.stats.embers < cost) return false;

    this.player.stats.embers -= cost;
    this.player.stats[stat] += 1;
    this.player.stats.level += 1;
    recalculatePlayerStats(this.player);
    soundEngine.playPotion();
    this.notify();
    return true;
  }

  // --------------------------------------------------------------------------
  // RENDERING ENGINE
  // --------------------------------------------------------------------------
  public render(ctx: CanvasRenderingContext2D, width: number, height: number) {
    ctx.clearRect(0, 0, width, height);

    // Apply Camera + Screen Shake
    ctx.save();
    const shakeX = (Math.random() - 0.5) * this.camera.shake;
    const shakeY = (Math.random() - 0.5) * this.camera.shake;
    ctx.translate(-Math.floor(this.camera.x + shakeX), -Math.floor(this.camera.y + shakeY));

    // Visible tile boundaries for high performance viewport culling
    const startCol = Math.max(0, Math.floor((this.camera.x - 32) / 32));
    const endCol = Math.min(this.worldMap.width - 1, Math.ceil((this.camera.x + width + 32) / 32));
    const startRow = Math.max(0, Math.floor((this.camera.y - 32) / 32));
    const endRow = Math.min(this.worldMap.height - 1, Math.ceil((this.camera.y + height + 32) / 32));

    // 1. Draw World Ground Tiles
    for (let r = startRow; r <= endRow; r++) {
      for (let c = startCol; c <= endCol; c++) {
        const tile = this.worldMap.tiles[r][c];
        const tileCanvas = PixelArt.getTileCanvas(tile.type, tile.biome, tile.variant);
        ctx.drawImage(tileCanvas, c * 32, r * 32);
      }
    }

    // 2. Draw Shrines
    for (const shrine of this.worldMap.shrines) {
      const shrineSprite = PixelArt.getShrineSprite(shrine.discovered, Math.floor(Date.now() / 250));
      ctx.drawImage(shrineSprite, shrine.x - 24, shrine.y - 24);
    }

    // 3. Draw Chests
    for (const chest of this.worldMap.chests) {
      const chestSprite = PixelArt.getChestSprite(chest.opened);
      ctx.drawImage(chestSprite, chest.x - 16, chest.y - 16);
    }

    // 4. Draw NPCs
    for (const npc of this.worldMap.npcs) {
      ctx.fillStyle = 'rgba(0,0,0,0.3)';
      ctx.beginPath();
      ctx.ellipse(npc.x, npc.y + 12, 8, 3, 0, 0, Math.PI * 2);
      ctx.fill();

      // NPC sprite (Elder cloak)
      ctx.fillStyle = '#4338ca';
      ctx.fillRect(npc.x - 8, npc.y - 12, 16, 20);
      ctx.fillStyle = '#e0e7ff';
      ctx.fillRect(npc.x - 6, npc.y - 18, 12, 8); // face/beard
      ctx.fillStyle = '#fbbf24';
      ctx.fillRect(npc.x - 2, npc.y - 24, 4, 6); // staff tip
    }

    // 5. Draw Danger Zones (Red telegraphs)
    for (const zone of this.dangerZones) {
      const progress = 1 - zone.duration / zone.maxDuration;
      ctx.save();
      ctx.fillStyle = zone.color;
      ctx.strokeStyle = '#ef4444';
      ctx.lineWidth = 2;

      if (zone.type === 'circle') {
        ctx.beginPath();
        ctx.arc(zone.x, zone.y, zone.radius, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();

        // Expanding charge ring
        ctx.beginPath();
        ctx.arc(zone.x, zone.y, zone.radius * progress, 0, Math.PI * 2);
        ctx.strokeStyle = '#ffffff';
        ctx.stroke();
      } else if (zone.type === 'cone' && zone.angle !== undefined && zone.spreadAngle !== undefined) {
        ctx.beginPath();
        ctx.moveTo(zone.x, zone.y);
        ctx.arc(zone.x, zone.y, zone.radius, zone.angle - zone.spreadAngle, zone.angle + zone.spreadAngle);
        ctx.closePath();
        ctx.fill();
        ctx.stroke();
      }
      ctx.restore();
    }

    // 6. Draw Regular Enemies
    for (const enemy of this.enemies) {
      const enemySprite = PixelArt.getEnemySprite(
        enemy.type,
        Math.floor(Date.now() / 200),
        enemy.hurtTimer > 0
      );
      ctx.drawImage(enemySprite, enemy.x - 16, enemy.y - 16);

      // Enemy Mini Health Bar
      if (enemy.hp < enemy.maxHp) {
        const hpPercent = Math.max(0, enemy.hp / enemy.maxHp);
        ctx.fillStyle = 'rgba(0,0,0,0.6)';
        ctx.fillRect(enemy.x - 14, enemy.y - 20, 28, 4);
        ctx.fillStyle = '#ef4444';
        ctx.fillRect(enemy.x - 14, enemy.y - 20, 28 * hpPercent, 4);
      }
    }

    // 7. Draw Bosses
    for (const boss of this.bosses) {
      if (boss.state === 'defeated' || boss.hp <= 0) continue;

      const bossSprite = PixelArt.getBossSprite(
        boss.id,
        boss.state,
        Math.floor(Date.now() / 250),
        boss.enraged
      );
      ctx.drawImage(bossSprite, boss.x - 40, boss.y - 40);

      // Arena boundary ring when engaged
      if (this.activeBoss?.id === boss.id) {
        ctx.save();
        ctx.strokeStyle = 'rgba(239, 68, 68, 0.25)';
        ctx.lineWidth = 3;
        ctx.setLineDash([8, 8]);
        ctx.beginPath();
        ctx.arc(boss.arenaCenter.x, boss.arenaCenter.y, boss.arenaRadius, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }
    }

    // 8. Draw Player
    let playerState: 'idle' | 'walk' | 'attack1' | 'attack2' | 'attack3' | 'block' | 'roll' | 'hurt' = 'idle';
    if (this.player.hurtTimer > 0) playerState = 'hurt';
    else if (this.player.isRolling) playerState = 'roll';
    else if (this.player.isBlocking) playerState = 'block';
    else if (this.player.isAttacking) playerState = `attack${this.player.attackCombo}` as any;
    else if (this.player.vx !== 0 || this.player.vy !== 0) playerState = 'walk';

    const playerFrame = Math.floor(Date.now() / 150);
    const playerSprite = PixelArt.getPlayerSprite(this.player.facing, playerState, playerFrame);
    ctx.drawImage(playerSprite, this.player.x - 16, this.player.y - 16);

    // 9. Draw Projectiles
    for (const proj of this.projectiles) {
      ctx.fillStyle = proj.color;
      ctx.beginPath();
      ctx.arc(proj.x, proj.y, proj.radius, 0, Math.PI * 2);
      ctx.fill();
      // Glow ring
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 1.5;
      ctx.stroke();
    }

    // 10. Draw Particles
    for (const part of this.particles) {
      ctx.save();
      ctx.globalAlpha = part.alpha;
      ctx.fillStyle = part.color;
      ctx.beginPath();
      ctx.arc(part.x, part.y, part.radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }

    // 11. Draw Floating Combat Text
    for (const ft of this.floatingTexts) {
      ctx.save();
      ctx.globalAlpha = ft.alpha;
      ctx.fillStyle = ft.color;
      ctx.font = `bold ${Math.floor(13 * ft.scale)}px 'Silkscreen', monospace`;
      ctx.textAlign = 'center';
      ctx.fillText(ft.text, ft.x, ft.y);
      ctx.restore();
    }

    // 12. Dynamic Lighting & Day/Night Atmosphere
    this.renderLighting(ctx, width, height);

    ctx.restore();
  }

  private renderLighting(ctx: CanvasRenderingContext2D, width: number, height: number) {
    // Determine ambient darkness: Night is from 20:00 to 05:00
    let darkAlpha = 0;
    if (this.dayTime >= 20 || this.dayTime <= 5) {
      darkAlpha = 0.72; // Deep night
    } else if (this.dayTime > 5 && this.dayTime < 8) {
      darkAlpha = 0.72 * (1 - (this.dayTime - 5) / 3); // Dawn
    } else if (this.dayTime > 18 && this.dayTime < 20) {
      darkAlpha = 0.72 * ((this.dayTime - 18) / 2); // Dusk
    }

    if (darkAlpha > 0.05) {
      ctx.save();
      ctx.fillStyle = `rgba(10, 15, 30, ${darkAlpha})`;

      // Create radial cutout around player (Lantern light!)
      const grad = ctx.createRadialGradient(
        this.player.x,
        this.player.y,
        30,
        this.player.x,
        this.player.y,
        180
      );
      grad.addColorStop(0, 'rgba(0, 0, 0, 0)');
      grad.addColorStop(1, `rgba(10, 15, 30, ${darkAlpha})`);

      // Fill screen with darkness except torch hole
      ctx.fillRect(
        this.camera.x - 30,
        this.camera.y - 30,
        width + 60,
        height + 60
      );

      ctx.globalCompositeOperation = 'destination-out';
      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.arc(this.player.x, this.player.y, 180, 0, Math.PI * 2);
      ctx.fill();

      // Shrines also emit cozy glowing light
      for (const shrine of this.worldMap.shrines) {
        if (shrine.discovered) {
          ctx.beginPath();
          ctx.arc(shrine.x, shrine.y, 140, 0, Math.PI * 2);
          ctx.fill();
        }
      }

      ctx.restore();
    }
  }
}
