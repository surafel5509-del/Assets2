/**
 * Procedural Pixel Art Sprite & Tile Generator
 * Generates crisp 16-bit style pixel sprites and tiles on offscreen canvases
 */

import { BiomeType, BossId, Direction, EnemyType } from './types';

// Helper to draw a pixel grid from a matrix of color strings
function createPixelCanvas(
  width: number,
  height: number,
  drawFn: (ctx: CanvasRenderingContext2D) => void
): HTMLCanvasElement {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (ctx) {
    ctx.imageSmoothingEnabled = false;
    drawFn(ctx);
  }
  return canvas;
}

// Map of cached sprite canvases
const spriteCache: Map<string, HTMLCanvasElement> = new Map();

export class PixelArt {
  // Clear or initialize
  public static init() {
    // Warm up procedural caches
  }

  // Draw a grid of pixels from a color key and row strings
  public static drawMatrix(
    ctx: CanvasRenderingContext2D,
    startX: number,
    startY: number,
    pixelSize: number,
    colorPalette: Record<string, string>,
    rows: string[]
  ) {
    for (let r = 0; r < rows.length; r++) {
      const row = rows[r];
      for (let c = 0; c < row.length; c++) {
        const char = row[c];
        if (char !== ' ' && char !== '.') {
          const color = colorPalette[char];
          if (color) {
            ctx.fillStyle = color;
            ctx.fillRect(startX + c * pixelSize, startY + r * pixelSize, pixelSize, pixelSize);
          }
        }
      }
    }
  }

