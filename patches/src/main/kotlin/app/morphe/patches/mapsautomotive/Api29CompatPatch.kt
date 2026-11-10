package app.morphe.patches.mapsautomotive

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import java.util.logging.Logger

// Android 9 (API 28) support. R8's minSdk 30 also dropped the SDK_INT >= 29 checks, so Maps calls
// API 29 members unguarded, nearly all through API model outlines. The members in api29Shims are
// redirected to Api29Compat together with the API 30 shims. Every other API 29 outline in
// api29OutlineMembers keeps its call on API 29+ and returns a default below: nothing, 0, false,
// null, or the receiver for builder setters. Android 9 has Insets and WindowInsets.inset as hidden
// APIs (the process is exempted from their checks) and the extension backports WindowInsets.Builder,
// so those members are linked as they are.

private const val API29_COMPAT_CLASS = "Lapp/morphe/extension/mapsautomotive/patches/Api29Compat;"
private const val SDK_INT_FIELD = "Landroid/os/Build\$VERSION;->SDK_INT:I"
private val api29Logger = Logger.getLogger("app.morphe.patches.mapsautomotive.Api29CompatPatch")

internal val api29Shims = listOf(
    ApiShim("Landroid/view/WindowInsets;->getSystemWindowInsets()Landroid/graphics/Insets;", "getSystemWindowInsets", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/WindowInsets;->getSystemGestureInsets()Landroid/graphics/Insets;", "getSystemGestureInsets", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/WindowInsets;->getMandatorySystemGestureInsets()Landroid/graphics/Insets;", "getMandatorySystemGestureInsets", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/WindowInsets;->getTappableElementInsets()Landroid/graphics/Insets;", "getTappableElementInsets", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/content/Context;->getOpPackageName()Ljava/lang/String;", "getOpPackageName", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/app/Service;->startForeground(ILandroid/app/Notification;I)V", "startForeground", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/content/Context;->bindService(Landroid/content/Intent;ILjava/util/concurrent/Executor;Landroid/content/ServiceConnection;)Z", "bindService", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/app/Activity;->registerActivityLifecycleCallbacks(Landroid/app/Application\$ActivityLifecycleCallbacks;)V", "registerActivityLifecycleCallbacks", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/os/Environment;->isExternalStorageLegacy()Z", "isExternalStorageLegacy", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/accessibility/AccessibilityManager;->getRecommendedTimeoutMillis(II)I", "getRecommendedTimeoutMillis", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->getTransitionAlpha()F", "getTransitionAlpha", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->setTransitionAlpha(F)V", "setTransitionAlpha", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->setTransitionVisibility(I)V", "setTransitionVisibility", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->setLeftTopRightBottom(IIII)V", "setLeftTopRightBottom", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->transformMatrixToGlobal(Landroid/graphics/Matrix;)V", "transformMatrixToGlobal", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->transformMatrixToLocal(Landroid/graphics/Matrix;)V", "transformMatrixToLocal", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/ViewGroup;->suppressLayout(Z)V", "suppressLayout", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->getUniqueDrawingId()J", "getUniqueDrawingId", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/View;->getAccessibilityDelegate()Landroid/view/View\$AccessibilityDelegate;", "getAccessibilityDelegate", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/webkit/WebView;->getAccessibilityDelegate()Landroid/view/View\$AccessibilityDelegate;", "getAccessibilityDelegate", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/widget/PopupWindow;->setEpicenterBounds(Landroid/graphics/Rect;)V", "setEpicenterBounds", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/widget/PopupWindow;->setTouchModal(Z)V", "setTouchModal", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/DisplayCutout;->getBoundingRectTop()Landroid/graphics/Rect;", "getBoundingRectTop", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/DisplayCutout;->getBoundingRectLeft()Landroid/graphics/Rect;", "getBoundingRectLeft", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/DisplayCutout;->getBoundingRectRight()Landroid/graphics/Rect;", "getBoundingRectRight", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/DisplayCutout;->getBoundingRectBottom()Landroid/graphics/Rect;", "getBoundingRectBottom", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/MotionEvent;->getRawX(I)F", "getRawX", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/view/MotionEvent;->getRawY(I)F", "getRawY", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/content/res/Resources;->getFloat(I)F", "getFloat", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/os/Parcel;->writeParcelableList(Ljava/util/List;I)V", "writeParcelableList", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/os/Parcel;->readParcelableList(Ljava/util/List;Ljava/lang/ClassLoader;)Ljava/util/List;", "readParcelableList", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Canvas;->enableZ()V", "enableZ", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Canvas;->disableZ()V", "disableZ", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Canvas;->drawColor(J)V", "drawColor", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Canvas;->drawDoubleRoundRect(Landroid/graphics/RectF;FFLandroid/graphics/RectF;FFLandroid/graphics/Paint;)V", "drawDoubleRoundRect", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Canvas;->drawDoubleRoundRect(Landroid/graphics/RectF;[FLandroid/graphics/RectF;[FLandroid/graphics/Paint;)V", "drawDoubleRoundRect", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Paint;->setColor(J)V", "setColor", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Paint;->getColorLong()J", "getColorLong", compatClass = API29_COMPAT_CLASS),
    ApiShim("Landroid/graphics/Paint;->getTextBounds(Ljava/lang/CharSequence;IILandroid/graphics/Rect;)V", "getTextBounds", compatClass = API29_COMPAT_CLASS),
)

// Fields without their type ("Lowner;->name"), methods as "Lowner;->name(params)return".
private val api29OutlineMembers = setOf(
    "Landroid/app/ActivityManager;->isRunningInUserTestHarness()Z",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPostCreated(Landroid/app/Activity;Landroid/os/Bundle;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPostDestroyed(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPostPaused(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPostResumed(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPostSaveInstanceState(Landroid/app/Activity;Landroid/os/Bundle;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPostStarted(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPostStopped(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPreCreated(Landroid/app/Activity;Landroid/os/Bundle;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPreDestroyed(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPrePaused(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPreResumed(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPreSaveInstanceState(Landroid/app/Activity;Landroid/os/Bundle;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPreStarted(Landroid/app/Activity;)V",
    "Landroid/app/Application\$ActivityLifecycleCallbacks;->onActivityPreStopped(Landroid/app/Activity;)V",
    "Landroid/app/Notification\$Action\$Builder;->setContextual(Z)Landroid/app/Notification\$Action\$Builder;",
    "Landroid/app/Notification\$Builder;->setAllowSystemGeneratedContextualActions(Z)Landroid/app/Notification\$Builder;",
    "Landroid/app/Notification\$Builder;->setBubbleMetadata(Landroid/app/Notification\$BubbleMetadata;)Landroid/app/Notification\$Builder;",
    "Landroid/app/NotificationManager;->getNotificationDelegate()Ljava/lang/String;",
    "Landroid/app/NotificationManager;->setNotificationDelegate(Ljava/lang/String;)V",
    "Landroid/app/RemoteInput\$Builder;->setEditChoicesBeforeSending(I)Landroid/app/RemoteInput\$Builder;",
    "Landroid/content/pm/ShortcutInfo\$Builder;->setLongLived(Z)Landroid/content/pm/ShortcutInfo\$Builder;",
    "Landroid/content/pm/ShortcutInfo\$Builder;->setPersons([Landroid/app/Person;)Landroid/content/pm/ShortcutInfo\$Builder;",
    "Landroid/content/res/Resources\$Theme;->rebase()V",
    "Landroid/graphics/BlendMode;->CLEAR",
    "Landroid/graphics/BlendMode;->DARKEN",
    "Landroid/graphics/BlendMode;->DST",
    "Landroid/graphics/BlendMode;->DST_ATOP",
    "Landroid/graphics/BlendMode;->DST_IN",
    "Landroid/graphics/BlendMode;->DST_OUT",
    "Landroid/graphics/BlendMode;->DST_OVER",
    "Landroid/graphics/BlendMode;->LIGHTEN",
    "Landroid/graphics/BlendMode;->MODULATE",
    "Landroid/graphics/BlendMode;->OVERLAY",
    "Landroid/graphics/BlendMode;->PLUS",
    "Landroid/graphics/BlendMode;->SCREEN",
    "Landroid/graphics/BlendMode;->SRC",
    "Landroid/graphics/BlendMode;->SRC_ATOP",
    "Landroid/graphics/BlendMode;->SRC_IN",
    "Landroid/graphics/BlendMode;->SRC_OUT",
    "Landroid/graphics/BlendMode;->SRC_OVER",
    "Landroid/graphics/BlendMode;->XOR",
    "Landroid/graphics/Canvas;->drawColor(ILandroid/graphics/BlendMode;)V",
    "Landroid/graphics/Canvas;->drawColor(JLandroid/graphics/BlendMode;)V",
    "Landroid/graphics/Canvas;->drawRenderNode(Landroid/graphics/RenderNode;)V",
    "Landroid/graphics/Canvas;->drawTextRun(Landroid/graphics/text/MeasuredText;IIIIFFZLandroid/graphics/Paint;)V",
    "Landroid/graphics/Paint;->setBlendMode(Landroid/graphics/BlendMode;)V",
    "Landroid/graphics/RenderNode;->beginRecording()Landroid/graphics/RecordingCanvas;",
    "Landroid/graphics/RenderNode;->discardDisplayList()V",
    "Landroid/graphics/RenderNode;->endRecording()V",
    "Landroid/graphics/RenderNode;->getMatrix(Landroid/graphics/Matrix;)V",
    "Landroid/graphics/RenderNode;->hasDisplayList()Z",
    "Landroid/graphics/RenderNode;->setAlpha(F)Z",
    "Landroid/graphics/RenderNode;->setAmbientShadowColor(I)Z",
    "Landroid/graphics/RenderNode;->setCameraDistance(F)Z",
    "Landroid/graphics/RenderNode;->setClipToBounds(Z)Z",
    "Landroid/graphics/RenderNode;->setClipToOutline(Z)Z",
    "Landroid/graphics/RenderNode;->setElevation(F)Z",
    "Landroid/graphics/RenderNode;->setHasOverlappingRendering(Z)Z",
    "Landroid/graphics/RenderNode;->setOutline(Landroid/graphics/Outline;)Z",
    "Landroid/graphics/RenderNode;->setPivotX(F)Z",
    "Landroid/graphics/RenderNode;->setPivotY(F)Z",
    "Landroid/graphics/RenderNode;->setPosition(IIII)Z",
    "Landroid/graphics/RenderNode;->setScaleX(F)Z",
    "Landroid/graphics/RenderNode;->setScaleY(F)Z",
    "Landroid/graphics/RenderNode;->setSpotShadowColor(I)Z",
    "Landroid/graphics/RenderNode;->setTranslationX(F)Z",
    "Landroid/graphics/RenderNode;->setUseCompositingLayer(ZLandroid/graphics/Paint;)Z",
    "Landroid/graphics/Typeface\$CustomFallbackBuilder;->addCustomFallback(Landroid/graphics/fonts/FontFamily;)Landroid/graphics/Typeface\$CustomFallbackBuilder;",
    "Landroid/graphics/Typeface\$CustomFallbackBuilder;->build()Landroid/graphics/Typeface;",
    "Landroid/graphics/Typeface\$CustomFallbackBuilder;->setStyle(Landroid/graphics/fonts/FontStyle;)Landroid/graphics/Typeface\$CustomFallbackBuilder;",
    "Landroid/graphics/Typeface\$CustomFallbackBuilder;->setSystemFallback(Ljava/lang/String;)Landroid/graphics/Typeface\$CustomFallbackBuilder;",
    "Landroid/graphics/drawable/ColorStateListDrawable;->getColorStateList()Landroid/content/res/ColorStateList;",
    "Landroid/graphics/drawable/Drawable;->isProjected()Z",
    "Landroid/graphics/fonts/Font\$Builder;->build()Landroid/graphics/fonts/Font;",
    "Landroid/graphics/fonts/Font\$Builder;->setFontVariationSettings(Ljava/lang/String;)Landroid/graphics/fonts/Font\$Builder;",
    "Landroid/graphics/fonts/Font\$Builder;->setSlant(I)Landroid/graphics/fonts/Font\$Builder;",
    "Landroid/graphics/fonts/Font\$Builder;->setTtcIndex(I)Landroid/graphics/fonts/Font\$Builder;",
    "Landroid/graphics/fonts/Font\$Builder;->setWeight(I)Landroid/graphics/fonts/Font\$Builder;",
    "Landroid/graphics/fonts/Font;->getFile()Ljava/io/File;",
    "Landroid/graphics/fonts/Font;->getStyle()Landroid/graphics/fonts/FontStyle;",
    "Landroid/graphics/fonts/FontFamily\$Builder;->addFont(Landroid/graphics/fonts/Font;)Landroid/graphics/fonts/FontFamily\$Builder;",
    "Landroid/graphics/fonts/FontFamily\$Builder;->build()Landroid/graphics/fonts/FontFamily;",
    "Landroid/graphics/fonts/FontFamily;->getFont(I)Landroid/graphics/fonts/Font;",
    "Landroid/graphics/fonts/FontFamily;->getSize()I",
    "Landroid/graphics/fonts/FontStyle;->getSlant()I",
    "Landroid/graphics/fonts/FontStyle;->getWeight()I",
    "Landroid/net/ssl/SSLSockets;->isSupportedSocket(Ljavax/net/ssl/SSLSocket;)Z",
    "Landroid/net/ssl/SSLSockets;->setEchConfigList(Ljavax/net/ssl/SSLSocket;Landroid/net/ssl/EchConfigList;)V",
    "Landroid/net/ssl/SSLSockets;->setUseSessionTickets(Ljavax/net/ssl/SSLSocket;Z)V",
    "Landroid/os/PowerManager;->addThermalStatusListener(Ljava/util/concurrent/Executor;Landroid/os/PowerManager\$OnThermalStatusChangedListener;)V",
    "Landroid/os/PowerManager;->getCurrentThermalStatus()I",
    "Landroid/os/PowerManager;->removeThermalStatusListener(Landroid/os/PowerManager\$OnThermalStatusChangedListener;)V",
    "Landroid/os/Trace;->beginAsyncSection(Ljava/lang/String;I)V",
    "Landroid/os/Trace;->endAsyncSection(Ljava/lang/String;I)V",
    "Landroid/os/Trace;->isEnabled()Z",
    "Landroid/os/Trace;->setCounter(Ljava/lang/String;J)V",
    "Landroid/view/MotionEvent;->getClassification()I",
    "Landroid/view/TouchDelegate;->getTouchDelegateInfo()Landroid/view/accessibility/AccessibilityNodeInfo\$TouchDelegateInfo;",
    "Landroid/view/View;->getContentCaptureSession()Landroid/view/contentcapture/ContentCaptureSession;",
    "Landroid/view/View;->getVerticalScrollbarThumbDrawable()Landroid/graphics/drawable/Drawable;",
    "Landroid/view/View;->saveAttributeDataForStyleable(Landroid/content/Context;[ILandroid/util/AttributeSet;Landroid/content/res/TypedArray;II)V",
    "Landroid/view/View;->setForceDarkAllowed(Z)V",
    "Landroid/view/View;->setSystemGestureExclusionRects(Ljava/util/List;)V",
    "Landroid/view/Window;->setNavigationBarContrastEnforced(Z)V",
    "Landroid/view/accessibility/AccessibilityNodeInfo\$AccessibilityAction;->ACTION_PAGE_DOWN",
    "Landroid/view/accessibility/AccessibilityNodeInfo\$AccessibilityAction;->ACTION_PAGE_LEFT",
    "Landroid/view/accessibility/AccessibilityNodeInfo\$AccessibilityAction;->ACTION_PAGE_RIGHT",
    "Landroid/view/accessibility/AccessibilityNodeInfo\$AccessibilityAction;->ACTION_PAGE_UP",
    "Landroid/view/accessibility/AccessibilityNodeInfo\$TouchDelegateInfo;->getRegionAt(I)Landroid/graphics/Region;",
    "Landroid/view/accessibility/AccessibilityNodeInfo\$TouchDelegateInfo;->getRegionCount()I",
    "Landroid/view/accessibility/AccessibilityNodeInfo;->setTextEntryKey(Z)V",
    "Landroid/view/contentcapture/ContentCaptureSession;->newAutofillId(Landroid/view/autofill/AutofillId;J)Landroid/view/autofill/AutofillId;",
    "Landroid/view/contentcapture/ContentCaptureSession;->newVirtualViewStructure(Landroid/view/autofill/AutofillId;J)Landroid/view/ViewStructure;",
    "Landroid/view/contentcapture/ContentCaptureSession;->notifyViewAppeared(Landroid/view/ViewStructure;)V",
    "Landroid/view/contentcapture/ContentCaptureSession;->notifyViewDisappeared(Landroid/view/autofill/AutofillId;)V",
    "Landroid/view/contentcapture/ContentCaptureSession;->notifyViewTextChanged(Landroid/view/autofill/AutofillId;Ljava/lang/CharSequence;)V",
    "Landroid/view/contentcapture/ContentCaptureSession;->notifyViewsDisappeared(Landroid/view/autofill/AutofillId;[J)V",
    "Landroid/widget/AutoCompleteTextView;->refreshAutoCompleteResults()V",
    "Landroid/widget/EditText;->getTextCursorDrawable()Landroid/graphics/drawable/Drawable;",
    "Landroid/widget/PopupWindow;->setIsClippedToScreen(Z)V",
    "Ljavax/net/ssl/SSLParameters;->setApplicationProtocols([Ljava/lang/String;)V",
    "Ljavax/net/ssl/SSLSocket;->getApplicationProtocol()Ljava/lang/String;",
)

private fun outlineMember(key: String) = if ('(' in key) key else key.substringBefore(':')

// dexlib2's Opcode.name is the smali mnemonic, such as "invoke-virtual/range".
private fun smaliOpcode(opcode: Opcode): String {
    val name = opcode.name.removeSuffix("/range")
    return if (name.startsWith("invoke-")) "$name/range" else name
}

private fun registerSize(type: CharSequence) = if (type == "J" || type == "D") 2 else 1

// The outline body again, behind an SDK_INT >= 29 check, with the default value below.
private fun guardedOutline(outline: Method, owner: String, call: ReferenceInstruction, key: String): String {
    val parameterRegisters = outline.parameterTypes.sumOf { registerSize(it) }
    val returnType = outline.returnType
    val invoke = when (call.reference) {
        is FieldReference ->
            if (call.opcode.name.startsWith("iget")) "${call.opcode.name} v0, p0, $key" else "${call.opcode.name} v0, $key"
        else -> {
            if (parameterRegisters == 0) {
                "${smaliOpcode(call.opcode).removeSuffix("/range")} { }, $key"
            } else {
                "${smaliOpcode(call.opcode)} { p0 .. p${parameterRegisters - 1} }, $key"
            }
        }
    }
    val result = when {
        call.reference is FieldReference -> ""
        returnType == "V" -> ""
        registerSize(returnType) == 2 -> "move-result-wide v0"
        returnType.startsWith("L") || returnType.startsWith("[") -> "move-result-object v0"
        else -> "move-result v0"
    }
    fun returnOf(register: String) = when {
        returnType == "V" -> "return-void"
        registerSize(returnType) == 2 -> "return-wide $register"
        returnType.startsWith("L") || returnType.startsWith("[") -> "return-object $register"
        else -> "return $register"
    }
    val isStaticMember = call.opcode == Opcode.INVOKE_STATIC || call.opcode == Opcode.INVOKE_STATIC_RANGE ||
        call.reference is FieldReference
    val fallback = when {
        returnType == "V" -> "return-void"
        !isStaticMember && returnType == owner -> "return-object p0"
        registerSize(returnType) == 2 -> "const-wide v0, 0x0\n${returnOf("v0")}"
        else -> "const/4 v0, 0x0\n${returnOf("v0")}"
    }
    return listOf(
        "sget v0, $SDK_INT_FIELD",
        "const/16 v1, 0x1d",
        "if-lt v0, v1, :morphe_pre_q",
        invoke,
        result,
        returnOf("v0"),
        ":morphe_pre_q",
        fallback,
    ).filter { it.isNotEmpty() }.joinToString("\n")
}

private class GuardedOutline(val outline: Method, val owner: String, val call: ReferenceInstruction, val key: String)

internal fun BytecodePatchContext.guardApi29Outlines() {
    val targets = mutableListOf<GuardedOutline>()
    classDefForEach { classDef ->
        if (!isMapsOutline(classDef.type)) return@classDefForEach
        classDef.methods.forEach { method ->
            val instructions = method.implementation?.instructions?.toList() ?: return@forEach
            val call = instructions.firstOrNull() as? ReferenceInstruction ?: return@forEach
            val key = memberKey(call) ?: return@forEach
            if (outlineMember(key) !in api29OutlineMembers) return@forEach
            if (!AccessFlags.STATIC.isSet(method.accessFlags) || instructions.size > 3 ||
                call.opcode == Opcode.INVOKE_DIRECT || call.opcode == Opcode.INVOKE_DIRECT_RANGE ||
                call.opcode == Opcode.INVOKE_SUPER || call.opcode == Opcode.INVOKE_SUPER_RANGE
            ) {
                throw PatchException("Unexpected API 29 outline layout for $key: $method")
            }
            val owner = when (val reference = call.reference) {
                is MethodReference -> reference.definingClass
                is FieldReference -> reference.definingClass
                else -> throw PatchException("Unexpected API 29 outline reference in $method")
            }
            targets += GuardedOutline(method, owner, call, key)
        }
    }

    targets.forEach { target ->
        val outline = target.outline
        val mutableClass = mutableClassDefBy(outline.definingClass)
        val parameterRegisters = outline.parameterTypes.sumOf { registerSize(it) }
        val guarded = ImmutableMethod(
            outline.definingClass,
            outline.name,
            outline.parameterTypes.map { ImmutableMethodParameter(it.toString(), null, null) },
            outline.returnType,
            outline.accessFlags,
            null,
            null,
            MutableMethodImplementation(parameterRegisters + 2),
        ).toMutable().apply {
            addInstructionsWithLabels(0, guardedOutline(outline, target.owner, target.call, target.key))
        }
        val removed = mutableClass.methods.removeIf {
            it.name == outline.name && it.returnType == outline.returnType &&
                it.parameterTypes.map { type -> type.toString() } == outline.parameterTypes.map { type -> type.toString() }
        }
        if (!removed) throw PatchException("Could not replace API 29 outline $outline")
        mutableClass.methods.add(guarded)
    }
    api29Logger.info("Guarded ${targets.size} API 29 outline methods (${targets.map { it.key }.distinct().size} members)")
}
