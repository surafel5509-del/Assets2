/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useReducer, useRef, useState } from 'react';
import { GameEngine } from './game/engine';
import { soundEngine } from './game/audio';
import { GameCanvas } from './components/GameCanvas';
import { HUD } from './components/HUD';
import { WorldMapModal } from './components/WorldMapModal';
import { InventoryModal } from './components/InventoryModal';
import { ShrineModal } from './components/ShrineModal';
import { GameOverModal } from './components/GameOverModal';
import { VictoryModal } from './components/VictoryModal';
import { ControlsGuideModal } from './components/ControlsGuideModal';
import { TouchControls } from './components/TouchControls';
import { Swords, Shield, Compass, Sparkles, Volume2, VolumeX, HelpCircle, Play } from 'lucide-react';

export default function App() {
  const engineRef = useRef<GameEngine | null>(null);
  const [, forceUpdate] = useReducer((x) => x + 1, 0);

  const [hasStarted, setHasStarted] = useState<boolean>(false);
  const [showMap, setShowMap] = useState<boolean>(false);
  const [showInventory, setShowInventory] = useState<boolean>(false);
  const [showHelp, setShowHelp] = useState<boolean>(false);
  const [isMuted, setIsMuted] = useState<boolean>(false);

  // Initialize engine once
  if (!engineRef.current) {
    const engine = new GameEngine();
    engine.setOnStateChange(() => {
      forceUpdate();
    });
    engineRef.current = engine;
  }

  const engine = engineRef.current;

  const handleStartGame = () => {
    setHasStarted(true);
    soundEngine.playMusic('explore');
  };

  const handleMuteToggle = () => {
    const muted = soundEngine.toggleMute();
    setIsMuted(muted);
  };

  return (
    <div className="relative w-screen h-screen overflow-hidden bg-neutral-950 font-sans select-none">
      {/* TITLE SCREEN / MAIN MENU */}
      {!hasStarted && (
        <div className="absolute inset-0 z-50 bg-[#0a0a0c] flex flex-col items-center justify-center p-6 text-center select-none">
          {/* Subtle atmospheric background effect */}
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_center,rgba(99,102,241,0.08)_0,transparent_70%)] pointer-events-none" />
          <div className="absolute inset-0 opacity-15 bg-[radial-gradient(#38bdf8_1px,transparent_1px)] [background-size:24px_24px] pointer-events-none" />

          <div className="relative z-10 max-w-2xl w-full flex flex-col items-center">
            {/* Title Badge */}
            <div className="flex items-center gap-2 text-xs font-mono text-amber-400 mb-4 tracking-widest uppercase">
              <span>✦ Open World 2D Action RPG ✦</span>
            </div>

            {/* Game Title */}
            <h1 className="font-cinzel text-4xl sm:text-6xl font-black text-transparent bg-clip-text bg-gradient-to-b from-amber-100 via-amber-200 to-amber-500 tracking-wider mb-3 drop-shadow-[0_4px_16px_rgba(245,158,11,0.3)]">
              AETHELGARD
            </h1>
            <p className="font-cinzel text-sm sm:text-base text-neutral-400 tracking-[0.2em] uppercase mb-8">
              Chronicles of the Shattered Titans
            </p>

            {/* Feature Highlights */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 w-full max-w-xl mb-10 text-xs">
              <div className="bg-neutral-900/80 border border-neutral-800/80 p-3 rounded-xl flex flex-col items-center">
                <Compass className="text-sky-400 mb-1.5" size={20} />
                <span className="font-semibold text-neutral-200">5 Seamless Biomes</span>
                <span className="text-[10px] text-neutral-500 mt-0.5">Glades, Fens, Calderas & Peaks</span>
              </div>
              <div className="bg-neutral-900/80 border border-neutral-800/80 p-3 rounded-xl flex flex-col items-center">
                <Shield className="text-amber-400 mb-1.5" size={20} />
                <span className="font-semibold text-neutral-200">Real-Time Parry & Roll</span>
                <span className="text-[10px] text-neutral-500 mt-0.5">Souls-like i-frames & ripostes</span>
              </div>
              <div className="bg-neutral-900/80 border border-neutral-800/80 p-3 rounded-xl flex flex-col items-center">
                <Swords className="text-red-400 mb-1.5" size={20} />
                <span className="font-semibold text-neutral-200">4 Colossal Bosses</span>
                <span className="text-[10px] text-neutral-500 mt-0.5">Multi-phase telegraphed combat</span>
              </div>
            </div>

            {/* Main Action Buttons */}
            <div className="flex flex-col sm:flex-row gap-3 w-full max-w-sm mb-6">
              <button
                onClick={handleStartGame}
                className="flex-1 bg-gradient-to-r from-amber-500 to-amber-600 hover:from-amber-400 hover:to-amber-500 text-neutral-950 font-bold py-3.5 px-6 rounded-xl text-xs uppercase tracking-widest transition-all shadow-[0_0_24px_rgba(245,158,11,0.35)] flex items-center justify-center gap-2 cursor-pointer"
              >
                <Play size={16} fill="currentColor" /> Enter Aethelgard
              </button>

              <button
                onClick={() => setShowHelp(true)}
                className="bg-neutral-900 hover:bg-neutral-800 text-neutral-300 border border-neutral-700 hover:border-neutral-600 font-semibold py-3.5 px-5 rounded-xl text-xs tracking-wider transition-colors flex items-center justify-center gap-2 cursor-pointer"
              >
                <HelpCircle size={16} /> Controls
              </button>
            </div>

            {/* Quick sound toggle */}
            <button
              onClick={handleMuteToggle}
              className="text-neutral-500 hover:text-neutral-300 text-xs flex items-center gap-1.5 transition-colors"
            >
              {isMuted ? <VolumeX size={14} /> : <Volume2 size={14} />}
              <span>{isMuted ? 'Sound Muted' : 'Synthesizer Audio Active'}</span>
            </button>
          </div>
        </div>
      )}

      {/* ACTIVE GAME VIEW */}
      {hasStarted && (
        <>
          {/* Main 60FPS Game Canvas */}
          <GameCanvas
            engine={engine}
            onOpenMap={() => setShowMap(true)}
            onOpenInventory={() => setShowInventory(true)}
          />

          {/* HUD Overlay */}
          <HUD
            engine={engine}
            onOpenMap={() => setShowMap(true)}
            onOpenInventory={() => setShowInventory(true)}
            onOpenHelp={() => setShowHelp(true)}
            onMuteToggle={handleMuteToggle}
            isMuted={isMuted}
          />

          {/* Virtual On-screen Mobile Controls */}
          <TouchControls engine={engine} />

          {/* Interactive World Map Modal */}
          {(showMap || engine.gameView === 'world_map') && (
            <WorldMapModal
              engine={engine}
              onClose={() => {
                setShowMap(false);
                if (engine.gameView === 'world_map') engine.gameView = 'playing';
              }}
            />
          )}

          {/* Equipment & Character Attributes Modal */}
          {(showInventory || engine.gameView === 'inventory') && (
            <InventoryModal
              engine={engine}
              onClose={() => {
                setShowInventory(false);
                if (engine.gameView === 'inventory') engine.gameView = 'playing';
              }}
            />
          )}

          {/* Whisperstone Shrine Resting Modal */}
          {engine.gameView === 'shrine' && (
            <ShrineModal
              engine={engine}
              onOpenInventory={() => setShowInventory(true)}
              onOpenMap={() => setShowMap(true)}
              onClose={() => {
                engine.gameView = 'playing';
                forceUpdate();
              }}
            />
          )}

          {/* Game Over (You Died) Modal */}
          {engine.gameView === 'game_over' && (
            <GameOverModal engine={engine} />
          )}

          {/* Titan Defeated / Victory Modal */}
          {engine.gameView === 'victory' && (
            <VictoryModal
              engine={engine}
              onContinue={() => {
                engine.gameView = 'playing';
                forceUpdate();
              }}
            />
          )}
        </>
      )}

      {/* Controls & Codex Modal */}
      {showHelp && (
        <ControlsGuideModal onClose={() => setShowHelp(false)} />
      )}
    </div>
  );
}