  // --------------------------------------------------------------------------
  // PLAYER SPRITES
  // --------------------------------------------------------------------------
  public static getPlayerSprite(
    facing: Direction,
    state: 'idle' | 'walk' | 'attack1' | 'attack2' | 'attack3' | 'block' | 'roll' | 'hurt',
    frame: number = 0
  ): HTMLCanvasElement {
    const key = `player_${facing}_${state}_${frame}`;
    if (spriteCache.has(key)) return spriteCache.get(key)!;

    const size = 32; // 32x32 pixel canvas
    const p = 1; // 1:1 pixel size
    const canvas = createPixelCanvas(size, size, (ctx) => {
      // Colors
      const C = {
        H: '#2b2d42', // Helm dark iron
        S: '#8d99ae', // Steel plate
        L: '#edf2f4', // Steel highlight / blade shine
        V: '#00f5d4', // Visor glowing slit (cyan magic)
        G: '#d90429', // Cape scarlet
        D: '#7b1113', // Cape dark fold
        K: '#222222', // Leather belt / boots
        W: '#f8f9fa', // Sword blade
        Gld: '#ffb703', // Gold trim
        Sk: '#f1c0e8', // Skin tone (if exposed)
        Sh: 'rgba(0,0,0,0.3)', // Shadow underneath
      };

      // Draw drop shadow
      ctx.fillStyle = C.Sh;
      ctx.beginPath();
      ctx.ellipse(16, 28, 7, 3, 0, 0, Math.PI * 2);
      ctx.fill();

      // If rolling, draw tuck roll
      if (state === 'roll') {
        const rollAngle = (frame * Math.PI) / 2;
        ctx.save();
        ctx.translate(16, 18);
        ctx.rotate(rollAngle);
        // Compact armored ball
        ctx.fillStyle = C.G;
        ctx.fillRect(-7, -7, 14, 14);
        ctx.fillStyle = C.S;
        ctx.fillRect(-5, -5, 10, 10);
        ctx.fillStyle = C.L;
        ctx.fillRect(-2, -6, 4, 3);
        ctx.restore();
        return;
      }

      // Bobbing offset for walking
      const bob = state === 'walk' ? (frame % 2 === 0 ? -1 : 1) : 0;
      const legOffset = state === 'walk' ? (frame % 2 === 0 ? 1 : -1) : 0;

      // Body Y base
      const by = 8 + bob;

      // Cape (behind if facing down, visible if facing up/sides)
      if (facing !== 'up') {
        ctx.fillStyle = C.G;
        ctx.fillRect(11, by + 5, 10, 10);
        ctx.fillStyle = C.D;
        ctx.fillRect(11, by + 12, 10, 3);
      }

      // Torso Armor
      ctx.fillStyle = C.H;
      ctx.fillRect(12, by + 5, 8, 8);
      ctx.fillStyle = C.S;
      ctx.fillRect(13, by + 6, 6, 6);
      ctx.fillStyle = C.Gld;
      ctx.fillRect(15, by + 7, 2, 4); // Golden crest

      // Legs / Boots
      ctx.fillStyle = C.K;
      ctx.fillRect(12 + legOffset, by + 13, 3, 5);
      ctx.fillRect(17 - legOffset, by + 13, 3, 5);

      // Helm & Visor
      ctx.fillStyle = C.H;
      ctx.fillRect(11, by - 4, 10, 9);
      ctx.fillStyle = C.S;
      ctx.fillRect(12, by - 3, 8, 7);
      ctx.fillStyle = C.L;
      ctx.fillRect(12, by - 3, 3, 2); // Highlight

      // Visor Glow slit based on facing
      ctx.fillStyle = C.V;
      if (facing === 'down') {
        ctx.fillRect(13, by + 1, 6, 2);
      } else if (facing === 'left') {
        ctx.fillRect(11, by + 1, 3, 2);
      } else if (facing === 'right') {
        ctx.fillRect(18, by + 1, 3, 2);
      } else {
        // Back of helm - cape clasp
        ctx.fillStyle = C.Gld;
        ctx.fillRect(14, by + 3, 4, 2);
      }

      // Shield (Left arm)
      const shieldX = facing === 'left' ? 7 : facing === 'right' ? 19 : 8;
      const shieldY = by + 4;
      if (state === 'block') {
        // Raised shield in front!
        ctx.fillStyle = '#1e3a8a'; // Deep royal blue
        ctx.fillRect(10, by + 2, 12, 11);
        ctx.fillStyle = '#3b82f6';
        ctx.fillRect(12, by + 4, 8, 7);
        ctx.fillStyle = C.Gld;
        ctx.fillRect(15, by + 5, 2, 5);
        ctx.fillRect(13, by + 7, 6, 2);
      } else {
        // Resting shield
        ctx.fillStyle = '#1e3a8a';
        ctx.fillRect(shieldX, shieldY, 5, 8);
        ctx.fillStyle = C.Gld;
        ctx.fillRect(shieldX + 1, shieldY + 2, 3, 4);
      }

      // Sword (Right arm)
      if (state.startsWith('attack')) {
        // Sword slash pose
        const comboNum = state === 'attack1' ? 1 : state === 'attack2' ? 2 : 3;
        ctx.fillStyle = comboNum === 3 ? '#fbbf24' : C.W; // Golden glow for heavy finisher

        if (facing === 'right') {
          // Horizontal sword sweep
          ctx.fillRect(20, by + 3, 10, 3);
          ctx.fillRect(18, by + 2, 3, 5); // guard
          // Blade trail
          ctx.fillStyle = 'rgba(255,255,255,0.4)';
          ctx.fillRect(22, by - 2, 8, 4);
        } else if (facing === 'left') {
          ctx.fillRect(2, by + 3, 10, 3);
          ctx.fillRect(11, by + 2, 3, 5);
          ctx.fillStyle = 'rgba(255,255,255,0.4)';
          ctx.fillRect(2, by - 2, 8, 4);
        } else if (facing === 'up') {
          ctx.fillRect(18, by - 8, 3, 11);
          ctx.fillRect(16, by + 1, 7, 2);
        } else {
          // Downward strike
          ctx.fillRect(19, by + 8, 3, 11);
          ctx.fillRect(17, by + 6, 7, 2);
        }
      } else if (state !== 'block') {
        // Sheathed or held sword
        ctx.fillStyle = C.W;
        ctx.fillRect(21, by + 5, 2, 7);
        ctx.fillStyle = C.Gld;
        ctx.fillRect(20, by + 4, 4, 2);
      }

      // If hurt state: flash tint
      if (state === 'hurt') {
        ctx.fillStyle = 'rgba(239, 68, 68, 0.45)';
        ctx.fillRect(0, 0, size, size);
      }
    });

    spriteCache.set(key, canvas);
    return canvas;
  }

