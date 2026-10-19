package dev.hexgate

import com.termux.view.TerminalView
import org.junit.Assert.*
import org.junit.Test

class TerminalOwnershipTest {
    @Test fun retiredViewCannotResizeActiveTerminalAfterReattachment() {
        launchHexgateTestActivity().use { scenario ->
            scenario.onActivity { activity ->
                val model=activity.model
                fun view(width:Int):TerminalView = TerminalView(activity,null).apply {
                    setTextSize(20);setTerminalViewClient(model.viewClient(this))
                    layout(0,0,width,600)
                }
                val previous=view(1600)
                model.attach(previous)
                val current=view(600)
                model.attach(current)
                val columns=model.terminal.emulator.mColumns
                previous.layout(0,0,1500,700)
                assertEquals("Overlapping old Fold window resized the active terminal",columns,model.terminal.emulator.mColumns)
                model.detach(previous) // Old Activity's onRelease can arrive after the new attach.
                previous.layout(0,0,1400,700)
                assertEquals("Retired Fold window resized the active terminal",columns,model.terminal.emulator.mColumns)
                model.detach(current)
            }
        }
    }
}
