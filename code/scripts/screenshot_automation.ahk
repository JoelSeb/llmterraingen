#Requires AutoHotkey v2.0

automationRunning := false  ; Keeps track of whether automation is running
runCount := 0               ; Counter for automation runs
maxRuns := 2              ; Maximum number of runs

; Hotkey to toggle automation on/off
t:: {
    global automationRunning, runCount, maxRuns  ; Declare global variables
    automationRunning := !automationRunning  ; Toggle the state
    if (automationRunning) {
        SetTimer(Automation, 1000)  ; Start the automation loop
        runCount := 0  ; Reset the counter when starting
        Tooltip("Automation Started (0 / " maxRuns ")", , 1000)
    } else {
        SetTimer(Automation, 0)  ; Stop the automation loop
        Tooltip("Automation Stopped", , 1000)
        Sleep(1000)
        Tooltip("")  ; Clear the tooltip
    }
}

Automation() {
    global automationRunning, runCount, maxRuns  ; Declare global variables

    if (runCount >= maxRuns) {
        automationRunning := false
        SetTimer(Automation, 0)  ; Stop after max runs
        Tooltip("Automation Stopped (Max runs reached)", , 1000)
        Sleep(1000)
        Tooltip("")  ; Clear the tooltip
        return
    }

    runCount++  ; Increment the run count
    Tooltip("Running... (" runCount " / " maxRuns ")", , 1000)

    Send("1")          ; Press the "1" key
    Click("R")         ; Perform a right-click
    Sleep(20000)       ; Wait for 20 seconds
    Send("2")          ; Press the "2" key
    Click("R")         ; Perform another right-click
    Sleep(2000)       ; Wait for 2 seconds
}