  // --------------------------------------------------------------------------
  // ENEMY SPRITES
  // --------------------------------------------------------------------------
  public static getEnemySprite(type: EnemyType, frame: number = 0, isHurt: boolean = false): HTMLCanvasElement {
    const key = `enemy_${type}_${frame}_${isHurt}`;
    if (spriteCache.has(key)) return spriteCache.get(key)!;

    const size = 32;
    const canvas = createPixelCanvas(size, size, (ctx) => {
      // Drop shadow
      ctx.fillStyle = 'rgba(0,0,0,0.3)';
      ctx.beginPath();
      ctx.ellipse(16, 27, 8, 3, 0, 0, Math.PI * 2);
      ctx.fill();

      const squish = Math.sin(frame * 0.4) * 2;

      if (type === 'slime' || type === 'acid_slime') {
        const isAcid = type === 'acid_slime';
        const primary = isAcid ? '#84cc16' : '#10b981';
        const dark = isAcid ? '#4d7c0f' : '#047857';
        const light = isAcid ? '#bef264' : '#6ee7b7';

        // Slime body
        ctx.fillStyle = dark;
        ctx.fillRect(8 - squish / 2, 14 + squish, 16 + squish, 12 - squish);
        ctx.fillStyle = primary;
        ctx.fillRect(9 - squish / 2, 15 + squish, 14 + squish, 10 - squish);
        ctx.fillStyle = light;
        ctx.fillRect(10 - squish / 2, 16 + squish, 5, 3); // shiny bubble

        // Eyes
        ctx.fillStyle = '#111827';
        ctx.fillRect(11, 19 + squish, 2, 3);
        ctx.fillRect(17, 19 + squish, 2, 3);
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(11, 19 + squish, 1, 1);
        ctx.fillRect(17, 19 + squish, 1, 1);
      } else if (type === 'skeleton') {
        // Skeleton warrior with scimitar & shield
        const bob = frame % 2 === 0 ? -1 : 1;
        // Bone colors
        const bone = '#f3f4f6';
        const darkBone = '#9ca3af';

        // Ribs & spine
        ctx.fillStyle = darkBone;
        ctx.fillRect(14, 14 + bob, 4, 7);
        ctx.fillStyle = bone;
        ctx.fillRect(12, 15 + bob, 8, 2);
        ctx.fillRect(13, 18 + bob, 6, 2);

        // Skull
        ctx.fillStyle = bone;
        ctx.fillRect(12, 7 + bob, 8, 7);
        // Eye sockets (red evil glow)
        ctx.fillStyle = '#dc2626';
        ctx.fillRect(13, 10 + bob, 2, 2);
        ctx.fillRect(17, 10 + bob, 2, 2);

        // Legs
        ctx.fillStyle = bone;
        ctx.fillRect(13, 21, 2, 6);
        ctx.fillRect(17, 21, 2, 6);

        // Scimitar blade
        ctx.fillStyle = '#e5e7eb';
        ctx.fillRect(22, 12 + bob, 2, 9);
        ctx.fillRect(23, 10 + bob, 2, 4);
      } else if (type === 'fire_imp') {
        // Horned volcanic imp
        const bob = Math.sin(frame * 0.3) * 2;
        ctx.fillStyle = '#7f1d1d';
        ctx.fillRect(10, 12 + bob, 12, 12);
        ctx.fillStyle = '#ea580c';
        ctx.fillRect(12, 14 + bob, 8, 8);
        ctx.fillStyle = '#fde047';
        ctx.fillRect(13, 16 + bob, 2, 2); // Glowing eyes
        ctx.fillRect(17, 16 + bob, 2, 2);
        // Horns
        ctx.fillStyle = '#1c1917';
        ctx.fillRect(9, 8 + bob, 3, 5);
        ctx.fillRect(20, 8 + bob, 3, 5);
      } else if (type === 'frost_wraith') {
        // Floating hooded phantom
        const float = Math.sin(frame * 0.3) * 3;
        ctx.fillStyle = '#1e3a8a';
        ctx.fillRect(10, 8 + float, 12, 16);
        ctx.fillStyle = '#38bdf8';
        ctx.fillRect(12, 10 + float, 8, 12);
        // Cyan glowing eye
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(14, 12 + float, 4, 2);
      } else if (type === 'magma_brute') {
        // Heavy volcanic golem
        ctx.fillStyle = '#292524';
        ctx.fillRect(8, 8, 16, 18);
        // Magma fissures
        ctx.fillStyle = '#f97316';
        ctx.fillRect(10, 11, 4, 2);
        ctx.fillRect(14, 15, 6, 2);
        ctx.fillRect(12, 19, 4, 3);
        ctx.fillStyle = '#facc15';
        ctx.fillRect(11, 10, 2, 2);
      }

      if (isHurt) {
        ctx.fillStyle = 'rgba(255, 255, 255, 0.7)';
        ctx.fillRect(0, 0, size, size);
      }
    });

    spriteCache.set(key, canvas);
    return canvas;
  }

