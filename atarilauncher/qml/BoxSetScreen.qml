import QtQuick
import QtQuick.Window

// 0.10.32 Box Set Mode: full-screen Trilogy + Co-op covers for an adult box-set.
// Leave with the visible Exit button, B / Esc, or hold Start / Select (~1s). No PIN.
Item {
    id: bs
    anchors.fill: parent
    visible: backend.boxSetActive
    property int ti: 0
    readonly property var tiles: backend.boxSetTiles
    readonly property int n: tiles ? tiles.length : 0
    readonly property int idx: Math.min(ti, Math.max(0, n - 1))
    readonly property var tile: n ? tiles[idx] : null
    readonly property real s: Math.max(0.5, Math.min(width / 1280, height / 720))
    readonly property real tw: 168 * s
    readonly property real th: 250 * s
    readonly property real gap: 14 * s

    function handleKey(e) {
        var k = e.key
        if (k === Qt.Key_F10 || k === Qt.Key_Menu || k === Qt.Key_Tab) {
            if (!e.isAutoRepeat) holdTimer.restart()
            return
        }
        if (k === Qt.Key_Left) move(-1)
        else if (k === Qt.Key_Right) move(1)
        else if (k === Qt.Key_PageUp) move(-4)
        else if (k === Qt.Key_PageDown) move(4)
        else if (k === Qt.Key_Return || k === Qt.Key_Enter) activate()
        else if (k === Qt.Key_Escape || k === Qt.Key_B || k === Qt.Key_Backspace) leave()
    }
    function handleRelease(e) {
        var k = e.key
        if (k === Qt.Key_F10 || k === Qt.Key_Menu || k === Qt.Key_Tab) holdTimer.stop()
    }
    function move(d) {
        if (!n) return
        var v = Math.max(0, Math.min(n - 1, idx + d))
        if (v !== ti) { ti = v; backend.playSound("hhmove") }
    }
    function activate() {
        if (!tile || !tile.action) return
        backend.playSound("hhselect")
        if (tile.action.indexOf("steamstore:") === 0) backend.openSteamStore(tile.action.substring(11))
        else if (tile.action.indexOf("store:") === 0) backend.openStorePage(tile.action.substring(6))
    }
    function leave() {
        holdTimer.stop()
        backend.playSound("hhback")
        backend.leaveBoxSet()
    }

    Timer { id: holdTimer; interval: 1000; repeat: false; onTriggered: leave() }

    Rectangle { anchors.fill: parent; color: "#000000" }
    Rectangle {
        width: parent.width * 1.1; height: parent.height * 0.7
        x: -parent.width * 0.05; y: parent.height * 0.28
        radius: height / 2
        gradient: Gradient {
            GradientStop { position: 0.0; color: "#00000000" }
            GradientStop { position: 0.5; color: "#18203a28" }
            GradientStop { position: 1.0; color: "#00000000" }
        }
    }

    Text {
        id: heading
        x: 28 * bs.s; y: 18 * bs.s
        text: "Box Set"
        color: "#f2efe6"
        font.pixelSize: 36 * bs.s; font.bold: true
    }
    Text {
        anchors.left: heading.left; anchors.top: heading.bottom; anchors.topMargin: 2 * bs.s
        text: tile && tile.note ? ("" + tile.note) : "Trilogies and co-op. Steam store links only. Press Exit or hold Start / Select to leave."
        color: "#a8b0b8"
        font.pixelSize: 14 * bs.s
        width: parent.width - 220 * bs.s
        wrapMode: Text.WordWrap
    }

    Rectangle {
        id: exitBtn
        objectName: "boxSetExit"
        anchors.right: parent.right
        anchors.rightMargin: 22 * bs.s
        anchors.top: parent.top
        anchors.topMargin: 16 * bs.s
        width: exitLab.implicitWidth + 36 * bs.s
        height: 48 * bs.s
        radius: height / 2
        color: "#e6c43a28"
        border.color: "#f0d878"
        border.width: 2 * bs.s
        Text {
            id: exitLab
            anchors.centerIn: parent
            text: "Exit"
            color: "#fff4c8"
            font.pixelSize: 20 * bs.s; font.bold: true
        }
        MouseArea { anchors.fill: parent; onClicked: bs.leave() }
    }

    Flickable {
        id: row
        objectName: "boxSetRow"
        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        anchors.topMargin: 100 * bs.s
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 24 * bs.s
        anchors.leftMargin: 24 * bs.s
        anchors.rightMargin: 24 * bs.s
        clip: true
        interactive: true
        flickableDirection: Flickable.HorizontalFlick
        contentWidth: Math.max(width, bs.n * (bs.tw + bs.gap) - bs.gap)
        contentHeight: height
        contentX: {
            var x = bs.idx * (bs.tw + bs.gap) - (width - bs.tw) / 2
            return Math.max(0, Math.min(Math.max(0, contentWidth - width), x))
        }
        Behavior on contentX { NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }

        Repeater {
            model: bs.tiles
            Item {
                required property var modelData
                required property int index
                readonly property bool on: index === bs.idx
                readonly property string art: modelData.art || ""
                readonly property bool fit: modelData.fit === true
                x: index * (bs.tw + bs.gap)
                y: (parent.height - bs.th) / 2
                width: bs.tw; height: bs.th
                scale: on ? 1.06 : 1
                z: on ? 2 : 0
                Behavior on scale { NumberAnimation { duration: 120 } }

                Rectangle {
                    id: box
                    anchors.fill: parent
                    radius: 10 * bs.s
                    color: "#141820"
                    border.color: on ? "#f0d878" : "#33404c"
                    border.width: on ? 3 * bs.s : 1 * bs.s
                    clip: true
                    Image {
                        id: artImg
                        anchors.fill: parent
                        anchors.margins: box.border.width
                        source: art
                        fillMode: fit ? Image.PreserveAspectFit : Image.PreserveAspectCrop
                        asynchronous: true
                        visible: art !== "" && status !== Image.Error
                    }
                    Text {
                        visible: !artImg.visible
                        anchors.centerIn: parent
                        width: parent.width - 12 * bs.s
                        wrapMode: Text.WordWrap
                        horizontalAlignment: Text.AlignHCenter
                        text: modelData.title
                        color: "#d8dde2"
                        font.pixelSize: 16 * bs.s
                    }
                }
                Text {
                    anchors.top: box.bottom
                    anchors.topMargin: 6 * bs.s
                    anchors.horizontalCenter: box.horizontalCenter
                    width: box.width
                    horizontalAlignment: Text.AlignHCenter
                    elide: Text.ElideRight
                    text: modelData.title
                    color: on ? "#fff4c8" : "#c4c8ce"
                    font.pixelSize: 13 * bs.s
                    font.bold: on
                }
                MouseArea {
                    anchors.fill: parent
                    anchors.bottomMargin: -28 * bs.s
                    onClicked: { bs.ti = index; bs.activate() }
                }
            }
        }
    }
}
