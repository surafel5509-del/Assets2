package com.sengine.engine.gameplay

/**
 * S Engine Ultimate 6.1 [Quantum] - Advanced Quest & Gameplay Systems
 * Quest System v2, Inventory v2, Crafting, Skill Tree, World Time
 */
enum class QuestType { Main, Side, Daily, Weekly, Event, Tutorial, Hidden }
enum class QuestStatus { NotStarted, Active, Completed, Failed, Abandoned }
enum class ObjectiveType { Kill, Collect, Talk, Reach, Escort, Defend, Interact, Custom }

data class QuestObjective(
    var id: String = "obj_0",
    var description: String = "Kill 5 Goblins",
    var type: ObjectiveType = ObjectiveType.Kill,
    var target: String = "Goblin",
    var requiredCount: Int = 5,
    var currentCount: Int = 0,
    var isOptional: Boolean = false,
    var isCompleted: Boolean = false,
    var locationX: Float = 0f, var locationY: Float = 0f, var locationZ: Float = 0f
) {
    fun progress(amount: Int = 1) {
        currentCount+=amount
        if (currentCount >= requiredCount) { currentCount=requiredCount; isCompleted=true }
    }
    fun progressRatio(): Float = if (requiredCount > 0) currentCount.toFloat()/requiredCount else 1f
}

data class QuestReward(
    var xp: Int = 100,
    var gold: Int = 50,
    var items: MutableList<String> = mutableListOf(),
    var reputation: Int = 10
)

data class Quest(
    var id: String = "quest_0",
    var name: String = "Kill Goblins",
    var description: String = "Defeat 5 goblins in the forest",
    var type: QuestType = QuestType.Side,
    var status: QuestStatus = QuestStatus.NotStarted,
    var objectives: MutableList<QuestObjective> = mutableListOf(),
    var rewards: QuestReward = QuestReward(),
    var prerequisites: MutableList<String> = mutableListOf(),
    var nextQuests: MutableList<String> = mutableListOf(),
    var timeLimit: Float = 0f, // 0 = no limit
    var elapsedTime: Float = 0f,
    var isTracked: Boolean = false
) {
    fun isCompleted(): Boolean = objectives.filter { !it.isOptional }.all { it.isCompleted }
    fun progressRatio(): Float = if (objectives.isEmpty()) 0f else objectives.map { it.progressRatio() }.average().toFloat()
    fun addObjective(desc: String, type: ObjectiveType, target: String, count: Int): QuestObjective {
        val obj = QuestObjective(id="obj_${objectives.size}", description=desc, type=type, target=target, requiredCount=count)
        objectives.add(obj); return obj
    }
}

class QuestSystemV2 {
    var quests = mutableMapOf<String, Quest>()
    var activeQuests = mutableListOf<String>()
    var completedQuests = mutableListOf<String>()
    var failedQuests = mutableListOf<String>()
    var totalXP: Int = 0
    var totalGold: Int = 0

    fun createQuest(id: String, name: String, description: String, type: QuestType = QuestType.Side): Quest {
        val q = Quest(id=id, name=name, description=description, type=type); quests[id]=q; return q
    }

    fun createSampleQuests() {
        val q1 = createQuest("q_kill_goblins", "Goblin Menace", "The village is threatened by goblins. Kill 5 of them.", QuestType.Main)
        q1.addObjective("Kill 5 Goblins", ObjectiveType.Kill, "Goblin", 5)
        q1.addObjective("Return to Old Man", ObjectiveType.Talk, "OldMan", 1)
        q1.rewards = QuestReward(xp=100, gold=50, items=mutableListOf("HealthPotion"))
        q1.isTracked = true

        val q2 = createQuest("q_collect_herbs", "Herbalist Request", "Collect 10 Moon Herbs for the herbalist", QuestType.Side)
        q2.addObjective("Collect Moon Herbs", ObjectiveType.Collect, "MoonHerb", 10)
        q2.rewards = QuestReward(xp=50, gold=25)
        q2.prerequisites.add("q_kill_goblins")

        val q3 = createQuest("q_defend_village", "Defend the Village", "Defend against the goblin wave", QuestType.Main)
        q3.addObjective("Defend Village Gate", ObjectiveType.Defend, "VillageGate", 1)
        q3.addObjective("Kill Goblin Chief", ObjectiveType.Kill, "GoblinChief", 1)
        q3.rewards = QuestReward(xp=200, gold=100, items=mutableListOf("IronSword"))
        q3.prerequisites.add("q_kill_goblins")
        q3.timeLimit = 300f // 5 minutes
    }