  // --------------------------------------------------------------------------
  // BOSS SPRITES (Large high-detail 64x64 or 80x80 pixel canvases)
  // --------------------------------------------------------------------------
  public static getBossSprite(
    bossId: BossId,
    state: string,
    frame: number = 0,
    enraged: boolean = false
  ): HTMLCanvasElement {
    const key = `boss_${bossId}_${state}_${frame}_${enraged}`;
    if (spriteCache.has(key)) return spriteCache.get(key)!;

    const size = 80; // 80x80 canvas
    const canvas = createPixelCanvas(size, size, (ctx) => {
      // Big drop shadow
      ctx.fillStyle = 'rgba(0,0,0,0.45)';
      ctx.beginPath();
      ctx.ellipse(40, 68, 26, 9, 0, 0, Math.PI * 2);
      ctx.fill();

      const pulse = Math.sin(frame * 0.25) * 3;

      if (bossId === 'blight_colossus') {
        // Giant King Slime Colossus with crown of bones and glowing toxic heart
        const bodyColor = enraged ? '#7e22ce' : '#059669'; // Turns virulent purple when enraged
        const innerColor = enraged ? '#a855f7' : '#10b981';
        const coreColor = enraged ? '#f43f5e' : '#34d399';

        // Outer mass
        ctx.fillStyle = bodyColor;
        ctx.fillRect(16 - pulse / 2, 24 + pulse, 48 + pulse, 42 - pulse);

        // Inner translucent core
        ctx.fillStyle = innerColor;
        ctx.fillRect(20 - pulse / 2, 28 + pulse, 40 + pulse, 34 - pulse);

        // Pulsing toxic nucleus
        ctx.fillStyle = coreColor;
        ctx.fillRect(34, 40 + pulse, 12, 12);
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(38, 43 + pulse, 4, 4);

        // Massive sinister eyes
        ctx.fillStyle = '#0f172a';
        ctx.fillRect(26, 34 + pulse, 6, 8);
        ctx.fillRect(48, 34 + pulse, 6, 8);
        ctx.fillStyle = enraged ? '#ff0055' : '#fbbf24';
        ctx.fillRect(28, 36 + pulse, 3, 4);
        ctx.fillRect(50, 36 + pulse, 3, 4);

        // Crown of Ancient Bones / Spikes
        ctx.fillStyle = '#e2e8f0';
        ctx.fillRect(24, 16 + pulse, 6, 10);
        ctx.fillRect(36, 12 + pulse, 8, 14); // center spire
        ctx.fillRect(50, 16 + pulse, 6, 10);
      } else if (bossId === 'ashfang_wyrm') {
        // Molten Behemoth Wyrm
        const red = '#b91c1c';
        const magma = '#f97316';
        const coal = '#1c1917';

        // Heavy volcanic torso
        ctx.fillStyle = coal;
        ctx.fillRect(20, 20 + pulse, 40, 42);

        // Magma rib cracks
        ctx.fillStyle = magma;
        ctx.fillRect(24, 28 + pulse, 32, 4);
        ctx.fillRect(26, 36 + pulse, 28, 4);
        ctx.fillRect(28, 44 + pulse, 24, 4);

        // Horned Draconic Head
        ctx.fillStyle = red;
        ctx.fillRect(28, 12 + pulse, 24, 16);
        ctx.fillStyle = '#fde047';
        // Burning eyes
        ctx.fillRect(32, 16 + pulse, 4, 4);
        ctx.fillRect(44, 16 + pulse, 4, 4);

        // Giant curved horns
        ctx.fillStyle = coal;
        ctx.fillRect(18, 6 + pulse, 10, 8);
        ctx.fillRect(52, 6 + pulse, 10, 8);

        // Fiery aura if enraged
        if (enraged) {
          ctx.fillStyle = 'rgba(249, 115, 22, 0.35)';
          ctx.fillRect(12, 10, 56, 56);
        }
      } else if (bossId === 'frost_vael') {
        // Cryomancer Lord Vael - Arch-Lich of Glaciers
        const ice = '#38bdf8';
        const deepIce = '#0369a1';
        const frost = '#e0f2fe';

        // Floating frost aura
        const floatY = Math.sin(frame * 0.2) * 5;

        // Robes
        ctx.fillStyle = deepIce;
        ctx.fillRect(24, 28 + floatY, 32, 38);
        ctx.fillStyle = ice;
        ctx.fillRect(28, 32 + floatY, 24, 30);

        // Crystalline Wings / Spikes
        ctx.fillStyle = frost;
        ctx.fillRect(10, 20 + floatY, 14, 24);
        ctx.fillRect(56, 20 + floatY, 14, 24);

        // Mask / Crown of Glaciers
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(32, 14 + floatY, 16, 16);
        ctx.fillStyle = '#0284c7';
        ctx.fillRect(34, 18 + floatY, 4, 4);
        ctx.fillRect(42, 18 + floatY, 4, 4);

        // Frost staff
        ctx.fillStyle = '#67e8f9';
        ctx.fillRect(66, 10 + floatY, 4, 52);
        ctx.fillRect(62, 8 + floatY, 12, 6);
      } else if (bossId === 'malakor_titan') {
        // The Shattered Titan King
        const gold = '#eab308';
        const darkSteel = '#0f172a';
        const voidPurple = enraged ? '#d946ef' : '#6366f1';

        // Colossal Armored Shoulders
        ctx.fillStyle = darkSteel;
        ctx.fillRect(14, 20 + pulse, 52, 44);
        ctx.fillStyle = gold;
        ctx.fillRect(12, 18 + pulse, 14, 12);
        ctx.fillRect(54, 18 + pulse, 14, 12);

        // Royal Crest Breastplate
        ctx.fillStyle = voidPurple;
        ctx.fillRect(28, 28 + pulse, 24, 26);
        ctx.fillStyle = gold;
        ctx.fillRect(38, 30 + pulse, 4, 20);
        ctx.fillRect(32, 38 + pulse, 16, 4);

        // Titan Crown Helm
        ctx.fillStyle = darkSteel;
        ctx.fillRect(28, 6 + pulse, 24, 16);
        ctx.fillStyle = gold;
        ctx.fillRect(26, 2 + pulse, 28, 6);
        // Visor glare
        ctx.fillStyle = enraged ? '#ff0055' : '#38bdf8';
        ctx.fillRect(32, 12 + pulse, 16, 4);

        // Colossal Greatsword
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(68, 6 + pulse, 6, 56);
        ctx.fillStyle = gold;
        ctx.fillRect(64, 48 + pulse, 14, 6);
      }
    });

    spriteCache.set(key, canvas);
    return canvas;
  }

