/**
 * Inventory & Character Attribute Leveling Modal
 */

import React, { useState } from 'react';
import { GameEngine } from '../game/engine';
import { equipItem } from '../game/player';
import { Item } from '../game/types';
import { X, Shield, Swords, Sparkles, Heart, Zap, Award, Flame } from 'lucide-react';

interface InventoryModalProps {
  engine: GameEngine;
  onClose: () => void;
}

export const InventoryModal: React.FC<InventoryModalProps> = ({ engine, onClose }) => {
  const { player } = engine;
  const [activeTab, setActiveTab] = useState<'equipment' | 'stats'>('equipment');
  const [selectedItem, setSelectedItem] = useState<Item>(player.equipment.weapon);

  const upgradeCost = 50 + player.stats.level * 30;
  const canAfford = player.stats.embers >= upgradeCost;

  const handleEquip = (item: Item) => {
    equipItem(player, item);
    setSelectedItem(item);
  };

  const handleUpgrade = (stat: 'vigor' | 'endurance' | 'strength' | 'arcane') => {
    engine.levelUpStat(stat);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-md flex items-center justify-center p-4 select-none">
      <div className="bg-neutral-950 border border-neutral-800 rounded-2xl max-w-3xl w-full p-6 shadow-2xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex justify-between items-center border-b border-neutral-800 pb-3 mb-4">
          <div className="flex items-center gap-4">
            <h2 className="font-cinzel text-xl font-bold text-neutral-100 tracking-wider">
              Hero's Ledger
            </h2>
            {/* Tabs */}
            <div className="flex gap-2">
              <button
                onClick={() => setActiveTab('equipment')}
                className={`px-3 py-1 text-xs rounded-lg font-medium transition-colors ${
                  activeTab === 'equipment'
                    ? 'bg-neutral-800 text-amber-300 border border-amber-500/40'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                Equipment
              </button>
              <button
                onClick={() => setActiveTab('stats')}
                className={`px-3 py-1 text-xs rounded-lg font-medium transition-colors ${
                  activeTab === 'stats'
                    ? 'bg-neutral-800 text-amber-300 border border-amber-500/40'
                    : 'text-neutral-400 hover:text-white'
                }`}
              >
                Attributes & Level
              </button>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            <X size={20} />
          </button>
        </div>

        {/* TAB 1: EQUIPMENT */}
        {activeTab === 'equipment' && (
          <div className="grid grid-cols-1 md:grid-cols-12 gap-6 overflow-y-auto">
            {/* Left: Equipped Slots & Item Grid */}
            <div className="md:col-span-7 flex flex-col gap-4">
              {/* Currently Equipped Summary */}
              <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-3.5">
                <span className="text-xs font-cinzel text-neutral-400 uppercase tracking-wider block mb-2">
                  Active Loadout
                </span>
                <div className="grid grid-cols-3 gap-2">
                  {/* Weapon Slot */}
                  <div
                    onClick={() => setSelectedItem(player.equipment.weapon)}
                    className="bg-neutral-950 p-2 rounded-lg border border-neutral-800 flex flex-col items-center cursor-pointer hover:border-amber-500/50 transition-colors"
                  >
                    <span className="text-[10px] text-neutral-500 uppercase">Weapon</span>
                    <span className="text-2xl my-1">{player.equipment.weapon.icon}</span>
                    <span className="text-[11px] text-neutral-200 font-semibold truncate w-full text-center">
                      {player.equipment.weapon.name}
                    </span>
                  </div>

                  {/* Armor Slot */}
                  <div
                    onClick={() => setSelectedItem(player.equipment.armor)}
                    className="bg-neutral-950 p-2 rounded-lg border border-neutral-800 flex flex-col items-center cursor-pointer hover:border-amber-500/50 transition-colors"
                  >
                    <span className="text-[10px] text-neutral-500 uppercase">Armor</span>
                    <span className="text-2xl my-1">{player.equipment.armor.icon}</span>
                    <span className="text-[11px] text-neutral-200 font-semibold truncate w-full text-center">
                      {player.equipment.armor.name}
                    </span>
                  </div>

                  {/* Ring Slot */}
                  <div
                    onClick={() => player.equipment.ring && setSelectedItem(player.equipment.ring)}
                    className="bg-neutral-950 p-2 rounded-lg border border-neutral-800 flex flex-col items-center cursor-pointer hover:border-amber-500/50 transition-colors"
                  >
                    <span className="text-[10px] text-neutral-500 uppercase">Relic</span>
                    <span className="text-2xl my-1">
                      {player.equipment.ring ? player.equipment.ring.icon : '📿'}
                    </span>
                    <span className="text-[11px] text-neutral-200 font-semibold truncate w-full text-center">
                      {player.equipment.ring ? player.equipment.ring.name : 'Empty Slot'}
                    </span>
                  </div>
                </div>
              </div>

              {/* Inventory Storage List */}
              <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-3.5 flex-1">
                <span className="text-xs font-cinzel text-neutral-400 uppercase tracking-wider block mb-2">
                  Inventory ({player.inventory.length})
                </span>
                <div className="grid grid-cols-4 gap-2 max-h-56 overflow-y-auto pr-1">
                  {player.inventory.map((item, idx) => (
                    <div
                      key={`${item.id}_${idx}`}
                      onClick={() => setSelectedItem(item)}
                      className={`bg-neutral-950 p-2.5 rounded-lg border cursor-pointer flex flex-col items-center justify-center transition-all ${
                        selectedItem?.id === item.id
                          ? 'border-amber-400 shadow-[0_0_8px_rgba(251,191,36,0.3)]'
                          : 'border-neutral-800 hover:border-neutral-700'
                      }`}
                    >
                      <span className="text-2xl">{item.icon}</span>
                      <span className="text-[10px] text-neutral-300 font-medium truncate w-full text-center mt-1">
                        {item.name}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            </div>

            {/* Right: Selected Item Details */}
            <div className="md:col-span-5 bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 flex flex-col justify-between">
              {selectedItem ? (
                <div>
                  <div className="flex items-center gap-3 border-b border-neutral-800 pb-3 mb-3">
                    <span className="text-3xl p-2 bg-neutral-950 border border-neutral-800 rounded-lg">
                      {selectedItem.icon}
                    </span>
                    <div>
                      <h3 className="font-cinzel text-sm font-bold text-neutral-100">
                        {selectedItem.name}
                      </h3>
                      <span className="text-[10px] uppercase font-mono tracking-wider text-amber-400">
                        {selectedItem.rarity} · {selectedItem.type}
                      </span>
                    </div>
                  </div>

                  {/* Item Stats */}
                  <div className="space-y-2 mb-4 text-xs">
                    {selectedItem.attack && (
                      <div className="flex justify-between bg-neutral-950/60 p-2 rounded">
                        <span className="text-neutral-400">Attack Power</span>
                        <span className="text-amber-300 font-bold font-mono">
                          +{selectedItem.attack}
                        </span>
                      </div>
                    )}
                    {selectedItem.defense && (
                      <div className="flex justify-between bg-neutral-950/60 p-2 rounded">
                        <span className="text-neutral-400">Defense Rating</span>
                        <span className="text-sky-300 font-bold font-mono">
                          +{selectedItem.defense}
                        </span>
                      </div>
                    )}
                    {selectedItem.bonusHp && (
                      <div className="flex justify-between bg-neutral-950/60 p-2 rounded">
                        <span className="text-neutral-400">Bonus Health</span>
                        <span className="text-red-300 font-bold font-mono">
                          +{selectedItem.bonusHp} HP
                        </span>
                      </div>
                    )}
                    {selectedItem.staminaRegen && (
                      <div className="flex justify-between bg-neutral-950/60 p-2 rounded">
                        <span className="text-neutral-400">Stamina Recovery</span>
                        <span className="text-emerald-300 font-bold font-mono">
                          +{selectedItem.staminaRegen}/s
                        </span>
                      </div>
                    )}
                  </div>

                  <p className="text-xs text-neutral-400 leading-relaxed mb-4 italic">
                    "{selectedItem.description}"
                  </p>

                  {selectedItem.special && (
                    <div className="bg-amber-950/30 border border-amber-800/40 p-2.5 rounded-lg text-xs text-amber-200 mb-4">
                      <span className="font-bold text-amber-300 block mb-0.5">Special Trait:</span>
                      {selectedItem.special}
                    </div>
                  )}
                </div>
              ) : (
                <div className="text-neutral-500 text-xs text-center my-auto">
                  Select an item to inspect its attributes
                </div>
              )}

              {selectedItem && (
                <button
                  onClick={() => handleEquip(selectedItem)}
                  className="w-full bg-amber-600 hover:bg-amber-500 text-neutral-950 font-bold py-2 rounded-lg text-xs uppercase tracking-wider transition-colors shadow-lg"
                >
                  Equip Item
                </button>
              )}
            </div>
          </div>
        )}

        {/* TAB 2: ATTRIBUTES & LEVELING */}
        {activeTab === 'stats' && (
          <div className="flex flex-col gap-6 overflow-y-auto">
            {/* Embers & Current Level Banner */}
            <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 flex justify-between items-center">
              <div>
                <span className="text-xs text-neutral-400 uppercase font-cinzel">Current Tier</span>
                <h3 className="font-cinzel text-xl font-bold text-amber-200">
                  Level {player.stats.level} Vanguard
                </h3>
              </div>
              <div className="text-right">
                <span className="text-xs text-neutral-400 uppercase font-cinzel">Available Embers</span>
                <div className="font-pixel text-lg text-amber-400 font-bold">
                  ✦ {player.stats.embers}
                </div>
              </div>
            </div>

            {/* Attribute List */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Vigor */}
              <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 flex justify-between items-center">
                <div>
                  <div className="flex items-center gap-1.5 font-cinzel font-bold text-red-300 text-sm">
                    <Heart size={16} /> Vigor
                  </div>
                  <span className="text-xs text-neutral-400 block mt-0.5">
                    Increases Max HP (+12 HP)
                  </span>
                  <span className="text-xs font-mono text-neutral-300 mt-1 block">
                    Rating: {player.stats.vigor} · Max HP: {player.maxHp}
                  </span>
                </div>
                <button
                  disabled={!canAfford}
                  onClick={() => handleUpgrade('vigor')}
                  className={`px-3 py-1.5 rounded text-xs font-bold font-mono transition-colors ${
                    canAfford
                      ? 'bg-amber-500 hover:bg-amber-400 text-neutral-950 cursor-pointer shadow-md'
                      : 'bg-neutral-800 text-neutral-500 cursor-not-allowed'
                  }`}
                >
                  +1 ({upgradeCost} ✦)
                </button>
              </div>

              {/* Endurance */}
              <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 flex justify-between items-center">
                <div>
                  <div className="flex items-center gap-1.5 font-cinzel font-bold text-emerald-300 text-sm">
                    <Zap size={16} /> Endurance
                  </div>
                  <span className="text-xs text-neutral-400 block mt-0.5">
                    Increases Max Stamina (+6 Stamina)
                  </span>
                  <span className="text-xs font-mono text-neutral-300 mt-1 block">
                    Rating: {player.stats.endurance} · Max Stamina: {player.maxStamina}
                  </span>
                </div>
                <button
                  disabled={!canAfford}
                  onClick={() => handleUpgrade('endurance')}
                  className={`px-3 py-1.5 rounded text-xs font-bold font-mono transition-colors ${
                    canAfford
                      ? 'bg-amber-500 hover:bg-amber-400 text-neutral-950 cursor-pointer shadow-md'
                      : 'bg-neutral-800 text-neutral-500 cursor-not-allowed'
                  }`}
                >
                  +1 ({upgradeCost} ✦)
                </button>
              </div>

              {/* Strength */}
              <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 flex justify-between items-center">
                <div>
                  <div className="flex items-center gap-1.5 font-cinzel font-bold text-amber-300 text-sm">
                    <Swords size={16} /> Strength
                  </div>
                  <span className="text-xs text-neutral-400 block mt-0.5">
                    Boosts weapon damage & stagger (+2.5 ATK)
                  </span>
                  <span className="text-xs font-mono text-neutral-300 mt-1 block">
                    Rating: {player.stats.strength} · Base Scaling: +{Math.floor(player.stats.strength * 2.5)}
                  </span>
                </div>
                <button
                  disabled={!canAfford}
                  onClick={() => handleUpgrade('strength')}
                  className={`px-3 py-1.5 rounded text-xs font-bold font-mono transition-colors ${
                    canAfford
                      ? 'bg-amber-500 hover:bg-amber-400 text-neutral-950 cursor-pointer shadow-md'
                      : 'bg-neutral-800 text-neutral-500 cursor-not-allowed'
                  }`}
                >
                  +1 ({upgradeCost} ✦)
                </button>
              </div>

              {/* Arcane */}
              <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-4 flex justify-between items-center">
                <div>
                  <div className="flex items-center gap-1.5 font-cinzel font-bold text-cyan-300 text-sm">
                    <Sparkles size={16} /> Arcane
                  </div>
                  <span className="text-xs text-neutral-400 block mt-0.5">
                    Increases Max Aether & spell damage (+8 MP)
                  </span>
                  <span className="text-xs font-mono text-neutral-300 mt-1 block">
                    Rating: {player.stats.arcane} · Max Mana: {player.maxMana}
                  </span>
                </div>
                <button
                  disabled={!canAfford}
                  onClick={() => handleUpgrade('arcane')}
                  className={`px-3 py-1.5 rounded text-xs font-bold font-mono transition-colors ${
                    canAfford
                      ? 'bg-amber-500 hover:bg-amber-400 text-neutral-950 cursor-pointer shadow-md'
                      : 'bg-neutral-800 text-neutral-500 cursor-not-allowed'
                  }`}
                >
                  +1 ({upgradeCost} ✦)
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
