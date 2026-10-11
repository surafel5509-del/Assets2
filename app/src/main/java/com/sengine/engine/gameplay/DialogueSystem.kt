package com.sengine.engine.gameplay

/**
 * S Engine Ultimate 6.1 [Quantum] - Dialogue System
 * Branching dialogues, conditions, localization, voice-over
 */
enum class DialogueNodeType { Line, Choice, Condition, Action, End, Random, SetVariable }

data class DialogueLine(
    var character: String = "NPC",
    var text: String = "Hello!",
    var localizedKey: String = "",
    var voiceClip: String = "",
    var duration: Float = 3f,
    var emotion: String = "Neutral",
    var animation: String = ""
)

data class DialogueChoice(
    var text: String = "Choice",
    var localizedKey: String = "",
    var nextNodeId: String = "",
    var condition: String = "",
    var isAvailable: Boolean = true
)

data class DialogueNode(
    var id: String = "node_0",
    var type: DialogueNodeType = DialogueNodeType.Line,
    var line: DialogueLine = DialogueLine(),
    var choices: MutableList<DialogueChoice> = mutableListOf(),
    var nextNodeId: String = "",
    var conditions: MutableList<String> = mutableListOf(),
    var actions: MutableList<String> = mutableListOf(),
    var variables: MutableMap<String, Any> = mutableMapOf()
)

data class Dialogue(
    var name: String = "Dialogue",
    var nodes: MutableMap<String, DialogueNode> = mutableMapOf(),
    var startNodeId: String = "node_0",
    var variables: MutableMap<String, Any> = mutableMapOf(),
    var localized: Boolean = true
) {
    fun addNode(id: String, type: DialogueNodeType = DialogueNodeType.Line): DialogueNode {
        val node = DialogueNode(id=id, type=type); nodes[id]=node; return node
    }
    fun getNode(id: String): DialogueNode? = nodes[id]
}

class DialogueSystem {
    var dialogues = mutableMapOf<String, Dialogue>()
    var currentDialogue: Dialogue? = null
    var currentNode: DialogueNode? = null
    var isActive: Boolean = false
    var history = mutableListOf<DialogueLine>()
    var variables = mutableMapOf<String, Any>()
    var onDialogueLine: ((DialogueLine)->Unit)? = null
    var onDialogueChoice: ((List<DialogueChoice>)->Unit)? = null
    var onDialogueEnd: (()->Unit)? = null

    fun createDialogue(name: String): Dialogue {
        val d = Dialogue(name=name); dialogues[name]=d; return d
    }

    fun createSampleDialogue(): Dialogue {
        val d = createDialogue("SampleQuest")
        val n0 = d.addNode("node_0", DialogueNodeType.Line)
        n0.line = DialogueLine(character="Old Man", text="Greetings, traveler! The village is in danger.", emotion="Worried")
        n0.nextNodeId = "node_1"

        val n1 = d.addNode("node_1", DialogueNodeType.Choice)
        n1.choices.add(DialogueChoice(text="What danger?", nextNodeId="node_2"))
        n1.choices.add(DialogueChoice(text="I don't have time.", nextNodeId="node_end"))

        val n2 = d.addNode("node_2", DialogueNodeType.Line)
        n2.line = DialogueLine(character="Old Man", text="Goblins have taken the eastern bridge! We need a hero.", emotion="Desperate")
        n2.nextNodeId = "node_3"

        val n3 = d.addNode("node_3", DialogueNodeType.Choice)
        n3.choices.add(DialogueChoice(text="I'll help! (Accept Quest)", nextNodeId="node_accept"))
        n3.choices.add(DialogueChoice(text="What's the reward?", nextNodeId="node_reward"))
        n3.choices.add(DialogueChoice(text="Not interested.", nextNodeId="node_end"))

        val nReward = d.addNode("node_reward", DialogueNodeType.Line)
        nReward.line = DialogueLine(character="Old Man", text="100 gold and the gratitude of the village!", emotion="Hopeful")
        nReward.nextNodeId = "node_3"

        val nAccept = d.addNode("node_accept", DialogueNodeType.Line)
        nAccept.line = DialogueLine(character="Old Man", text="Thank you, hero! Return when the goblins are defeated.", emotion="Grateful")
        nAccept.actions.add("AcceptQuest:KillGoblins")
        nAccept.nextNodeId = "node_end"

        val nEnd = d.addNode("node_end", DialogueNodeType.End)
        nEnd.line = DialogueLine(character="", text="", emotion="")

        d.startNodeId = "node_0"
        return d
    }

    fun startDialogue(name: String): Boolean {
        val dialogue = dialogues[name] ?: return false
        currentDialogue = dialogue
        currentNode = dialogue.getNode(dialogue.startNodeId)
        isActive = true
        history.clear()
        variables.putAll(dialogue.variables)
        advance()
        return true
    }

    fun advance() {
        val node = currentNode ?: run { endDialogue(); return }
        when (node.type) {
            DialogueNodeType.Line -> {
                history.add(node.line)
                onDialogueLine?.invoke(node.line)
                // Auto advance if next node is not choice
                val nextId = node.nextNodeId
                if (nextId.isNotEmpty()) {
                    currentNode = currentDialogue?.getNode(nextId)
                    if (currentNode?.type == DialogueNodeType.Choice) {
                        onDialogueChoice?.invoke(currentNode!!.choices.filter { it.isAvailable })
                    } else if (currentNode?.type == DialogueNodeType.Line) {
                        // Wait for player input to advance - for now auto advance after duration
                    }
                } else {
                    endDialogue()
                }
            }
            DialogueNodeType.Choice -> {
                onDialogueChoice?.invoke(node.choices.filter { it.isAvailable })
            }
            DialogueNodeType.End -> endDialogue()
            else -> {
                if (node.nextNodeId.isNotEmpty()) {
                    currentNode = currentDialogue?.getNode(node.nextNodeId)
                    advance()
                } else endDialogue()
            }
        }
    }

    fun selectChoice(index: Int) {
        val node = currentNode ?: return
        if (node.type != DialogueNodeType.Choice) return
        if (index !in node.choices.indices) return
        val choice = node.choices[index]
        if (!choice.isAvailable) return
        // Execute condition check
        currentNode = currentDialogue?.getNode(choice.nextNodeId)
        advance()
    }

    fun nextLine() {
        val node = currentNode ?: return
        if (node.type == DialogueNodeType.Line && node.nextNodeId.isNotEmpty()) {
            currentNode = currentDialogue?.getNode(node.nextNodeId)
            advance()
        }
    }

    fun endDialogue() {
        isActive = false
        currentDialogue = null
        currentNode = null
        onDialogueEnd?.invoke()
    }

    fun setVariable(key: String, value: Any) { variables[key]=value }
    fun getVariable(key: String): Any? = variables[key]

    fun getStats(): String = "Dialogues:${dialogues.size} Active:$isActive History:${history.size}"
}