  // --------------------------------------------------------------------------
  // WORLD TILES (32x32 pixel tiles)
  // --------------------------------------------------------------------------
  public static getTileCanvas(type: number, biome: BiomeType, variant: number): HTMLCanvasElement {
    const key = `tile_${type}_${biome}_${variant}`;
    if (spriteCache.has(key)) return spriteCache.get(key)!;

    const size = 32;
    const canvas = createPixelCanvas(size, size, (ctx) => {
      // 0: grass, 1: path, 2: water, 3: wall/cliff, 4: swamp, 5: lava, 6: ice, 7: ruin_floor, 8: bridge

      if (type === 0) {
        // Grass / Ground by biome
        if (biome === 'verdant') {
          ctx.fillStyle = '#2d6a4f';
          ctx.fillRect(0, 0, size, size);
          // Grass texture blades
          ctx.fillStyle = '#40916c';
          ctx.fillRect(4, 6, 2, 4);
          ctx.fillRect(18, 14, 2, 4);
          ctx.fillRect(10, 24, 2, 4);
          if (variant === 1) {
            // Little wildflower
            ctx.fillStyle = '#fbbf24';
            ctx.fillRect(14, 10, 3, 3);
          } else if (variant === 2) {
            ctx.fillStyle = '#f472b6';
            ctx.fillRect(8, 20, 3, 3);
          }
        } else if (biome === 'marsh') {
          ctx.fillStyle = '#1e293b';
          ctx.fillRect(0, 0, size, size);
          ctx.fillStyle = '#334155';
          ctx.fillRect(6, 6, 4, 3);
          ctx.fillRect(20, 18, 4, 3);
          if (variant === 1) {
            // Toxic toadstool
            ctx.fillStyle = '#a855f7';
            ctx.fillRect(12, 12, 4, 3);
          }
        } else if (biome === 'caldera') {
          ctx.fillStyle = '#292524';
          ctx.fillRect(0, 0, size, size);
          ctx.fillStyle = '#44403c';
          ctx.fillRect(8, 10, 6, 4);
          if (variant === 1) {
            // Glowing ember crack
            ctx.fillStyle = '#ea580c';
            ctx.fillRect(14, 14, 4, 2);
          }
        } else if (biome === 'frost') {
          ctx.fillStyle = '#e2e8f0';
          ctx.fillRect(0, 0, size, size);
          ctx.fillStyle = '#cbd5e1';
          ctx.fillRect(4, 8, 6, 3);
          ctx.fillRect(18, 20, 6, 3);
          if (variant === 1) {
            ctx.fillStyle = '#93c5fd';
            ctx.fillRect(10, 14, 3, 3);
          }
        } else {
          // Sanctum / Titan Core
          ctx.fillStyle = '#0f172a';
          ctx.fillRect(0, 0, size, size);
          ctx.fillStyle = '#1e1b4b';
          ctx.fillRect(2, 2, 28, 28);
          ctx.fillStyle = '#6366f1';
          ctx.fillRect(15, 15, 2, 2);
        }
      } else if (type === 1) {
        // Path / Dirt road
        ctx.fillStyle = '#78350f';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#92400e';
        ctx.fillRect(4, 4, 24, 24);
        ctx.fillStyle = '#b45309';
        ctx.fillRect(8, 10, 4, 3);
        ctx.fillRect(18, 18, 4, 3);
      } else if (type === 2) {
        // Water
        ctx.fillStyle = '#0284c7';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#38bdf8';
        ctx.fillRect(4, 6, 12, 2);
        ctx.fillRect(14, 18, 14, 2);
      } else if (type === 3) {
        // Cliff / Wall
        ctx.fillStyle = '#1c1917';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#44403c';
        ctx.fillRect(2, 2, 28, 26);
        ctx.fillStyle = '#57534e';
        ctx.fillRect(4, 4, 12, 10);
        ctx.fillRect(18, 12, 10, 12);
      } else if (type === 4) {
        // Swamp Deep Murk
        ctx.fillStyle = '#064e3b';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#047857';
        ctx.fillRect(4, 8, 8, 3);
        ctx.fillStyle = '#10b981';
        ctx.fillRect(16, 16, 4, 4); // toxic bubble
      } else if (type === 5) {
        // Lava
        ctx.fillStyle = '#991b1b';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#ea580c';
        ctx.fillRect(4, 6, 14, 6);
        ctx.fillStyle = '#fde047';
        ctx.fillRect(8, 8, 6, 2); // yellow molten heat
      } else if (type === 6) {
        // Ice sheet
        ctx.fillStyle = '#93c5fd';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#bfdbfe';
        ctx.fillRect(4, 4, 24, 24);
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(8, 8, 14, 2);
      } else if (type === 7) {
        // Ruin Floor (Ancient Flagstones)
        ctx.fillStyle = '#334155';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#475569';
        ctx.fillRect(2, 2, 13, 13);
        ctx.fillRect(17, 2, 13, 13);
        ctx.fillRect(2, 17, 13, 13);
        ctx.fillRect(17, 17, 13, 13);
      } else if (type === 8) {
        // Wooden Bridge
        ctx.fillStyle = '#451a03';
        ctx.fillRect(0, 0, size, size);
        ctx.fillStyle = '#78350f';
        ctx.fillRect(2, 2, 28, 6);
        ctx.fillRect(2, 10, 28, 6);
        ctx.fillRect(2, 18, 28, 6);
        ctx.fillRect(2, 26, 28, 6);
      }
    });

    spriteCache.set(key, canvas);
    return canvas;
  }

