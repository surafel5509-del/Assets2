package com.sengine.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Commands carry executable actions, not decorative menu entries. */
object CommandPalette {
    data class Command(val title: String, val category: String, val enabled: Boolean = true, val action: () -> Unit)

    fun show(context: Context, commands: List<Command>) {
        val content = context.vbox().apply { setPadding(context.dp(20), context.dp(8), context.dp(20), context.dp(8)) }
        val search = context.field("").apply { hint = "Search commands…"; contentDescription = "Search editor commands" }
        val list = ListView(context).apply { dividerHeight = 0 }
        val available = commands.filter { it.enabled }
        var filtered = available
        val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, mutableListOf<String>()) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View =
                super.getView(position, convertView, parent).also { (it as TextView).apply { setTextColor(C.TEXT); textSize = 14f } }
        }
        list.adapter = adapter
        fun refresh(query: String) {
            val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            filtered = available.filter { cmd -> words.all { "${cmd.category} ${cmd.title}".contains(it, true) } }
            adapter.clear()
            adapter.addAll(filtered.map { "${it.category}  /  ${it.title}" })
        }
        search.onTextChanged { refresh(it) }
        content.addView(search, lp(MATCH, context.dp(44)))
        content.addView(list, lp(MATCH, context.dp(280)))
        val dialog = MaterialAlertDialogBuilder(context).setTitle("Command palette").setView(content).setNegativeButton("Close", null).create()
        list.setOnItemClickListener { _, _, position, _ -> dialog.dismiss(); filtered[position].action() }
        search.setOnEditorActionListener { _, _, _ ->
            if (filtered.isNotEmpty()) { dialog.dismiss(); filtered.first().action(); true } else false
        }
        refresh("")
        dialog.show()
        search.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
}

fun android.widget.EditText.onTextChanged(action: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action(s?.toString().orEmpty()) }
        override fun afterTextChanged(s: Editable?) {}
    })
}
