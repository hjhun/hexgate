package dev.hexgate

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry

/** Controlled fixture tests explicitly disable the product's normal startup reconnect. */
fun launchHexgateTestActivity(): ActivityScenario<MainActivity> = ActivityScenario.launch(
    Intent(InstrumentationRegistry.getInstrumentation().targetContext,MainActivity::class.java)
        .putExtra("hexgate.skipAutoConnect",true)
)
