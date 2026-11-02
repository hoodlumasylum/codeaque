import QtQuick

// The start-up intro screen. With no video of your own it shows CodeAque's splash (Code's artwork) with a live loading
// bar, plus a soft original sound. With your video, a video player covers the screen and this
// is just the black backdrop. Any key, click or controller button skips (handled in Main.qml / the backend).
Item {
    id: intro
    property real u: 1
    readonly property bool own: backend.introMode === "default"
    property real shown: 0                 // 0..1 fade/zoom in, then out at the end

    Rectangle { anchors.fill: parent; color: "black" }

    // Code's splash (assets/splash.png, 1672x941, its bar and dots emptied). It covers the screen (cropped evenly on
    // 4:3 screens such as 640x480; the bar and dots are in the middle so they always show) and the bar fills / the dots
    // light up over it while CodeAque starts.
    Item {
        id: splash
        anchors.fill: parent
        visible: intro.own
        opacity: intro.shown
        // cover the screen, but never cut into the CODEAQUE wordmark (about 80% of the art's width): on 4:3 screens a little
        // of the sides is cropped and thin black bands stay above and below
        readonly property real k: Math.min(Math.max(width / 1672, height / 941), width * 0.96 / (1672 * 0.80))
        readonly property real ox: (width - 1672 * k) / 2
        readonly property real oy: (height - 941 * k) / 2
        property real progress: 0
        property int dot: 0
        Image {
            objectName: "introSplash"
            x: splash.ox; y: splash.oy; width: 1672 * splash.k; height: 941 * splash.k
            source: Qt.resolvedUrl("../assets/splash.png")
            fillMode: Image.Stretch
            smooth: true; mipmap: true
        }
        Rectangle {        // the loading bar fill
            x: splash.ox + 523 * splash.k; y: splash.oy + 682 * splash.k
            height: 19 * splash.k; radius: height / 2
            width: Math.max(height, 627 * splash.k * splash.progress)
            gradient: Gradient {
                GradientStop { position: 0.0; color: "#ff6a6a" }
                GradientStop { position: 0.35; color: "#ff1a1a" }
                GradientStop { position: 1.0; color: "#a80000" }
            }
        }
        Repeater {         // the four dots: one lit at a time
            model: 4
            Rectangle {
                x: splash.ox + (757 + index * 46) * splash.k; y: splash.oy + 741 * splash.k
                width: 19 * splash.k; height: width; radius: width * 0.15
                visible: index === splash.dot
                color: "#ff2a2a"
            }
        }
        NumberAnimation on progress { from: 0; to: 1; duration: 3200; easing.type: Easing.InOutCubic; running: intro.visible && intro.own }
        Timer { interval: 400; repeat: true; running: intro.visible && intro.own; onTriggered: splash.dot = (splash.dot + 1) % 4 }
    }

    Rectangle { id: blackout; anchors.fill: parent; color: "black"; opacity: 0 }

    Text {
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 34 * intro.u
        anchors.horizontalCenter: parent.horizontalCenter
        text: "Press any key to skip"
        color: "#6b7090"
        font.pixelSize: 20 * intro.u
        opacity: 0.8
    }

    SequentialAnimation {
        running: intro.visible && intro.own
        NumberAnimation { target: intro; property: "shown"; from: 0; to: 1; duration: Theme.anim ? 500 : 1; easing.type: Easing.OutCubic }
        PauseAnimation { duration: Theme.anim ? 2300 : 2700 }
        NumberAnimation { target: blackout; property: "opacity"; from: 0; to: 1; duration: Theme.anim ? 900 : 1 }
    }
    MouseArea { anchors.fill: parent; cursorShape: Qt.BlankCursor; onClicked: backend.skipIntro() }     // no mouse pointer during the intro
}