  // --------------------------------------------------------------------------
  // INTERACTABLE SPRITES (Shrine, Chest, Trees)
  // --------------------------------------------------------------------------
  public static getShrineSprite(active: boolean, frame: number = 0): HTMLCanvasElement {
    const key = `shrine_${active}_${frame % 4}`;
    if (spriteCache.has(key)) return spriteCache.get(key)!;

    const size = 48;
    const canvas = createPixelCanvas(size, size, (ctx) => {
      // Stone Dais
      ctx.fillStyle = '#1e293b';
      ctx.fillRect(8, 28, 32, 14);
      ctx.fillStyle = '#334155';
      ctx.fillRect(12, 26, 24, 6);

      // Embedded Sword
      ctx.fillStyle = '#e2e8f0';
      ctx.fillRect(23, 10, 2, 18);
      // Crossguard
      ctx.fillStyle = '#f59e0b';
      ctx.fillRect(20, 14, 8, 2);

      // Flame / Runic glow
      if (active) {
        const glow = Math.sin(frame * 0.4) * 2;
        ctx.fillStyle = '#38bdf8';
        ctx.fillRect(21, 6 + glow, 6, 8);
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(23, 8 + glow, 2, 4);
      } else {
        ctx.fillStyle = '#64748b';
        ctx.fillRect(22, 8, 4, 4);
      }
    });

    spriteCache.set(key, canvas);
    return canvas;
  }

