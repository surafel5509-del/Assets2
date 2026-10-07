/**
 * Victory Screen: The Shattered Titan King Has Fallen
 */

import React from 'react';
import { GameEngine } from '../game/engine';
import { Trophy, Sparkles, CheckCircle2 } from 'lucide-react';

interface VictoryModalProps {
  engine: GameEngine;
  onContinue: () => void;
}

export const VictoryModal: React.FC<VictoryModalProps> = ({ engine, onContinue }) => {
  const { player } = engine;

  return (
    <div className="fixed inset-0 z-50 bg-black/90 backdrop-blur-xl flex items-center justify-center p-4 select-none">
      <div className="bg-neutral-950 border border-amber-500/60 rounded-2xl max-w-lg w-full p-8 shadow-[0_0_50px_rgba(245,158,11,0.25)] text-center relative overflow-hidden">
        {/* Glow halo */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-64 h-32 bg-amber-500/15 blur-3xl rounded-full pointer-events-none" />

        <div className="flex justify-center mb-4">
          <div className="w-20 h-20 rounded-full bg-neutral-900 border border-amber-400 flex items-center justify-center shadow-2xl">
            <Trophy className="text-amber-400" size={40} />
          </div>
        </div>

        <h1 className="font-cinzel text-3xl font-extrabold text-amber-200 tracking-wider mb-2">
          TITAN VANQUISHED
        </h1>
        <p className="text-neutral-400 text-xs font-cinzel tracking-widest uppercase mb-6">
          The Curse of Aethelgard Has Broken
        </p>

        {/* Victory Stats */}
        <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 mb-8 space-y-2 text-xs">
          <div className="flex justify-between text-neutral-300">
            <span>Hero Rank</span>
            <span className="font-bold text-amber-300">Level {player.stats.level} Vanguard</span>
          </div>
          <div className="flex justify-between text-neutral-300">
            <span>Titans Slain</span>
            <span className="font-bold text-emerald-400">4 / 4 Titans Defeated</span>
          </div>
          <div className="flex justify-between text-neutral-300">
            <span>Titan Embers Amassed</span>
            <span className="font-bold text-amber-400">✦ {player.stats.embers}</span>
          </div>
          <div className="flex justify-between text-neutral-300">
            <span>Legendary Cleaver</span>
            <span className="font-bold text-sky-400">Claimed & Mastered</span>
          </div>
        </div>

        <button
          onClick={onContinue}
          className="bg-amber-500 hover:bg-amber-400 text-neutral-950 font-bold py-3 px-8 rounded-xl text-xs uppercase tracking-wider transition-colors shadow-xl w-full"
        >
          Continue Exploring Open World
        </button>
      </div>
    </div>
  );
};