    fun startQuest(id: String): Boolean {
        val quest = quests[id] ?: return false
        if (quest.status != QuestStatus.NotStarted) return false
        // Check prerequisites
        for (pre in quest.prerequisites) if (pre !in completedQuests) return false
        quest.status = QuestStatus.Active; activeQuests.add(id); return true
    }

    fun completeQuest(id: String): Boolean {
        val quest = quests[id] ?: return false
        if (!quest.isCompleted()) return false
        quest.status = QuestStatus.Completed
        activeQuests.remove(id); completedQuests.add(id)
        totalXP+=quest.rewards.xp; totalGold+=quest.rewards.gold
        // Unlock next quests
        for (nextId in quest.nextQuests) quests[nextId]?.let { if (it.status == QuestStatus.NotStarted) {} }
        return true
    }

    fun failQuest(id: String) {
        val quest = quests[id] ?: return
        quest.status = QuestStatus.Failed; activeQuests.remove(id); failedQuests.add(id)
    }

    fun updateObjective(questId: String, objectiveId: String, amount: Int = 1) {
        val quest = quests[questId] ?: return
        quest.objectives.find { it.id == objectiveId }?.progress(amount)
        if (quest.isCompleted() && quest.status == QuestStatus.Active) completeQuest(questId)
    }

    fun updateObjectiveByTarget(target: String, amount: Int = 1) {
        for (questId in activeQuests) {
            val quest = quests[questId] ?: continue
            for (obj in quest.objectives.filter { !it.isCompleted && it.target == target }) {
                obj.progress(amount)
            }
            if (quest.isCompleted()) completeQuest(questId)
        }
    }

    fun update(dt: Float) {
        for (questId in activeQuests.toList()) {
            val quest = quests[questId] ?: continue
            if (quest.timeLimit > 0f) {
                quest.elapsedTime+=dt
                if (quest.elapsedTime >= quest.timeLimit) failQuest(questId)
            }
        }
    }

    fun getActiveQuests(): List<Quest> = activeQuests.mapNotNull { quests[it] }
    fun getTrackedQuests(): List<Quest> = quests.values.filter { it.isTracked && it.status == QuestStatus.Active }
    fun getStats(): String = "Quests:${quests.size} Active:${activeQuests.size} Completed:${completedQuests.size} XP:$totalXP Gold:$totalGold"
}

// Inventory System V2
enum class ItemRarity { Common, Uncommon, Rare, Epic, Legendary, Mythic }
enum class ItemType { Weapon, Armor, Consumable, Material, Quest, Currency, Tool }

data class Item(
    var id: String = "item_0",
    var name: String = "Health Potion",
    var description: String = "Restores 50 HP",
    var type: ItemType = ItemType.Consumable,
    var rarity: ItemRarity = ItemRarity.Common,
    var stackable: Boolean = true,
    var maxStack: Int = 99,
    var quantity: Int = 1,
    var value: Int = 10,
    var weight: Float = 0.5f,
    var icon: String = "potion_icon",
    var stats: MutableMap<String, Float> = mutableMapOf()
)

data class InventorySlot(
    var item: Item? = null,
    var quantity: Int = 0,
    var isEmpty: Boolean = true
)

class InventorySystemV2 {
    var slots = MutableList(30) { InventorySlot() }
    var gold: Int = 100
    var maxWeight: Float = 100f
    var currentWeight: Float = 0f

    fun addItem(item: Item, quantity: Int = 1): Boolean {
        // Try stack
        if (item.stackable) {
            for (slot in slots) if (!slot.isEmpty && slot.item?.id == item.id && slot.quantity < (slot.item?.maxStack ?: 99)) {
                val space = (slot.item?.maxStack ?: 99) - slot.quantity
                val toAdd = minOf(space, quantity)
                slot.quantity+=toAdd; currentWeight+=item.weight*toAdd
                if (toAdd < quantity) return addItem(item, quantity-toAdd)
                return true
            }
        }
        // Find empty slot
        for (slot in slots) if (slot.isEmpty) {
            slot.item = item.copy(quantity=quantity); slot.quantity=quantity; slot.isEmpty=false
            currentWeight+=item.weight*quantity; return true
        }
        return false
    }

