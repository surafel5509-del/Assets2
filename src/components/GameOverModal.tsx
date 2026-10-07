/**
 * Game Over (You Died) Souls-like Cinematic Overlay
 */

import React from 'react';
import { GameEngine } from '../game/engine';
import { RotateCcw } from 'lucide-react';

interface GameOverModalProps {
  engine: GameEngine;
}

export const GameOverModal: React.FC<GameOverModalProps> = ({ engine }) => {
  const handleRespawn = () => {
    engine.respawn();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/90 backdrop-blur-lg flex flex-col items-center justify-center p-4 select-none animate-fade-in">
      <div className="text-center max-w-md w-full">
        <h1 className="font-cinzel text-5xl md:text-7xl font-extrabold text-red-600 tracking-[0.25em] mb-4 shadow-red-950 drop-shadow-[0_4px_12px_rgba(220,38,38,0.5)]">
          YOU DIED
        </h1>
        <p className="text-neutral-400 text-sm font-cinzel tracking-widest uppercase mb-10">
          The Titans claim another vanguard
        </p>

        <button
          onClick={handleRespawn}
          className="bg-neutral-900 hover:bg-neutral-800 text-neutral-200 border border-neutral-700 hover:border-amber-500/60 font-cinzel text-sm px-6 py-3 rounded-xl transition-all shadow-xl flex items-center gap-2 mx-auto"
        >
          <RotateCcw size={16} /> Respawn at Last Whisperstone
        </button>
      </div>
    </div>
  );
};
