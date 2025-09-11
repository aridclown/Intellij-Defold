package com.aridclown.intellij.defold.debugger

import com.aridclown.intellij.defold.debugger.value.MobRValue
import com.aridclown.intellij.defold.debugger.value.MobRValue.Num
import com.aridclown.intellij.defold.debugger.value.MobRValue.Str
import com.aridclown.intellij.defold.debugger.value.MobVariable
import com.intellij.icons.AllIcons
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XNumericValuePresentation
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Basic XValue implementation showing the string representation of a variable.
 */
class MobDebugValue(
    private val variable: MobVariable,
    private val protocol: MobDebugProtocol,
    private val frameIndex: Int,
    private val expr: String
) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val v = variable.value
        val xValuePresentation = when (v) {
            is Str -> object : XStringValuePresentation(v.value) {
                override fun getType() = v.typeLabel
            }

            is Num -> object : XNumericValuePresentation(v.value) {
                override fun getType() = v.typeLabel
            }

            else -> XRegularValuePresentation(v.preview, v.typeLabel)
        }

        // Simple icon hint: tables as object, others no icon
        val icon = if (v is MobRValue.Table) AllIcons.Json.Object else null
        node.setPresentation(icon, xValuePresentation, v.hasChildren)
    }

    override fun computeChildren(node: XCompositeNode) {
        if ((variable.value as? MobRValue.Table) == null) {
            node.addChildren(XValueChildrenList.EMPTY, true)
            return
        }
        // EmmyLua approach: evaluate "return <expr>" and reconstruct the value client-side with LuaJ
        val chunk = "return $expr"
        protocol.exec(chunk, frame = frameIndex, onResult = { body ->
            try {
                val globals = JsePlatform.standardGlobals()
                val code = globals.load(body, "exec_result").call()
                val serialized = code.get(1) // serpent line for the value
                val reconstructed = globals.load("local _=" + serialized.tojstring() + " return _", "recon").call()

                if (!reconstructed.istable()) {
                    node.addChildren(XValueChildrenList.EMPTY, true)
                    return@exec
                }
                val table = reconstructed.checktable()
                val keys = table.keys()
                val list = XValueChildrenList()
                for (k in keys) {
                    val childName = try { k.tojstring() } catch (_: Throwable) { k.toString() }
                    val childVal = table.get(k)
                    val rv = MobRValue.fromRawLuaValue(childVal)
                    val childVar = MobVariable(childName, rv)
                    val childExpr = buildChildExpr(expr, childName)
                    list.add(childName, MobDebugValue(childVar, protocol, frameIndex, childExpr))
                }
                node.addChildren(list, true)
            } catch (_: Throwable) {
                node.addChildren(XValueChildrenList.EMPTY, true)
            }
        }, onError = {
            node.addChildren(XValueChildrenList.EMPTY, true)
        })
    }
}


private fun buildChildExpr(parentExpr: String, keyName: String): String {
    return if (keyName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
        "$parentExpr.$keyName"
    } else {
        // numeric index
        if (keyName.matches(Regex("-?\\d+(?:\\.\\d+)?"))) "$parentExpr[$keyName]" else "$parentExpr[${luaStringLiteral(keyName)}]"
    }
}

private fun luaStringLiteral(s: String): String = buildString {
    append('"')
    s.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\t' -> append("\\t")
            '\r' -> append("\\r")
            else -> append(ch)
        }
    }
    append('"')
}
