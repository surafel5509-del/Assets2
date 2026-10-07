/**
 * Virtual On-Screen Touch Controls for Mobile and Tablet Play
 */

import React from 'react';
import { GameEngine } from '../game/engine';

interface TouchControlsProps {
  engine: GameEngine;
}

export const TouchControls: React.FC<TouchControlsProps> = ({ engine }) => {
  // Only show or active on touch screens / small screens
  return (
    <div className="absolute inset-x-0 bottom-4 pointer-events-none flex justify-between px-6 z-30 select-none md:hidden">
      {/* D-PAD (Left Side) */}
      <div className="pointer-events-auto grid grid-cols-3 gap-1.5 w-36 h-36 bg-neutral-900/40 backdrop-blur-sm p-1.5 rounded-full border border-neutral-800/80">
        <div />
        <button
          onTouchStart={(e) => { e.preventDefault(); engine.keys.up = true; }}
          onTouchEnd={(e) => { e.preventDefault(); engine.keys.up = false; }}
          className="bg-neutral-800/90 active:bg-neutral-700 rounded-lg flex items-center justify-center text-neutral-300 font-bold text-xs"
        >
          ▲
        </button>
        <div />

        <button
          onTouchStart={(e) => { e.preventDefault(); engine.keys.left = true; }}
          onTouchEnd={(e) => { e.preventDefault(); engine.keys.left = false; }}
          className="bg-neutral-800/90 active:bg-neutral-700 rounded-lg flex items-center justify-center text-neutral-300 font-bold text-xs"
        >
          ◀
        </button>
        <div className="bg-neutral-950/60 rounded-lg" />
        <button
          onTouchStart={(e) => { e.preventDefault(); engine.keys.right = true; }}
          onTouchEnd={(e) => { e.preventDefault(); engine.keys.right = false; }}
          className="bg-neutral-800/90 active:bg-neutral-700 rounded-lg flex items-center justify-center text-neutral-300 font-bold text-xs"
        >
          ▶
        </button>

        <div />
        <button
          onTouchStart={(e) => { e.preventDefault(); engine.keys.down = true; }}
          onTouchEnd={(e) => { e.preventDefault(); engine.keys.down = false; }}
          className="bg-neutral-800/90 active:bg-neutral-700 rounded-lg flex items-center justify-center text-neutral-300 font-bold text-xs"
        >
          ▼
        </button>
        <div />
      </div>

      {/* ACTION CLUSTER (Right Side) */}
      <div className="pointer-events-auto flex flex-col items-end gap-2">
        <div className="flex gap-2">
          {/* Flask */}
          <button
            onTouchStart={(e) => { e.preventDefault(); engine.triggerFlask(); }}
            className="w-11 h-11 bg-neutral-900/90 active:bg-neutral-800 border border-amber-600/60 rounded-full flex items-center justify-center text-sm shadow-lg text-amber-300"
          >
            🧪
          </button>
          {/* Interact */}
          <button
            onTouchStart={(e) => { e.preventDefault(); engine.triggerInteract(); }}
            className="w-11 h-11 bg-neutral-900/90 active:bg-neutral-800 border border-sky-600/60 rounded-full flex items-center justify-center text-xs font-bold shadow-lg text-sky-300"
          >
            E
          </button>
        </div>

        <div className="flex items-center gap-2">
          {/* Block */}
          <button
            onTouchStart={(e) => { e.preventDefault(); engine.triggerBlock(true); }}
            onTouchEnd={(e) => { e.preventDefault(); engine.triggerBlock(false); }}
            className="w-12 h-12 bg-neutral-900/90 active:bg-neutral-800 border border-blue-600/60 rounded-full flex items-center justify-center text-lg shadow-lg"
          >
            🛡️
          </button>

          {/* Roll */}
          <button
            onTouchStart={(e) => { e.preventDefault(); engine.triggerRoll(); }}
            className="w-12 h-12 bg-neutral-900/90 active:bg-neutral-800 border border-neutral-600 rounded-full flex items-center justify-center text-lg shadow-lg"
          >
            💨
          </button>

          {/* Attack */}
          <button
            onTouchStart={(e) => { e.preventDefault(); engine.triggerAttack(); }}
            className="w-14 h-14 bg-red-950/90 active:bg-red-900 border border-red-600 rounded-full flex items-center justify-center text-xl shadow-lg"
          >
            ⚔️
          </button>
        </div>
      </div>
    </div>
  );
};
