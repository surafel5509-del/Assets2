/**
 * Heads Up Display: Health, Stamina, Mana, Flasks, Embers, Minimap, Boss HP, and Interaction Prompts
 */

import React from 'react';
import { GameEngine } from '../game/engine';
import { soundEngine } from '../game/audio';
import { Volume2, VolumeX, Map as MapIcon, Backpack, HelpCircle, Shield, Swords, Sparkles } from 'lucide-react';

interface HUDProps {
  engine: GameEngine;
  onOpenMap: () => void;
  onOpenInventory: () => void;
  onOpenHelp: () => void;
  onMuteToggle: () => void;
  isMuted: boolean;
}

export const HUD: React.FC<HUDProps> = ({
  engine,
  onOpenMap,
  onOpenInventory,
  onOpenHelp,
  onMuteToggle,
  isMuted,
}) => {
  const { player, activeBoss, currentInteraction, activeDialogue } = engine;

  const hpPercent = Math.max(0, Math.min(100, (player.hp / player.maxHp) * 100));
  const staminaPercent = Math.max(0, Math.min(100, (player.stamina / player.maxStamina) * 100));
  const manaPercent = Math.max(0, Math.min(100, (player.mana / player.maxMana) * 100));

  const currentBiome = engine.getCurrentBiome();
  const biomeNames = {
    verdant: 'Whispering Glade',
    marsh: 'The Sunken Fen',
    caldera: 'Obsidian Caldera',
    frost: 'Glacial Peak',
    sanctum: 'Void Sanctum',
  };

  return (
    <div className="absolute inset-0 pointer-events-none select-none flex flex-col justify-between p-4 md:p-6">
      {/* TOP BAR */}
      <div className="flex justify-between items-start gap-4">
        {/* PLAYER STATUS BARS */}
        <div className="pointer-events-auto bg-neutral-900/85 backdrop-blur-md border border-neutral-800 rounded-lg p-3.5 shadow-xl w-64 md:w-80">
          <div className="flex justify-between items-center mb-1.5 text-xs">
            <span className="font-cinzel font-bold text-amber-200 tracking-wider">
              Knight Vanguard · Lv.{player.stats.level}
            </span>
            <span className="font-pixel text-[10px] text-amber-400 flex items-center gap-1">
              ✦ {player.stats.embers}
            </span>
          </div>

          {/* HP Bar */}
          <div className="mb-2">
            <div className="flex justify-between text-[11px] mb-0.5">
              <span className="text-red-300 font-semibold">HP</span>
              <span className="text-neutral-400 font-mono text-[10px]">
                {Math.ceil(player.hp)} / {player.maxHp}
              </span>
            </div>
            <div className="h-3 w-full bg-neutral-950 rounded overflow-hidden border border-red-950/80">
              <div
                className="h-full bg-gradient-to-r from-red-700 via-red-600 to-rose-500 transition-all duration-100 ease-out"
                style={{ width: `${hpPercent}%` }}
              />
            </div>
          </div>

          {/* Stamina Bar */}
          <div className="mb-2">
            <div className="flex justify-between text-[11px] mb-0.5">
              <span className="text-emerald-300 font-semibold">STAMINA</span>
              <span className="text-neutral-400 font-mono text-[10px]">
                {Math.ceil(player.stamina)} / {player.maxStamina}
              </span>
            </div>
            <div className="h-2 w-full bg-neutral-950 rounded overflow-hidden border border-emerald-950/80">
              <div
                className="h-full bg-gradient-to-r from-emerald-600 to-teal-400 transition-all duration-75 ease-out"
                style={{ width: `${staminaPercent}%` }}
              />
            </div>
          </div>

          {/* Mana / Aether Bar */}
          <div>
            <div className="flex justify-between text-[11px] mb-0.5">
              <span className="text-cyan-300 font-semibold">AETHER</span>
              <span className="text-neutral-400 font-mono text-[10px]">
                {Math.ceil(player.mana)} / {player.maxMana}
              </span>
            </div>
            <div className="h-2 w-full bg-neutral-950 rounded overflow-hidden border border-cyan-950/80">
              <div
                className="h-full bg-gradient-to-r from-cyan-600 to-blue-400 transition-all duration-75 ease-out"
                style={{ width: `${manaPercent}%` }}
              />
            </div>
          </div>

          {/* Quick Flask & Rune indicator */}
          <div className="mt-3 pt-2.5 border-t border-neutral-800 flex items-center justify-between text-xs">
            <div className="flex items-center gap-2">
              <button
                onClick={() => engine.triggerFlask()}
                className="flex items-center gap-1.5 bg-neutral-800 hover:bg-neutral-700 text-amber-300 border border-amber-500/40 px-2 py-1 rounded transition-colors text-xs font-mono"
                title="Drink Flask [Q]"
              >
                <span>🧪</span>
                <span className="font-bold">{player.flasks}/{player.maxFlasks}</span>
                <span className="text-[10px] text-neutral-400">[Q]</span>
              </button>
            </div>

            {/* Runes Collected */}
            <div className="flex items-center gap-1 text-xs" title="Titan Runes: Marsh, Caldera, Frost">
              <span className={`text-xs ${player.unlockedRunes.marsh ? 'text-emerald-400' : 'text-neutral-600'}`}>
                ●
              </span>
              <span className={`text-xs ${player.unlockedRunes.caldera ? 'text-orange-400' : 'text-neutral-600'}`}>
                ●
              </span>
              <span className={`text-xs ${player.unlockedRunes.frost ? 'text-sky-400' : 'text-neutral-600'}`}>
                ●
              </span>
              <span className="text-[10px] text-neutral-400 ml-1">Runes</span>
            </div>
          </div>
        </div>

        {/* TOP RIGHT: MINIMAP & ACTION BUTTONS */}
        <div className="flex flex-col items-end gap-3 pointer-events-auto">
          {/* Action icon buttons */}
          <div className="flex items-center gap-1.5 bg-neutral-900/85 backdrop-blur-md p-1.5 border border-neutral-800 rounded-lg shadow-lg">
            <button
              onClick={onOpenMap}
              className="p-2 hover:bg-neutral-800 rounded text-neutral-300 hover:text-white transition-colors"
              title="World Map [M]"
            >
              <MapIcon size={18} />
            </button>
            <button
              onClick={onOpenInventory}
              className="p-2 hover:bg-neutral-800 rounded text-neutral-300 hover:text-white transition-colors"
              title="Inventory & Stats [I]"
            >
              <Backpack size={18} />
            </button>
            <button
              onClick={onMuteToggle}
              className="p-2 hover:bg-neutral-800 rounded text-neutral-300 hover:text-white transition-colors"
              title="Mute Audio"
            >
              {isMuted ? <VolumeX size={18} className="text-red-400" /> : <Volume2 size={18} />}
            </button>
            <button
              onClick={onOpenHelp}
              className="p-2 hover:bg-neutral-800 rounded text-neutral-300 hover:text-white transition-colors"
              title="Controls & Mechanics"
            >
              <HelpCircle size={18} />
            </button>
          </div>

          {/* Minimap card */}
          <div className="bg-neutral-900/85 backdrop-blur-md border border-neutral-800 rounded-lg p-2.5 shadow-xl w-36 h-36 relative overflow-hidden flex flex-col justify-between">
            <div className="absolute inset-0 bg-neutral-950">
              {/* Mini radar canvas / grid representation */}
              <div className="absolute inset-0 opacity-20 bg-[radial-gradient(#64748b_1px,transparent_1px)] [background-size:12px_12px]" />
              
              {/* Center sanctum marker */}
              <div className="absolute left-[50%] top-[50%] -translate-x-1/2 -translate-y-1/2 w-4 h-4 rounded-full border border-purple-500/40 bg-purple-900/20" />

              {/* Player Position Dot */}
              <div
                className="absolute w-2.5 h-2.5 bg-cyan-400 rounded-full shadow-[0_0_8px_#38bdf8] -translate-x-1/2 -translate-y-1/2"
                style={{
                  left: `${(player.x / (120 * 32)) * 100}%`,
                  top: `${(player.y / (120 * 32)) * 100}%`,
                }}
              />
            </div>

            <div className="relative z-10 flex justify-between items-center text-[10px] text-neutral-400 font-pixel">
              <span>N</span>
              <span className="text-amber-300 text-[9px] uppercase tracking-wider">{biomeNames[currentBiome]}</span>
            </div>

            <div className="relative z-10 text-[9px] text-neutral-400 text-right font-mono">
              X:{Math.floor(player.x / 32)} Y:{Math.floor(player.y / 32)}
            </div>
          </div>
        </div>
      </div>

      {/* CENTER: INTERACTION PROMPT & DIALOGUE */}
      <div className="flex flex-col items-center justify-center my-auto pointer-events-auto">
        {currentInteraction && !activeDialogue && (
          <div className="bg-neutral-900/90 border border-amber-500/60 text-amber-200 px-4 py-2 rounded-lg font-cinzel text-sm shadow-2xl animate-pulse flex items-center gap-2">
            <Sparkles size={16} className="text-amber-400" />
            <span>{currentInteraction.label}</span>
          </div>
        )}

        {/* NPC Dialogue Box */}
        {activeDialogue && (
          <div className="bg-neutral-950/95 border border-indigo-900/80 rounded-xl p-5 max-w-xl w-full shadow-2xl backdrop-blur-md">
            <div className="flex justify-between items-center border-b border-neutral-800 pb-2 mb-3">
              <span className="font-cinzel text-amber-300 font-bold text-sm tracking-wide">
                {activeDialogue.speaker}
              </span>
              <span className="text-[10px] text-neutral-500 font-mono">[ESC / E] Close</span>
            </div>
            <p className="text-neutral-200 text-sm leading-relaxed mb-4">
              "{activeDialogue.text}"
            </p>
            <div className="flex justify-end">
              <button
                onClick={() => engine.closeDialogue()}
                className="bg-indigo-900 hover:bg-indigo-800 text-white text-xs px-3 py-1.5 rounded transition-colors font-semibold"
              >
                Continue
              </button>
            </div>
          </div>
        )}
      </div>

      {/* BOTTOM AREA: BOSS HEALTH BAR OR SKILL HOTBAR */}
      <div className="flex flex-col items-center gap-3">
        {/* BOSS HEALTH BAR */}
        {activeBoss && activeBoss.hp > 0 && (
          <div className="w-full max-w-2xl bg-neutral-950/90 border border-red-950/80 rounded-xl p-3 shadow-2xl backdrop-blur-md pointer-events-auto animate-fade-in">
            <div className="flex justify-between items-baseline mb-1 px-1">
              <div>
                <span className="font-cinzel text-base font-bold text-red-200 tracking-wider">
                  {activeBoss.name}
                </span>
                <span className="text-xs text-neutral-400 ml-2 italic">
                  {activeBoss.title}
                </span>
              </div>
              <div className="text-xs font-mono text-neutral-300">
                {activeBoss.enraged && (
                  <span className="text-rose-400 font-bold mr-2 uppercase tracking-wider animate-pulse">
                    ⚠ ENRAGED
                  </span>
                )}
                <span>
                  {Math.ceil(activeBoss.hp)} / {activeBoss.maxHp}
                </span>
              </div>
            </div>

            {/* Boss HP Bar */}
            <div className="h-4 w-full bg-neutral-900 rounded overflow-hidden border border-red-900/60 relative">
              <div
                className="h-full bg-gradient-to-r from-red-800 via-red-600 to-rose-600 transition-all duration-150 ease-out"
                style={{ width: `${Math.max(0, (activeBoss.hp / activeBoss.maxHp) * 100)}%` }}
              />
            </div>
          </div>
        )}

        {/* SKILLS & WEAPON HOTBAR */}
        <div className="pointer-events-auto bg-neutral-900/85 backdrop-blur-md border border-neutral-800 rounded-xl px-4 py-2 flex items-center gap-3 shadow-xl">
          {/* Weapon / Attack */}
          <div
            onClick={() => engine.triggerAttack()}
            className="flex flex-col items-center cursor-pointer hover:bg-neutral-800/80 p-1.5 rounded transition-colors"
            title="Attack Combo [J / Left Click]"
          >
            <div className="w-10 h-10 rounded-lg bg-neutral-950 border border-neutral-700 flex items-center justify-center text-lg">
              {player.equipment.weapon.icon}
            </div>
            <span className="text-[10px] text-neutral-400 mt-0.5 font-mono">ATK [J]</span>
          </div>

          {/* Block / Parry */}
          <div
            onClick={() => engine.triggerBlock(!player.isBlocking)}
            className={`flex flex-col items-center cursor-pointer hover:bg-neutral-800/80 p-1.5 rounded transition-colors ${player.isBlocking ? 'bg-blue-900/40 border-blue-500' : ''}`}
            title="Block / Perfect Parry [K / Right Click]"
          >
            <div className="w-10 h-10 rounded-lg bg-neutral-950 border border-neutral-700 flex items-center justify-center text-lg">
              🛡️
            </div>
            <span className="text-[10px] text-neutral-400 mt-0.5 font-mono">PARRY [K]</span>
          </div>

          {/* Roll */}
          <div
            onClick={() => engine.triggerRoll()}
            className="flex flex-col items-center cursor-pointer hover:bg-neutral-800/80 p-1.5 rounded transition-colors"
            title="Dodge Roll [Space]"
          >
            <div className="w-10 h-10 rounded-lg bg-neutral-950 border border-neutral-700 flex items-center justify-center text-lg">
              💨
            </div>
            <span className="text-[10px] text-neutral-400 mt-0.5 font-mono">ROLL [SPC]</span>
          </div>

          <div className="h-8 w-px bg-neutral-800 mx-1" />

          {/* Spells */}
          <div
            onClick={() => engine.castSpell('fire')}
            className={`flex flex-col items-center cursor-pointer hover:bg-neutral-800/80 p-1.5 rounded transition-colors ${player.mana < 25 ? 'opacity-40' : ''}`}
            title="Flame Arc (25 MP) [1]"
          >
            <div className="w-10 h-10 rounded-lg bg-neutral-950 border border-orange-900/60 flex items-center justify-center text-lg">
              🔥
            </div>
            <span className="text-[10px] text-neutral-400 mt-0.5 font-mono">[1] 25MP</span>
          </div>

          <div
            onClick={() => engine.castSpell('frost')}
            className={`flex flex-col items-center cursor-pointer hover:bg-neutral-800/80 p-1.5 rounded transition-colors ${player.mana < 35 ? 'opacity-40' : ''}`}
            title="Frost Nova Freeze (35 MP) [2]"
          >
            <div className="w-10 h-10 rounded-lg bg-neutral-950 border border-sky-900/60 flex items-center justify-center text-lg">
              ❄️
            </div>
            <span className="text-[10px] text-neutral-400 mt-0.5 font-mono">[2] 35MP</span>
          </div>

          <div
            onClick={() => engine.castSpell('thunder')}
            className={`flex flex-col items-center cursor-pointer hover:bg-neutral-800/80 p-1.5 rounded transition-colors ${player.mana < 45 ? 'opacity-40' : ''}`}
            title="Thunder Smite (45 MP) [3]"
          >
            <div className="w-10 h-10 rounded-lg bg-neutral-950 border border-amber-900/60 flex items-center justify-center text-lg">
              ⚡
            </div>
            <span className="text-[10px] text-neutral-400 mt-0.5 font-mono">[3] 45MP</span>
          </div>
        </div>
      </div>
    </div>
  );
};