  public static getChestSprite(opened: boolean): HTMLCanvasElement {
    const key = `chest_${opened}`;
    if (spriteCache.has(key)) return spriteCache.get(key)!;

    const size = 32;
    const canvas = createPixelCanvas(size, size, (ctx) => {
      ctx.fillStyle = 'rgba(0,0,0,0.3)';
      ctx.beginPath();
      ctx.ellipse(16, 26, 9, 4, 0, 0, Math.PI * 2);
      ctx.fill();

      // Chest body
      ctx.fillStyle = '#78350f';
      ctx.fillRect(6, 14, 20, 12);
      ctx.fillStyle = '#f59e0b';
      ctx.fillRect(5, 13, 22, 2); // Gold trim
      ctx.fillRect(14, 16, 4, 4); // Lock plate

      if (opened) {
        // Open lid tilted back
        ctx.fillStyle = '#451a03';
        ctx.fillRect(6, 7, 20, 7);
        // Shiny loot shimmer inside
        ctx.fillStyle = '#fef08a';
        ctx.fillRect(10, 14, 12, 3);
      } else {
        // Closed lid
        ctx.fillStyle = '#92400e';
        ctx.fillRect(6, 8, 20, 7);
        ctx.fillStyle = '#f59e0b';
        ctx.fillRect(15, 12, 2, 4);
      }
    });

    spriteCache.set(key, canvas);
    return canvas;
  }

  public static getTreeSprite(biome: BiomeType): HTMLCanvasElement {
    const key = `tree_${biome}`;
    if (spriteCache.has(key)) return spriteCache.get(key)!;

    const size = 64;
    const canvas = createPixelCanvas(size, size, (ctx) => {
      // Trunk
      ctx.fillStyle = '#451a03';
      ctx.fillRect(28, 38, 8, 22);

      let foliageMain = '#15803d';
      let foliageLight = '#22c55e';
      let foliageDark = '#14532d';

      if (biome === 'marsh') {
        foliageMain = '#1e3a5f';
        foliageLight = '#2d5a7b';
        foliageDark = '#0f172a';
      } else if (biome === 'caldera') {
        // Dead charred tree
        ctx.fillStyle = '#1c1917';
        ctx.fillRect(28, 20, 8, 40);
        ctx.fillRect(20, 26, 10, 4);
        ctx.fillRect(34, 32, 12, 4);
        return;
      } else if (biome === 'frost') {
        foliageMain = '#cbd5e1';
        foliageLight = '#f8fafc';
        foliageDark = '#64748b';
      }

      // Canopy layers (round pine/oak canopy)
      ctx.fillStyle = foliageDark;
      ctx.beginPath();
      ctx.arc(32, 28, 22, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = foliageMain;
      ctx.beginPath();
      ctx.arc(32, 25, 18, 0, Math.PI * 2);
      ctx.fill();

      ctx.fillStyle = foliageLight;
      ctx.beginPath();
      ctx.arc(30, 21, 11, 0, Math.PI * 2);
      ctx.fill();
    });

    spriteCache.set(key, canvas);
    return canvas;
  }
}
