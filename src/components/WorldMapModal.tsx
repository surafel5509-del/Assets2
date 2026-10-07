/**
 * Fullscreen Interactive World Map of Aethelgard with Fast Travel
 */

import React, { useEffect, useRef } from 'react';
import { GameEngine } from '../game/engine';
import { X, Navigation, Compass, Flame, ShieldAlert, Sparkles } from 'lucide-react';

interface WorldMapModalProps {
  engine: GameEngine;
  onClose: () => void;
}

export const WorldMapModal: React.FC<WorldMapModalProps> = ({ engine, onClose }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const { worldMap, player, bosses } = engine;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const width = 360;
    const height = 360;
    canvas.width = width;
    canvas.height = height;

    // Draw minimap background
    ctx.fillStyle = '#0a0a0c';
    ctx.fillRect(0, 0, width, height);

    // Render world tiles scaled down
    const scale = width / worldMap.width; // 360 / 120 = 3px per tile

    for (let y = 0; y < worldMap.height; y++) {
      for (let x = 0; x < worldMap.width; x++) {
        const tile = worldMap.tiles[y][x];
        let color = '#15803d'; // verdant

        if (tile.biome === 'marsh') color = '#1e293b';
        else if (tile.biome === 'caldera') color = '#44403c';
        else if (tile.biome === 'frost') color = '#93c5fd';
        else if (tile.biome === 'sanctum') color = '#312e81';

        if (tile.solid) {
          if (tile.type === 5) color = '#dc2626'; // lava
          else if (tile.type === 4) color = '#064e3b'; // swamp water
          else color = '#0f172a'; // cliff
        } else if (tile.type === 1) {
          color = '#78350f'; // path
        } else if (tile.type === 8) {
          color = '#b45309'; // bridge
        }

        ctx.fillStyle = color;
        ctx.fillRect(x * scale, y * scale, Math.ceil(scale), Math.ceil(scale));
      }
    }

    // Draw Boss Arenas
    for (const boss of bosses) {
      const bx = (boss.x / (worldMap.width * 32)) * width;
      const by = (boss.y / (worldMap.height * 32)) * height;
      const isDefeated = player.defeatedBosses.includes(boss.id);

      ctx.save();
      ctx.strokeStyle = isDefeated ? '#22c55e' : '#ef4444';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(bx, by, 10, 0, Math.PI * 2);
      ctx.stroke();

      ctx.fillStyle = isDefeated ? '#22c55e' : '#ef4444';
      ctx.font = '8px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(isDefeated ? '✓' : '💀', bx, by + 3);
      ctx.restore();
    }

    // Draw Shrines
    for (const shrine of worldMap.shrines) {
      const sx = (shrine.x / (worldMap.width * 32)) * width;
      const sy = (shrine.y / (worldMap.height * 32)) * height;

      ctx.fillStyle = shrine.discovered ? '#38bdf8' : '#64748b';
      ctx.beginPath();
      ctx.arc(sx, sy, 4, 0, Math.PI * 2);
      ctx.fill();

      if (shrine.discovered) {
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 1;
        ctx.stroke();
      }
    }

    // Draw Player Location marker
    const px = (player.x / (worldMap.width * 32)) * width;
    const py = (player.y / (worldMap.height * 32)) * height;

    ctx.save();
    ctx.fillStyle = '#facc15';
    ctx.shadowColor = '#facc15';
    ctx.shadowBlur = 8;
    ctx.beginPath();
    ctx.arc(px, py, 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }, [worldMap, player, bosses]);

  const handleFastTravel = (shrineId: string) => {
    engine.fastTravelToShrine(shrineId);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-4">
      <div className="bg-neutral-950 border border-neutral-800 rounded-2xl max-w-4xl w-full p-6 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex justify-between items-center border-b border-neutral-800 pb-3 mb-4">
          <div className="flex items-center gap-2">
            <Compass className="text-amber-400" size={24} />
            <h2 className="font-cinzel text-xl font-bold text-neutral-100 tracking-wider">
              Map of Aethelgard
            </h2>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            <X size={20} />
          </button>
        </div>

        {/* Content Body */}
        <div className="grid grid-cols-1 md:grid-cols-12 gap-6 overflow-y-auto">
          {/* Tactical Map Canvas */}
          <div className="md:col-span-7 flex flex-col items-center justify-center bg-neutral-900/60 p-4 rounded-xl border border-neutral-800">
            <canvas
              ref={canvasRef}
              className="rounded-lg shadow-xl border border-neutral-800 pixelated max-w-full"
            />
            <div className="flex flex-wrap gap-4 mt-3 text-xs text-neutral-400 justify-center">
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-amber-400" /> Player
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-sky-400" /> Discovered Shrine
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-red-500" /> Boss Arena
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" /> Defeated Titan
              </span>
            </div>
          </div>

          {/* Shrine Fast Travel & Titan Seals */}
          <div className="md:col-span-5 flex flex-col gap-4">
            {/* Titan Rune Seals Status */}
            <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4">
              <h3 className="font-cinzel text-xs font-bold text-amber-300 uppercase tracking-wider mb-2.5 flex items-center gap-1.5">
                <ShieldAlert size={16} /> Sanctum Seals (Titan Runes)
              </h3>
              <div className="space-y-2 text-xs">
                <div className="flex justify-between items-center bg-neutral-950/60 p-2 rounded border border-neutral-800/80">
                  <span className="text-neutral-300">Fen Rune (Blight Colossus)</span>
                  <span className={player.unlockedRunes.marsh ? 'text-emerald-400 font-bold' : 'text-neutral-500'}>
                    {player.unlockedRunes.marsh ? 'UNSEALED' : 'LOCKED'}
                  </span>
                </div>
                <div className="flex justify-between items-center bg-neutral-950/60 p-2 rounded border border-neutral-800/80">
                  <span className="text-neutral-300">Caldera Rune (Ashfang Wyrm)</span>
                  <span className={player.unlockedRunes.caldera ? 'text-emerald-400 font-bold' : 'text-neutral-500'}>
                    {player.unlockedRunes.caldera ? 'UNSEALED' : 'LOCKED'}
                  </span>
                </div>
                <div className="flex justify-between items-center bg-neutral-950/60 p-2 rounded border border-neutral-800/80">
                  <span className="text-neutral-300">Glacial Rune (Cryomancer Vael)</span>
                  <span className={player.unlockedRunes.frost ? 'text-emerald-400 font-bold' : 'text-neutral-500'}>
                    {player.unlockedRunes.frost ? 'UNSEALED' : 'LOCKED'}
                  </span>
                </div>
              </div>
            </div>

            {/* Fast Travel Shrines */}
            <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 flex-1">
              <h3 className="font-cinzel text-xs font-bold text-sky-300 uppercase tracking-wider mb-2.5 flex items-center gap-1.5">
                <Flame size={16} /> Whisperstone Shrines (Fast Travel)
              </h3>
              <div className="space-y-2 overflow-y-auto max-h-56 pr-1">
                {worldMap.shrines.map((shrine) => (
                  <div
                    key={shrine.id}
                    className="flex justify-between items-center p-2.5 bg-neutral-950/70 border border-neutral-800 rounded-lg text-xs"
                  >
                    <div>
                      <div className="font-semibold text-neutral-200">
                        {shrine.name}
                      </div>
                      <div className="text-[10px] text-neutral-500 italic">
                        {shrine.discovered ? shrine.description : 'Undiscovered Shrine'}
                      </div>
                    </div>
                    {shrine.discovered ? (
                      <button
                        onClick={() => handleFastTravel(shrine.id)}
                        className="bg-sky-600 hover:bg-sky-500 text-white font-medium px-2.5 py-1 rounded transition-colors text-xs flex items-center gap-1"
                      >
                        <Navigation size={12} /> Travel
                      </button>
                    ) : (
                      <span className="text-[10px] text-neutral-600">Locked</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
