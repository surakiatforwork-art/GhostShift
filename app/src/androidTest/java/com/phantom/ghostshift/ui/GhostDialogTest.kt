package com.phantom.ghostshift.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import com.phantom.ghostshift.ui.theme.LocalGhostDark
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import org.junit.Assert.*

class GhostDialogTest {
    private data class Bounds(val x:Float,val y:Float,val w:Int,val h:Int,val density:Float)
    private fun measure(dark:Boolean,fontScale:Float):Pair<Bounds,Bounds> {
        val left=AtomicReference<Bounds>()
        val right=AtomicReference<Bounds>()
        val ready=CountDownLatch(2)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        val density=LocalDensity.current.density
                        CompositionLocalProvider(LocalGhostDark provides dark, LocalDensity provides Density(density,fontScale)) {
                            GhostDialog(onDismissRequest={}, kind=if(dark) DialogKind.Delete else DialogKind.Download,
                                title={Text("GhostShift")}, text={Text("0000")},
                                dismissButton={
                                    GhostDialogButton(onClick={}, tone=UtilityTone.Neutral, modifier=Modifier.onGloballyPositioned {
                                        val p=it.positionInRoot()
                                        if(left.getAndSet(Bounds(p.x,p.y,it.size.width,it.size.height,density))==null)ready.countDown()
                                    }) {Text("ยกเลิก")}
                                },
                                confirmButton={
                                    GhostDialogButton(onClick={}, modifier=Modifier.onGloballyPositioned {
                                        val p=it.positionInRoot()
                                        if(right.getAndSet(Bounds(p.x,p.y,it.size.width,it.size.height,density))==null)ready.countDown()
                                    }) {Text("ดาวน์โหลด")}
                                })
                        }
                    }
                }
            }
            assertTrue("Dialog buttons were laid out",ready.await(20,TimeUnit.SECONDS))
            return checkNotNull(left.get()) to checkNotNull(right.get())
        }
    }
    @Test fun pairedActionsEqualWidth() {
        val (a,b)=measure(false,1f)
        assertTrue(kotlin.math.abs(a.w-b.w)<=2)
        assertTrue(a.h>=48*a.density && b.h>=48*b.density)
        assertTrue("Buttons must not overlap",a.x+a.w<=b.x || b.y+b.h<=a.y)
    }
    @Test fun darkLargeTextActionsDoNotOverlap() {
        val (a,b)=measure(true,1.5f)
        assertTrue(kotlin.math.abs(a.w-b.w)<=2)
        assertTrue(a.h>=48*a.density && b.h>=48*b.density)
        assertTrue(a.x+a.w<=b.x || b.y+b.h<=a.y)
    }
}
