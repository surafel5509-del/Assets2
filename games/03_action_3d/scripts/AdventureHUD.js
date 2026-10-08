// Hollow Ridge -- AdventureHUD.js
// Health, stamina, compass, objective tracker and the damage feedback.
//
// Stamina is what stops sprint-spam from being the only way to move, and the
// regen delay is what makes it a decision rather than a bar that is always full.
//
// Params: maxHealth=100, maxStamina=100, staminaDrain=22, staminaRegen=16,
//         regenDelay=1.2, attack=12

var health = 100;
var stamina = 100;
var regenTimer = 0;
var hurtFlash = 0;
var objectives = [];
var kills = 0;

function start() {
    health = maxHealth;
    stamina = maxStamina;
    objectives = ["Find the campfire", "Clear the ridge of creatures"];
    refresh();
}

function update(dt) {
    var hero = scene.find("Hero");

    // --- stamina
    var sprinting = hero && hero.send("isAttacking") === false &&
                    Math.abs(input.axisX) + Math.abs(input.axisY) > 1.5 && input.a;
    if (sprinting && stamina > 0) {
        stamina = Math.max(0, stamina - staminaDrain * dt);
        regenTimer = regenDelay;
    } else if (regenTimer > 0) {
        regenTimer -= dt;
    } else {
        stamina = Math.min(maxStamina, stamina + staminaRegen * dt);
    }

    // --- out of stamina means no sprint; CharacterController3D reads this
    if (hero) hero.send("setStamina", stamina / maxStamina);

    if (hurtFlash > 0) {
        hurtFlash -= dt;
        var v = scene.find("HurtVignette");
        if (v) v.color = hurtFlash > 0 ? "#60D0453F" : "#00000000";
    }

    applyBars();
}

function applyBars() {
    var hBar = scene.find("HealthFill");
    if (hBar) hBar.scaleX = Math.max(0.001, health / maxHealth);
    var sBar = scene.find("StaminaFill");
    if (sBar) sBar.scaleX = Math.max(0.001, stamina / maxStamina);

    var ht = scene.find("HealthText");
    if (ht) ht.text = Math.ceil(health) + " / " + maxHealth;
}

function refresh() {
    applyBars();
    var obj = scene.find("ObjectiveText");
    if (obj) obj.text = objectives.length ? ("- " + objectives[0]) : "All objectives complete";
}

function damageHero(amount) {
    health -= amount;
    hurtFlash = 0.3;
    scene.shake(0.2);
    applyBars();
    if (health <= 0) { health = 0; die(); }
}

function healHero(amount) {
    health = Math.min(maxHealth, health + amount);
    applyBars();
}

function die() {
    log("You have fallen on the ridge.");
    var b = scene.find("DeathBanner");
    if (b) b.active = true;
    after(3.0, function () { scene.reload(); });
}

function onEnemyKilled() {
    kills++;
    var hud = scene.find("ObjectiveText");
    if (kills >= 3 && objectives.length > 1) {
        objectives.shift();               // first objective is done
        objectives.shift();
        refresh();
        log("The ridge is quiet.");
    }
}

/** Called when the player rests at a campfire. */
function restAtCampfire() {
    health = maxHealth;
    stamina = maxStamina;
    var rig = scene.find("LightingRig");
    if (rig) rig.send("setHour", 7);      // resting skips to morning
    applyBars();
    log("You rest until dawn.");
}

function completeObjective(name) {
    for (var i = 0; i < objectives.length; i++) {
        if (objectives[i] === name) { objectives.splice(i, 1); break; }
    }
    refresh();
}

function getHeroAttack() { return attack; }
function getHealth() { return health; }
function getKills() { return kills; }
