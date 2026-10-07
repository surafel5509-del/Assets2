/**
 * Controls & Combat Mechanics Guide Modal
 */

import React from 'react';
import { X, Shield, Swords, Wind, Sparkles, Navigation } from 'lucide-react';

interface ControlsGuideModalProps {
  onClose: () => void;
}

export const ControlsGuideModal: React.FC<ControlsGuideModalProps> = ({ onClose }) => {
  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-4 select-none">
      <div className="bg-neutral-950 border border-neutral-800 rounded-2xl max-w-xl w-full p-6 shadow-2xl overflow-y-auto max-h-[90vh]">
        <div className="flex justify-between items-center border-b border-neutral-800 pb-3 mb-4">
          <h2 className="font-cinzel text-lg font-bold text-neutral-100 tracking-wider">
            Combat & Controls Codex
          </h2>
          <button
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            <X size={20} />
          </button>
        </div>

        <div className="space-y-4 text-xs">
          {/* Movement & Dodge */}
          <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-3.5">
            <h3 className="font-cinzel font-bold text-neutral-200 text-sm mb-2 flex items-center gap-2">
              <Navigation size={16} className="text-amber-400" /> Movement & Dodge Roll
            </h3>
            <div className="grid grid-cols-2 gap-2 text-neutral-300">
              <div><strong className="text-neutral-100">WASD / Arrow Keys:</strong> 8-directional movement</div>
              <div><strong className="text-neutral-100">Spacebar:</strong> Dodge Roll (Invulnerability frames!)</div>
            </div>
            <p className="text-[11px] text-neutral-400 mt-2">
              Use Dodge Roll to phase straight through boss projectiles and sweeps without taking damage. Consumes 20 Stamina.
            </p>
          </div>

          {/* Melee & Combos */}
          <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-3.5">
            <h3 className="font-cinzel font-bold text-neutral-200 text-sm mb-2 flex items-center gap-2">
              <Swords size={16} className="text-amber-400" /> Melee Combo Chain
            </h3>
            <div className="grid grid-cols-2 gap-2 text-neutral-300">
              <div><strong className="text-neutral-100">Left Click / [J]:</strong> Attack Combo</div>
              <div><strong className="text-neutral-100">Combo Finisher:</strong> 3rd hit deals 175% DMG</div>
            </div>
            <p className="text-[11px] text-neutral-400 mt-2">
              Staggered or parried bosses receive a Critical Riposte dealing 220% damage.
            </p>
          </div>

          {/* Block & Perfect Parry */}
          <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-3.5">
            <h3 className="font-cinzel font-bold text-neutral-200 text-sm mb-2 flex items-center gap-2">
              <Shield size={16} className="text-sky-400" /> Shield Block & Perfect Parry
            </h3>
            <div className="grid grid-cols-2 gap-2 text-neutral-300">
              <div><strong className="text-neutral-100">Right Click / [K]:</strong> Raise Shield (80% DMG Reduction)</div>
              <div><strong className="text-amber-300">Perfect Parry:</strong> Tap right as enemy hits!</div>
            </div>
            <p className="text-[11px] text-neutral-400 mt-2">
              A Perfect Parry negates 100% damage, creates a ringing sound, and staggers the boss for 1.8 seconds, exposing them to devastating critical counterattacks!
            </p>
          </div>

          {/* Magic Spells & Items */}
          <div className="bg-neutral-900/80 border border-neutral-800 rounded-xl p-3.5">
            <h3 className="font-cinzel font-bold text-neutral-200 text-sm mb-2 flex items-center gap-2">
              <Sparkles size={16} className="text-purple-400" /> Spells & Shortcuts
            </h3>
            <div className="grid grid-cols-2 gap-2 text-neutral-300">
              <div><strong className="text-neutral-100">[1] Flame Arc:</strong> Piercing fire spread (25 MP)</div>
              <div><strong className="text-neutral-100">[2] Frost Nova:</strong> Freezes all foes for 2.5s (35 MP)</div>
              <div><strong className="text-neutral-100">[3] Thunder Smite:</strong> Piercing heavy bolt (45 MP)</div>
              <div><strong className="text-neutral-100">[Q] Estus Flask:</strong> Restores 65% HP</div>
              <div><strong className="text-neutral-100">[E] Interact:</strong> Shrines, Chests, NPCs</div>
              <div><strong className="text-neutral-100">[M] Map:</strong> Full World Map & Fast Travel</div>
              <div><strong className="text-neutral-100">[I / Tab] Ledger:</strong> Equipment & Leveling</div>
            </div>
          </div>
        </div>

        <div className="mt-5 text-center">
          <button
            onClick={onClose}
            className="bg-neutral-900 hover:bg-neutral-800 text-neutral-200 border border-neutral-700 px-6 py-2 rounded-xl text-xs font-semibold transition-colors"
          >
            Understood
          </button>
        </div>
      </div>
    </div>
  );
};