    fun removeItem(itemId: String, quantity: Int = 1): Boolean {
        var remaining = quantity
        for (slot in slots) if (!slot.isEmpty && slot.item?.id == itemId) {
            val toRemove = minOf(slot.quantity, remaining)
            slot.quantity-=toRemove; currentWeight-= (slot.item?.weight ?: 0f)*toRemove
            remaining-=toRemove
            if (slot.quantity <= 0) { slot.item=null; slot.quantity=0; slot.isEmpty=true }
            if (remaining <= 0) return true
        }
        return remaining <= 0
    }

    fun hasItem(itemId: String, quantity: Int = 1): Boolean {
        var total = 0
        for (slot in slots) if (!slot.isEmpty && slot.item?.id == itemId) total+=slot.quantity
        return total >= quantity
    }

    fun getItemCount(itemId: String): Int = slots.filter { !it.isEmpty && it.item?.id == itemId }.sumOf { it.quantity }

    fun getStats(): String = "Inventory ${slots.count { !it.isEmpty }}/${slots.size} Weight:${currentWeight.toInt()}/$maxWeight Gold:$gold"
}

// Skill Tree System
data class SkillNode(
    var id: String = "skill_0",
    var name: String = "Fireball",
    var description: String = "Launches a fireball",
    var level: Int = 0,
    var maxLevel: Int = 5,
    var requiredLevel: Int = 1,
    var cost: Int = 1,
    var prerequisites: MutableList<String> = mutableListOf(),
    var isUnlocked: Boolean = false,
    var x: Float = 0f, var y: Float = 0f // tree position
) {
    fun canUnlock(playerLevel: Int, skillPoints: Int, unlockedSkills: List<String>): Boolean {
        if (isUnlocked && level >= maxLevel) return false
        if (playerLevel < requiredLevel) return false
        if (skillPoints < cost) return false
        for (pre in prerequisites) if (pre !in unlockedSkills) return false
        return true
    }
}

class SkillTreeSystem {
    var nodes = mutableMapOf<String, SkillNode>()
    var skillPoints: Int = 10
    var playerLevel: Int = 1
    var unlockedSkills = mutableListOf<String>()

    fun addNode(id: String, name: String, maxLevel: Int = 5, requiredLevel: Int = 1, cost: Int = 1, x: Float = 0f, y: Float = 0f): SkillNode {
        val node = SkillNode(id=id, name=name, maxLevel=maxLevel, requiredLevel=requiredLevel, cost=cost, x=x, y=y)
        nodes[id]=node; return node
    }

    fun createWarriorTree() {
        addNode("warrior_root", "Warrior Path", 1, 1, 0, 0f, 0f).isUnlocked=true
        addNode("slash", "Slash", 5, 1, 1, -2f, 1f).prerequisites.add("warrior_root")
        addNode("block", "Block", 3, 2, 1, 0f, 1f).prerequisites.add("warrior_root")
        addNode("charge", "Charge", 3, 3, 2, 2f, 1f).prerequisites.add("warrior_root")
        addNode("whirlwind", "Whirlwind", 3, 5, 3, -2f, 2f).prerequisites.add("slash")
        addNode("shield_wall", "Shield Wall", 2, 6, 3, 0f, 2f).prerequisites.add("block")
        addNode("berserk", "Berserk", 1, 10, 5, 0f, 3f).prerequisites.addAll(listOf("whirlwind","shield_wall"))
    }

    fun unlockSkill(id: String): Boolean {
        val node = nodes[id] ?: return false
        if (!node.canUnlock(playerLevel, skillPoints, unlockedSkills)) return false
        skillPoints-=node.cost
        if (!node.isUnlocked) { node.isUnlocked=true; unlockedSkills.add(id) }
        else node.level++
        return true
    }

    fun getStats(): String = "SkillTree Nodes:${nodes.size} Unlocked:${unlockedSkills.size} Points:$skillPoints Level:$playerLevel"
}
