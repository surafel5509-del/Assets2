/**
 * Whisperstone Shrine (Bonfire) Rest Screen
 */

import React from 'react';
import { GameEngine } from '../game/engine';
import { Flame, MapPin, Sparkles, LogOut } from 'lucide-react';

interface ShrineModalProps {
  engine: GameEngine;
  onOpenInventory: () => void;
  onOpenMap: () => void;
  onClose: () => void;
}

export const ShrineModal: React.FC<ShrineModalProps> = ({
  engine,
  onOpenInventory,
  onOpenMap,
  onClose,
}) => {
  const currentShrine = engine.worldMap.shrines.find(
    (s) => s.id === engine.player.activeShrineId
  ) || engine.worldMap.shrines[0];

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 select-none">
      <div className="bg-neutral-950 border border-amber-900/50 rounded-2xl max-w-lg w-full p-8 shadow-2xl text-center relative overflow-hidden">
        {/* Glowing flame aura */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-48 h-24 bg-amber-500/10 blur-2xl rounded-full pointer-events-none" />

        <div className="flex justify-center mb-4">
          <div className="w-16 h-16 rounded-full bg-neutral-900 border border-amber-500/40 flex items-center justify-center shadow-[0_0_20px_rgba(245,158,11,0.25)]">
            <Flame className="text-amber-400 animate-pulse" size={32} />
          </div>
        </div>

        <h2 className="font-cinzel text-2xl font-bold text-neutral-100 tracking-wider mb-1">
          {currentShrine.name}
        </h2>
        <p className="text-xs text-amber-300 font-mono mb-6">
          Whisperstone Kindled · Health & Flasks Restored
        </p>

        <div className="space-y-3 max-w-xs mx-auto mb-8">
          <button
            onClick={onOpenInventory}
            className="w-full bg-neutral-900 hover:bg-neutral-800 text-neutral-200 border border-neutral-700 hover:border-amber-500/50 py-2.5 px-4 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 transition-all"
          >
            <Sparkles size={16} className="text-amber-400" />
            Level Up & Equipment
          </button>

          <button
            onClick={onOpenMap}
            className="w-full bg-neutral-900 hover:bg-neutral-800 text-neutral-200 border border-neutral-700 hover:border-sky-500/50 py-2.5 px-4 rounded-xl text-xs font-semibold flex items-center justify-center gap-2 transition-all"
          >
            <MapPin size={16} className="text-sky-400" />
            Fast Travel via World Map
          </button>
        </div>

        <button
          onClick={onClose}
          className="bg-amber-600 hover:bg-amber-500 text-neutral-950 font-bold py-2.5 px-8 rounded-xl text-xs uppercase tracking-wider transition-colors shadow-lg"
        >
          Resume Journey
        </button>
      </div>
    </div>
  );
};
