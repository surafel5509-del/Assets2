/**
 * Fullscreen Pixel Game Canvas with 60FPS Game Loop & Input Listeners
 */

import React, { useEffect, useRef } from 'react';
import { GameEngine } from '../game/engine';

interface GameCanvasProps {
  engine: GameEngine;
  onOpenMap: () => void;
  onOpenInventory: () => void;
}

export const GameCanvas: React.FC<GameCanvasProps> = ({
  engine,
  onOpenMap,
  onOpenInventory,
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let animationFrameId: number;
    let lastTime = performance.now();

    // Resize handler
    const handleResize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
      ctx.imageSmoothingEnabled = false;
    };
    handleResize();
    window.addEventListener('resize', handleResize);

    // ----------------------------------------------------
    // Input Handlers
    // ----------------------------------------------------
    const handleKeyDown = (e: KeyboardEvent) => {
      // Prevent scrolling on Space / Arrow keys
      if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code)) {
        e.preventDefault();
      }

      if (e.code === 'KeyW' || e.code === 'ArrowUp') engine.keys.up = true;
      if (e.code === 'KeyS' || e.code === 'ArrowDown') engine.keys.down = true;
      if (e.code === 'KeyA' || e.code === 'ArrowLeft') engine.keys.left = true;
      if (e.code === 'KeyD' || e.code === 'ArrowRight') engine.keys.right = true;

      if (e.code === 'Space') {
        engine.triggerRoll();
      }
      if (e.code === 'KeyJ') {
        engine.triggerAttack();
      }
      if (e.code === 'KeyK') {
        engine.triggerBlock(true);
      }
      if (e.code === 'KeyQ') {
        engine.triggerFlask();
      }
      if (e.code === 'KeyE') {
        if (engine.activeDialogue) {
          engine.closeDialogue();
        } else {
          engine.triggerInteract();
        }
      }
      if (e.code === 'KeyM') {
        onOpenMap();
      }
      if (e.code === 'KeyI' || e.code === 'Tab') {
        e.preventDefault();
        onOpenInventory();
      }
      if (e.code === 'Digit1') engine.castSpell('fire');
      if (e.code === 'Digit2') engine.castSpell('frost');
      if (e.code === 'Digit3') engine.castSpell('thunder');
      if (e.code === 'Escape') {
        if (engine.activeDialogue) {
          engine.closeDialogue();
        }
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      if (e.code === 'KeyW' || e.code === 'ArrowUp') engine.keys.up = false;
      if (e.code === 'KeyS' || e.code === 'ArrowDown') engine.keys.down = false;
      if (e.code === 'KeyA' || e.code === 'ArrowLeft') engine.keys.left = false;
      if (e.code === 'KeyD' || e.code === 'ArrowRight') engine.keys.right = false;
      if (e.code === 'KeyK') engine.triggerBlock(false);
    };

    const handleMouseMove = (e: MouseEvent) => {
      engine.mousePos.x = e.clientX;
      engine.mousePos.y = e.clientY;
    };

    const handleMouseDown = (e: MouseEvent) => {
      if (e.button === 0) {
        // Left click: Attack
        engine.triggerAttack();
      } else if (e.button === 2) {
        // Right click: Block / Parry
        e.preventDefault();
        engine.triggerBlock(true);
      }
    };

    const handleMouseUp = (e: MouseEvent) => {
      if (e.button === 2) {
        e.preventDefault();
        engine.triggerBlock(false);
      }
    };

    const handleContextMenu = (e: MouseEvent) => {
      e.preventDefault();
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mousedown', handleMouseDown);
    window.addEventListener('mouseup', handleMouseUp);
    window.addEventListener('contextmenu', handleContextMenu);

    // ----------------------------------------------------
    // Main Render / Update Loop
    // ----------------------------------------------------
    const loop = (currentTime: number) => {
      const dt = Math.min(0.1, (currentTime - lastTime) / 1000);
      lastTime = currentTime;

      engine.update(dt);
      engine.render(ctx, canvas.width, canvas.height);

      animationFrameId = requestAnimationFrame(loop);
    };

    animationFrameId = requestAnimationFrame(loop);

    return () => {
      cancelAnimationFrame(animationFrameId);
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mousedown', handleMouseDown);
      window.removeEventListener('mouseup', handleMouseUp);
      window.removeEventListener('contextmenu', handleContextMenu);
    };
  }, [engine, onOpenMap, onOpenInventory]);

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 w-full h-full pixelated block bg-neutral-950 cursor-crosshair"
    />
  );
};
