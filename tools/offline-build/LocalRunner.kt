import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

/**
 * Minimal JUnit-alike runner used by the offline toolchain:
 *  - every @Test method runs in a fresh instance of the class
 *  - AssumptionViolatedException  -> SKIP
 *  - any other failure            -> FAIL (collected)
 *  - prints RESULT passed=… failed=… skipped=… and exits 1 on failure
 */
fun main(args: Array<String>) {
    var passed = 0
    var failed = 0
    var skipped = 0
    val failures = ArrayList<String>()

    for (clsName in args) {
        val cls = Class.forName(clsName)
        val tests = cls.declaredMethods
            .filter { m -> m.annotations.any { it.annotationClass.qualifiedName == "org.junit.Test" } }
            .sortedBy { it.name }
        for (m in tests) {
            val label = "$clsName.${m.name}"
            try {
                val inst = cls.getDeclaredConstructor().newInstance()
                m.isAccessible = true
                if (Modifier.isStatic(m.modifiers)) m.invoke(null) else m.invoke(inst)
                println("PASS $label")
                passed++
            } catch (e: InvocationTargetException) {
                val c = e.cause ?: e
                if (c is org.junit.AssumptionViolatedException) {
                    println("SKIP $label (${c.message ?: "assumption failed"})")
                    skipped++
                } else {
                    println("FAIL $label: $c")
                    failures.add(label)
                    failed++
                }
            } catch (e: Throwable) {
                println("FAIL $label: $e")
                failures.add(label)
                failed++
            }
        }
    }

    println("RESULT passed=$passed failed=$failed skipped=$skipped")
    if (failed > 0) {
        println("FAILURES: $failures")
        kotlin.system.exitProcess(1)
    }
}
