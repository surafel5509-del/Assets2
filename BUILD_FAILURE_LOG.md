To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.7/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon will be stopped at the end of the build 
> Task :app:checkKotlinGradlePluginConfigurationErrors
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:generateDebugBuildConfig
> Task :app:generateDebugResValues
> Task :app:checkDebugAarMetadata
> Task :app:mapDebugSourceSetPaths
> Task :app:generateDebugResources
> Task :app:packageDebugResources
> Task :app:mergeDebugResources
> Task :app:createDebugCompatibleScreenManifests
> Task :app:extractDeepLinksDebug
> Task :app:parseDebugLocalResources
> Task :app:processDebugMainManifest
> Task :app:processDebugManifest
> Task :app:processDebugManifestForPackage
> Task :app:processDebugResources
> Task :app:compileDebugKotlin
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/ai/AISystem.kt:16:5 Property must be initialized
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/ai/AISystem.kt:213:7 Redeclaration: Blackboard
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/ai/EQSSystem.kt:181:12 Redeclaration: Blackboard
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/anim/advanced/AnimatorController.kt:43:66 Unresolved reference: copy
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:525:18 Conflicting declarations: public open val type: String, public final var type: Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:526:9 Conflicting declarations: public open val type: String, public final var type: Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:526:9 'type' hides member of supertype 'Component' and needs 'override' modifier
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:551:100 Type mismatch: inferred type is Unit but Int was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:551:102 Overload resolution ambiguity: 
public final var type: Int defined in com.sengine.engine.core.LightPro
public open val type: String defined in com.sengine.engine.core.LightPro
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:551:112 Overload resolution ambiguity: 
public final var type: Int defined in com.sengine.engine.core.LightPro
public open val type: String defined in com.sengine.engine.core.LightPro
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:562:18 Conflicting declarations: public open val type: String, public final var type: Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:564:9 Conflicting declarations: public open val type: String, public final var type: Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:564:9 'type' hides member of supertype 'Component' and needs 'override' modifier
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:569:115 Type mismatch: inferred type is Unit but Int was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:569:117 Overload resolution ambiguity: 
public final var type: Int defined in com.sengine.engine.core.SkyboxComponent
public open val type: String defined in com.sengine.engine.core.SkyboxComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:569:127 Overload resolution ambiguity: 
public final var type: Int defined in com.sengine.engine.core.SkyboxComponent
public open val type: String defined in com.sengine.engine.core.SkyboxComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1026:9 'enabled' hides member of supertype 'Component' and needs 'override' modifier
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1201:7 Redeclaration: OffMeshLinkComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1280:18 Conflicting declarations: public open val type: String, public final var type: Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1284:9 Conflicting declarations: public open val type: String, public final var type: Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1284:9 'type' hides member of supertype 'Component' and needs 'override' modifier
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1292:76 Type mismatch: inferred type is Unit but Int was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1292:78 Overload resolution ambiguity: 
public final var type: Int defined in com.sengine.engine.core.ImageComponent
public open val type: String defined in com.sengine.engine.core.ImageComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1292:88 Overload resolution ambiguity: 
public final var type: Int defined in com.sengine.engine.core.ImageComponent
public open val type: String defined in com.sengine.engine.core.ImageComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/Components.kt:1458:7 Redeclaration: ScriptableObjectComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:11:1 Class 'Light2DComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:12:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:29:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:29:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:29:22 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:29:22 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:29:36 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:30:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:30:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:30:23 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:30:23 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:30:39 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:31:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:31:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:31:27 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:31:27 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:31:43 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:32:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:32:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:32:24 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:32:24 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:32:40 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:32:48 Cannot find a parameter with this name: min
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:32:56 Cannot find a parameter with this name: max
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:33:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:33:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:33:23 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:33:23 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:33:39 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:33:47 Cannot find a parameter with this name: min
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:33:55 Cannot find a parameter with this name: max
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:34:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:34:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:34:29 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:34:29 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:34:45 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:35:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:35:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:35:28 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:35:28 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:35:44 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:36:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:36:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:36:25 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:36:25 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:36:40 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:37:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:37:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:37:28 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:37:28 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:37:42 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:38:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:38:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:38:34 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:38:34 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:38:48 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:39:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:39:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:39:28 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:39:28 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:39:42 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:41:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:56:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:57:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:58:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:62:1 Class 'ShadowCaster2DComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:63:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:70:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:70:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:70:29 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:70:29 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:70:44 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:71:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:71:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:71:29 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:71:29 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:71:44 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:72:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:72:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:72:33 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:72:33 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:72:49 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:74:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:81:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:82:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:83:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:87:1 Class 'TilemapCollider2DComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:88:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:93:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:93:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:93:71 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:93:71 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:93:86 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:94:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:95:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:96:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:97:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:101:1 Class 'TileComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:102:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:62 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:62 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:79 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:88 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:88 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:102 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:102 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:116 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:124 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:124 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:138 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:138 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:108:152 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:109:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:117:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:118:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:119:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:123:1 Class 'SortingGroupComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:124:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:68 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:68 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:82 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:97 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:97 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:118 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:118 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:132 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:147 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:147 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:166 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:166 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:129:181 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:130:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:137:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:138:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:139:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:143:1 Class 'Bone2DComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:144:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:64 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:64 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:81 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:92 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:92 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:111 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:111 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:128 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:141 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:141 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:156 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:156 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:149:172 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:150:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:157:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:158:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:159:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:163:1 Class 'IK2DComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:164:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:62 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:62 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:79 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:88 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:88 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:102 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:102 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:119 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:127 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:127 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:142 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:142 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:170:158 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:171:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:179:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:180:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:181:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:185:1 Class 'SpriteLibraryComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:186:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:189:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:189:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:189:63 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:189:63 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:189:80 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:190:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:191:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:192:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:193:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:196:1 Class 'SpriteResolverComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:197:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:64 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:64 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:81 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:92 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:92 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:106 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:106 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:201:123 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:202:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:208:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:209:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:210:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:214:1 Class 'CinemachineVirtualCamera2DComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:215:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:230:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:230:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:230:24 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:230:24 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:230:41 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:231:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:231:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:231:26 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:231:26 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:231:40 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:232:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:232:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:232:22 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:232:22 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:232:36 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:233:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:233:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:233:31 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:233:31 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:233:47 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:234:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:234:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:234:32 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:234:32 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:234:48 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:235:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:235:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:235:26 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:235:26 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:235:42 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:236:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:236:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:236:26 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:236:26 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:236:42 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:238:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:251:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:252:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:253:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:257:1 Class 'ParticleSystem2DComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:258:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:268:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:268:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:268:26 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:268:26 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:268:42 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:269:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:269:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:269:22 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:269:22 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:269:37 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:270:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:270:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:270:31 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:270:31 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:270:47 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:271:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:271:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:271:28 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:271:28 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:271:44 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:272:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:272:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:272:30 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:272:30 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:272:44 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:273:9 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:273:9 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:273:30 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:273:30 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:273:46 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:275:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:285:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:286:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:287:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:291:1 Class 'FullControlComponent' is not abstract and does not implement abstract base class member public abstract val type: String defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:292:5 'name' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:295:47 Cannot access '<init>': it is protected in 'Prop'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:295:47 Sealed types cannot be instantiated
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:295:68 Unresolved reference: PropType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:295:68 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:295:82 Too many arguments for protected constructor Prop(name: String) defined in com.sengine.engine.core.Prop
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:296:5 'setProp' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:297:5 'toJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:298:5 'fromJson' in 'Component' is final and cannot be overridden
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/TitanComponents.kt:299:5 'duplicate' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:8:1 Class 'BurstJobComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:13:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:16:1 Class 'AssemblyDefinitionComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:22:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:25:1 Class 'ScriptableObjectComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:25:7 Redeclaration: ScriptableObjectComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:30:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:30:66 Unresolved reference: soName
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:30:84 Unresolved reference: soType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:30:102 Unresolved reference: guid
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:33:1 Class 'ClusteredRenderingComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:39:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:42:1 Class 'ShadowCSMComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:50:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:53:1 Class 'UpscalerComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:59:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:62:1 Class 'VolumetricCloudsV2Component' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:70:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:73:1 Class 'WaterV2Component' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:83:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:86:1 Class 'PhysicsQueryComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:91:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:94:1 Class 'ConfigurableJointComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:103:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:106:1 Class 'TireFrictionComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:113:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:116:1 Class 'AnimationEventComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:123:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:126:1 Class 'BlendShapeComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:131:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:134:1 Class 'PoseLibraryComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:139:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:142:1 Class 'MotionWarpingV2Component' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:148:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:151:1 Class 'AudioMixerSnapshotComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:156:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:159:1 Class 'AudioPortalComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:166:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:169:1 Class 'ReverbProbeComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:174:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:177:1 Class 'NavMeshAreaComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:181:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:184:1 Class 'OffMeshLinkComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:184:7 Redeclaration: OffMeshLinkComponent
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:61 Unresolved reference: linkType
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:83 Unresolved reference: startX
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:101 Unresolved reference: startY
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:119 Unresolved reference: startZ
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:137 Unresolved reference: endX
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:151 Unresolved reference: endY
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:165 Unresolved reference: endZ
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:191:179 Unresolved reference: bidirectional
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:194:1 Class 'PerceptionComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:200:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:203:1 Class 'CoverPointComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:209:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:212:1 Class 'GOAPComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:217:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:220:1 Class 'InterestManagementComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:227:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:230:1 Class 'ClientPredictionComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:236:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:239:1 Class 'VoiceChat3DComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:246:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:249:1 Class 'LobbyComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:255:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:258:1 Class 'InputRebindingComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:263:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:266:1 Class 'SaveSystemComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:273:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:276:1 Class 'EditorWindowComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:281:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:284:1 Class 'XRComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:289:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:292:1 Class 'ARPlaneComponent' is not abstract and does not implement abstract base class member public abstract fun props(): List<Prop> defined in com.sengine.engine.core.Component
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/core/UltraComponents.kt:297:5 'clone' overrides nothing
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:181:29 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
public fun <T : Comparable<TypeVariable(T)>> Array<out TypeVariable(T)>.min(): TypeVariable(T) defined in kotlin.collections
public fun Array<out Double>.min(): Double defined in kotlin.collections
public fun Array<out Float>.min(): Float defined in kotlin.collections
public fun ByteArray.min(): Byte defined in kotlin.collections
public fun CharArray.min(): Char defined in kotlin.collections
public fun CharSequence.min(): Char defined in kotlin.text
public fun DoubleArray.min(): Double defined in kotlin.collections
public fun FloatArray.min(): Float defined in kotlin.collections
public fun IntArray.min(): Int defined in kotlin.collections
public fun LongArray.min(): Long defined in kotlin.collections
public fun ShortArray.min(): Short defined in kotlin.collections
public fun UByteArray.min(): UByte defined in kotlin.collections
public fun UIntArray.min(): UInt defined in kotlin.collections
public fun ULongArray.min(): ULong defined in kotlin.collections
public fun UShortArray.min(): UShort defined in kotlin.collections
public fun <T : Comparable<TypeVariable(T)>> Iterable<TypeVariable(T)>.min(): TypeVariable(T) defined in kotlin.collections
public fun Iterable<Double>.min(): Double defined in kotlin.collections
public fun Iterable<Float>.min(): Float defined in kotlin.collections
public fun <T : Comparable<TypeVariable(T)>> Sequence<TypeVariable(T)>.min(): TypeVariable(T) defined in kotlin.sequences
public fun Sequence<Double>.min(): Double defined in kotlin.sequences
public fun Sequence<Float>.min(): Float defined in kotlin.sequences
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:182:30 Overload resolution ambiguity: 
public final operator fun plus(other: Byte): Int defined in kotlin.Int
public final operator fun plus(other: Double): Double defined in kotlin.Int
public final operator fun plus(other: Float): Float defined in kotlin.Int
public final operator fun plus(other: Int): Int defined in kotlin.Int
public final operator fun plus(other: Long): Long defined in kotlin.Int
public final operator fun plus(other: Short): Int defined in kotlin.Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:182:65 Overload resolution ambiguity: 
public final operator fun times(other: Byte): Float defined in kotlin.Float
public final operator fun times(other: Double): Double defined in kotlin.Float
public final operator fun times(other: Float): Float defined in kotlin.Float
public final operator fun times(other: Int): Float defined in kotlin.Float
public final operator fun times(other: Long): Float defined in kotlin.Float
public final operator fun times(other: Short): Float defined in kotlin.Float
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:183:68 Overload resolution ambiguity: 
public final operator fun minus(other: Byte): Int defined in kotlin.Int
public final operator fun minus(other: Double): Double defined in kotlin.Int
public final operator fun minus(other: Float): Float defined in kotlin.Int
public final operator fun minus(other: Int): Int defined in kotlin.Int
public final operator fun minus(other: Long): Long defined in kotlin.Int
public final operator fun minus(other: Short): Int defined in kotlin.Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:198:28 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
public fun <T : Comparable<TypeVariable(T)>> Array<out TypeVariable(T)>.min(): TypeVariable(T) defined in kotlin.collections
public fun Array<out Double>.min(): Double defined in kotlin.collections
public fun Array<out Float>.min(): Float defined in kotlin.collections
public fun ByteArray.min(): Byte defined in kotlin.collections
public fun CharArray.min(): Char defined in kotlin.collections
public fun CharSequence.min(): Char defined in kotlin.text
public fun DoubleArray.min(): Double defined in kotlin.collections
public fun FloatArray.min(): Float defined in kotlin.collections
public fun IntArray.min(): Int defined in kotlin.collections
public fun LongArray.min(): Long defined in kotlin.collections
public fun ShortArray.min(): Short defined in kotlin.collections
public fun UByteArray.min(): UByte defined in kotlin.collections
public fun UIntArray.min(): UInt defined in kotlin.collections
public fun ULongArray.min(): ULong defined in kotlin.collections
public fun UShortArray.min(): UShort defined in kotlin.collections
public fun <T : Comparable<TypeVariable(T)>> Iterable<TypeVariable(T)>.min(): TypeVariable(T) defined in kotlin.collections
public fun Iterable<Double>.min(): Double defined in kotlin.collections
public fun Iterable<Float>.min(): Float defined in kotlin.collections
public fun <T : Comparable<TypeVariable(T)>> Sequence<TypeVariable(T)>.min(): TypeVariable(T) defined in kotlin.sequences
public fun Sequence<Double>.min(): Double defined in kotlin.sequences
public fun Sequence<Float>.min(): Float defined in kotlin.sequences
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:199:26 Overload resolution ambiguity: 
public final operator fun minus(other: Byte): Int defined in kotlin.Int
public final operator fun minus(other: Double): Double defined in kotlin.Int
public final operator fun minus(other: Float): Float defined in kotlin.Int
public final operator fun minus(other: Int): Int defined in kotlin.Int
public final operator fun minus(other: Long): Long defined in kotlin.Int
public final operator fun minus(other: Short): Int defined in kotlin.Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:199:79 Overload resolution ambiguity: 
public final operator fun times(other: Byte): Float defined in kotlin.Float
public final operator fun times(other: Double): Double defined in kotlin.Float
public final operator fun times(other: Float): Float defined in kotlin.Float
public final operator fun times(other: Int): Float defined in kotlin.Float
public final operator fun times(other: Long): Float defined in kotlin.Float
public final operator fun times(other: Short): Float defined in kotlin.Float
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/QuestSystemV2.kt:200:22 Overload resolution ambiguity: 
public final operator fun minus(other: Byte): Int defined in kotlin.Int
public final operator fun minus(other: Double): Double defined in kotlin.Int
public final operator fun minus(other: Float): Float defined in kotlin.Int
public final operator fun minus(other: Int): Int defined in kotlin.Int
public final operator fun minus(other: Long): Long defined in kotlin.Int
public final operator fun minus(other: Short): Int defined in kotlin.Int
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/WorldTimeSystem.kt:123:48 Unresolved reference: altRad
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/WorldTimeSystem.kt:123:74 Unresolved reference: altRad
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/gameplay/WorldTimeSystem.kt:126:23 Type mismatch: inferred type is Double but Float was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/render/SkyboxSystem.kt:122:21 This type is final, so it cannot be inherited from
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:612:69 Type mismatch: inferred type is Float but Double was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:618:27 Type mismatch: inferred type is UndoRecord? but Boolean was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:619:27 Type mismatch: inferred type is UndoRecord? but Boolean was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:630:81 Unresolved reference: init
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:632:71 Too many arguments for public final fun assignLights(): Unit defined in com.sengine.engine.render.ClusteredForwardPlus
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:641:47 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
public operator fun MatchGroupCollection.get(name: String): MatchGroup? defined in kotlin.text
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:641:50 No get method providing array access
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:641:66 Unresolved reference. None of the following candidates is applicable because of receiver type mismatch: 
public operator fun MatchGroupCollection.get(name: String): MatchGroup? defined in kotlin.text
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:641:69 No get method providing array access
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:644:111 No value passed for parameter 'time'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:658:119 Type mismatch: inferred type is Float but List<Collider2DV2> was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:658:133 Too many arguments for public final fun overlapBox(centerX: Float, centerY: Float, halfX: Float, halfY: Float, colliders: List<Collider2DV2>): List<Collider2DV2> defined in com.sengine.engine.physics.PhysicsQuerySystem
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:658:147 Too many arguments for public final fun overlapBox(centerX: Float, centerY: Float, halfX: Float, halfY: Float, colliders: List<Collider2DV2>): List<Collider2DV2> defined in com.sengine.engine.physics.PhysicsQuerySystem
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:659:58 Unresolved reference: objectId
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:662:108 Type mismatch: inferred type is Float but List<Collider2DV2> was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:662:126 Too many arguments for public final fun overlapSphere(centerX: Float, centerY: Float, radius: Float, colliders: List<Collider2DV2>): List<Collider2DV2> defined in com.sengine.engine.physics.PhysicsQuerySystem
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:663:58 Unresolved reference: objectId
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:717:97 Type mismatch: inferred type is String but Long was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:719:75 Type inference failed. The value of the type parameter K should be mentioned in input types (argument types, receiver type or expected type). Try to specify it explicitly.
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:719:192 Type mismatch: inferred type is String but Long was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:722:16 Type inference failed. The value of the type parameter K should be mentioned in input types (argument types, receiver type or expected type). Try to specify it explicitly.
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:722:157 No value passed for parameter 'selfDirX'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:722:157 No value passed for parameter 'selfDirZ'
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/script/Api.kt:727:59 Type mismatch: inferred type is String but GOAPGoal was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/world/FoliageSystem.kt:99:43 Type mismatch: inferred type is Float? but Float was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/world/WorldPartitionSystem.kt:98:84 Unresolved reference: cell
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/world/WorldPartitionSystem.kt:98:119 Unresolved reference: cell
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/world/WorldPartitionSystem.kt:98:154 Unresolved reference: cell
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/engine/world/WorldPartitionSystem.kt:98:189 Unresolved reference: cell
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1692:74 Type mismatch: inferred type is String but Int was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1692:88 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1693:16 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1695:63 Type mismatch: inferred type is String but Int was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1695:77 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1696:118 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1704:76 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1705:16 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1706:176 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1706:209 Unresolved reference: it
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1706:212 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1707:114 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1707:151 Unresolved reference: it
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1707:154 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1715:74 Type mismatch: inferred type is String but Int was expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1715:86 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1716:16 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1717:63 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1725:74 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1726:16 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1735:76 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1736:16 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1737:110 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1745:76 Unresolved reference: col2
e: file:///home/runner/work/Assets2/Assets2/app/src/main/java/com/sengine/project/Templates.kt:1746:16 Variable expected
e: file:///home/runner/work/Assets2/Assets2/app/src/ma