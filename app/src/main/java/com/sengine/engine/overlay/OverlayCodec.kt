package com.sengine.engine.overlay

import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes `.ctrl.json` and `.ui.json` files. */
object OverlayCodec {

    fun toJson(doc: OverlayDocument): JSONObject {
        val els = JSONArray()
        for (e in doc.elements) {
            els.put(
                JSONObject()
                    .put("id", e.id)
                    .put("type", e.type.name)
                    .put("name", e.name)
                    .put("x", e.x.toDouble()).put("y", e.y.toDouble())
                    .put("w", e.w.toDouble()).put("h", e.h.toDouble())
                    .put("hAnchor", e.hAnchor.name)
                    .put("vAnchor", e.vAnchor.name)
                    .put("text", e.text)
                    .put("color", e.color)
                    .put("background", e.background)
                    .put("opacity", e.opacity.toDouble())
                    .put("shape", e.shape.name)
                    .put("fontSize", e.fontSize.toDouble())
                    .put("action", e.action)
                    .put("value", e.value.toDouble())
                    .put("visible", e.visible)
                    .put("locked", e.locked),
            )
        }
        return JSONObject()
            .put("format", Overlay.FORMAT)
            .put("name", doc.name)
            .put("kind", doc.kind.name)
            .put("designAspect", doc.designAspect.toDouble())
            .put("elements", els)
    }

    /** Parses a layout. Unknown enum values fall back to safe defaults rather than failing the whole file. */
    fun fromJson(o: JSONObject, fallbackName: String = "Layout"): OverlayDocument {
        val kind = enumOr(Kind.values(), o.optString("kind"), Kind.UI)
        val doc = OverlayDocument(o.optString("name", fallbackName), kind)
        doc.designAspect = o.optDouble("designAspect", 16.0 / 9.0).toFloat()
        val els = o.optJSONArray("elements") ?: JSONArray()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val type = enumOr(ElementType.values(), e.optString("type"), ElementType.PANEL)
            doc.elements.add(
                OverlayElement(
                    id = e.optString("id", "el_${i + 1}"),
                    type = type,
                    name = e.optString("name", type.label),
                    x = e.optDouble("x", 0.0).toFloat(),
                    y = e.optDouble("y", 0.0).toFloat(),
                    w = e.optDouble("w", 0.1).toFloat(),
                    h = e.optDouble("h", 0.1).toFloat(),
                    hAnchor = enumOr(HAnchor.values(), e.optString("hAnchor"), HAnchor.LEFT),
                    vAnchor = enumOr(VAnchor.values(), e.optString("vAnchor"), VAnchor.TOP),
                    text = e.optString("text", ""),
                    color = e.optInt("color", 0xFFFFFFFF.toInt()),
                    background = e.optInt("background", 0),
                    opacity = e.optDouble("opacity", 1.0).toFloat(),
                    shape = enumOr(Shape.values(), e.optString("shape"), Shape.ROUNDED),
                    fontSize = e.optDouble("fontSize", 0.035).toFloat(),
                    action = e.optString("action", ""),
                    value = e.optDouble("value", 1.0).toFloat(),
                    visible = e.optBoolean("visible", true),
                    locked = e.optBoolean("locked", false),
                ),
            )
        }
        return doc
    }

    private inline fun <reified T : Enum<T>> enumOr(values: Array<T>, name: String, default: T): T =
        values.firstOrNull { it.name == name } ?: default
}
