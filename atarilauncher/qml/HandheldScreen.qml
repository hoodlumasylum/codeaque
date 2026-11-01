import QtQuick
import QtQuick.Effects

// CodeAque Handheld Edition (0.7.0): a home made for a small handheld screen (laid out for 1280x720).
// Top: status bar. A row of categories (the chosen one bigger and glowing), a big glowing CODEAQUE wordmark over a flowing
// green wave, and a row of tiles for the chosen category at the bottom (Games: Cloud Gaming first, then your retro systems).
// A tile with games opens a sliding list. Everything by controller; plain text system names and CodeAque's own line drawings,
// no company logos and no console maker's button symbols. Light on weak chips: no shaders unless effects are on, a fixed
// pool of list rows for any number of games, fewer light motes with Reduce effects, nothing moves with Reduce animations.
FocusScope {
    id: hh
    property var cats: backend.handheldCategories
    property int cat: 0
    property var tileIdx: [0, 0, 0, 0, 0, 0, 0, 0]
    property string zone: "tiles"            // "cats" | "tiles" | "list"
    property int listIdx: 0
    property real slide: 0
    property var status: ({ battery: -1, hasBattery: false, charging: false, online: false })
    readonly property real s: Math.max(0.5, Math.min(width / 1280, height / 720))
    readonly property var tiles: cats.length > cat ? cats[cat].tiles : []
    readonly property int ti: Math.min(tileIdx[cat] || 0, Math.max(0, tiles.length - 1))
    readonly property var tile: tiles.length ? tiles[ti] : null
    readonly property var items: tile && tile.kind === "list" ? tile.items : []
    readonly property int li: Math.min(listIdx, Math.max(0, items.length - 1))
    readonly property bool fx: Theme.fx
    readonly property bool anim: Theme.anim
    readonly property color glow: "#46f07a"
    signal menuAction(string id)

    function refreshStatus() { status = backend.handheldStatus() }
    Timer { interval: 20000; running: hh.visible; repeat: true; triggeredOnStart: true; onTriggered: hh.refreshStatus() }

    function snd(n) { backend.playSound(n) }
    function setTile(v) { var a = tileIdx.slice(); a[cat] = v; tileIdx = a }
    function moveCat(d) {
        var n = Math.max(0, Math.min(cats.length - 1, cat + d))
        if (n === cat) return
        cat = n; snd("hhcat")
        if (zone === "list") zone = "tiles"
    }
    function moveTile(d) {
        if (!tiles.length) return
        var n = Math.max(0, Math.min(tiles.length - 1, ti + d))
        if (n !== ti) { setTile(n); snd("hhmove") }
    }
    function moveList(d) {
        if (!items.length) return
        var n = Math.max(0, Math.min(items.length - 1, li + d))
        if (n === li) return
        listIdx = n; snd("hhmove")
        if (anim) { slideAnim.stop(); slide = d; slideAnim.start() }
    }
    function activateTile() {
        if (!tile) return
        snd("hhselect")
        if (tile.kind === "list") { listIdx = 0; zone = "list" }
        else if (tile.kind === "launch") backend.launchGid(tile.gid)
        else if (tile.action === "effects") backend.cycleEffects()
        else hh.menuAction(tile.action)
    }
    function handleKey(e) {
        var k = e.key
        if (k === Qt.Key_Q) { moveCat(-1); return }
        if (k === Qt.Key_E) { moveCat(1); return }
        if (k === Qt.Key_F10 || k === Qt.Key_Menu || k === Qt.Key_O || k === Qt.Key_Tab) {     // Y / Start: Settings
            cat = cats.length - 1; zone = "tiles"; snd("hhcat"); return
        }
        if (zone === "list") {
            if (k === Qt.Key_Up) moveList(-1)
            else if (k === Qt.Key_Down) moveList(1)
            else if (k === Qt.Key_PageUp || k === Qt.Key_Left) moveList(-6)
            else if (k === Qt.Key_PageDown || k === Qt.Key_Right) moveList(6)
            else if (k === Qt.Key_Return || k === Qt.Key_Enter) { if (items.length) { snd("hhselect"); backend.launchGid(items[li].gid) } }
            else if (k === Qt.Key_F) { if (items.length) backend.toggleFavoriteGid(items[li].gid) }
            else if (k === Qt.Key_Escape || k === Qt.Key_B || k === Qt.Key_Backspace) { zone = "tiles"; snd("hhback") }
            return
        }
        if (zone === "cats") {
            if (k === Qt.Key_Left) moveCat(-1)
            else if (k === Qt.Key_Right) moveCat(1)
            else if (k === Qt.Key_Down || k === Qt.Key_Return || k === Qt.Key_Enter) { zone = "tiles"; snd("hhmove") }
            return
        }
        if (k === Qt.Key_Left) moveTile(-1)
        else if (k === Qt.Key_Right) moveTile(1)
        else if (k === Qt.Key_Up || k === Qt.Key_Escape || k === Qt.Key_B || k === Qt.Key_Backspace) { zone = "cats"; snd(k === Qt.Key_Up ? "hhmove" : "hhback") }
        else if (k === Qt.Key_Return || k === Qt.Key_Enter) activateTile()
        else if (k === Qt.Key_F && tile && tile.kind === "launch") backend.toggleFavoriteGid(tile.gid)
    }
    NumberAnimation { id: slideAnim; target: hh; property: "slide"; to: 0; duration: 170; easing.type: Easing.OutCubic }

    // ------------------------------------------------------------ background: black, a flowing green wave, light motes
    Rectangle { anchors.fill: parent; color: "#000000" }
    Rectangle {   // a soft green light low in the middle
        width: parent.width * 1.2; height: parent.height * 0.9
        x: -parent.width * 0.1; y: parent.height * 0.22
        radius: height / 2
        gradient: Gradient { GradientStop { position: 0.0; color: "#00000000" } GradientStop { position: 0.5; color: "#14286a3a" }
                             GradientStop { position: 1.0; color: "#00000000" } }
    }
    Repeater {
        model: [ { src: "hh_wave1.png", period: 50000, top: 0.40, dir: 1, op: 1.0, h: 0.62 },
                 { src: "hh_wave2.png", period: 80000, top: 0.47, dir: -1, op: 0.45, h: 0.40 } ]
        Item {
            id: rib
            required property var modelData
            anchors.fill: parent
            property real shift: 0
            NumberAnimation on shift { from: 0; to: 1; loops: Animation.Infinite; duration: rib.modelData.period
                                       running: hh.visible && hh.anim }
            opacity: rib.modelData.op
            Row {
                y: hh.height * rib.modelData.top - height / 2
                x: rib.modelData.dir > 0 ? -rib.shift * width / 2 : -(1 - rib.shift) * width / 2
                Repeater { model: 2
                    Image { source: Qt.resolvedUrl("../assets/" + rib.modelData.src); width: hh.width * 1.5; height: hh.height * rib.modelData.h
                            fillMode: Image.Stretch; smooth: true; asynchronous: true; sourceSize.width: 1024 } }
            }
        }
    }
    Repeater {
        model: hh.fx ? 20 : 6
        Rectangle {
            id: mote
            required property int index
            readonly property real seed: ((index * 7919) % 1000) / 1000
            width: (2 + seed * 4) * hh.s; height: width; radius: width / 2
            color: index % 4 === 0 ? "#f0d27a" : "#7dffa8"
            x: hh.width * ((index * 0.137 + seed * 0.5) % 1)
            property real p: seed
            y: hh.height * (1.0 - (p % 1) * 1.05)
            opacity: 0.5 * Math.sin(Math.PI * (p % 1))
            NumberAnimation on p { from: mote.seed; to: mote.seed + 1; duration: 16000 + mote.seed * 12000
                                   loops: Animation.Infinite; running: hh.visible && hh.anim }
        }
    }

    // ------------------------------------------------------------ status bar
    Row {
        x: 34 * hh.s; y: 16 * hh.s
        spacing: 14 * hh.s
        Text { text: "CODEAQUE"; color: "#7dffa8"; font.pixelSize: 24 * hh.s; font.weight: Font.Bold; font.letterSpacing: 2 * hh.s }
        Text { text: "Handheld Edition"; color: "#c9cfc6"; font.pixelSize: 20 * hh.s; anchors.baseline: parent.children[0].baseline }
    }
    Row {
        objectName: "hhStatus"
        anchors.right: parent.right; anchors.rightMargin: 30 * hh.s; y: 14 * hh.s
        spacing: 18 * hh.s
        Icon { name: "wifi"; size: 26 * hh.s; color: hh.status.online ? "white" : "#6d756c"; anchors.verticalCenter: parent.verticalCenter }
        Row {
            visible: hh.status.hasBattery
            spacing: 8 * hh.s
            anchors.verticalCenter: parent.verticalCenter
            Text { text: hh.status.battery + "%"; color: "white"; font.pixelSize: 21 * hh.s; anchors.verticalCenter: parent.verticalCenter }
            Rectangle {   // battery picture
                width: 40 * hh.s; height: 20 * hh.s; radius: 4 * hh.s; color: "transparent"
                border.width: 2 * hh.s; border.color: "white"; anchors.verticalCenter: parent.verticalCenter
                Rectangle { x: 3 * hh.s; y: 3 * hh.s; height: parent.height - 6 * hh.s; radius: 2 * hh.s
                            width: Math.max(0, (parent.width - 6 * hh.s) * hh.status.battery / 100)
                            color: hh.status.battery <= 15 ? "#ff6a5a" : hh.glow }
                Rectangle { x: parent.width; y: parent.height * 0.3; width: 3 * hh.s; height: parent.height * 0.4; color: "white" }
            }
        }
        Text {
            id: clock
            color: "white"; font.pixelSize: 21 * hh.s
            anchors.verticalCenter: parent.verticalCenter
            text: Qt.formatDateTime(new Date(), "ddd d MMM  HH:mm")
            Timer { interval: 15000; running: hh.visible; repeat: true; onTriggered: clock.text = Qt.formatDateTime(new Date(), "ddd d MMM  HH:mm") }
        }
    }

    // ------------------------------------------------------------ category row
    Item {
        id: catRow
        objectName: "hhCats"
        x: 60 * hh.s; width: parent.width - 120 * hh.s
        y: 92 * hh.s; height: 130 * hh.s
        readonly property real step: width / Math.max(1, hh.cats.length)
        Text { x: -40 * hh.s; anchors.verticalCenter: parent.verticalCenter; text: "\u2039"; color: hh.cat > 0 ? "white" : "#444"
               font.pixelSize: 54 * hh.s }
        Text { x: parent.width + 14 * hh.s; anchors.verticalCenter: parent.verticalCenter; text: "\u203a"
               color: hh.cat < hh.cats.length - 1 ? "white" : "#444"; font.pixelSize: 54 * hh.s }
        Repeater {
            model: hh.cats
            Item {
                id: ci
                required property var modelData
                required property int index
                readonly property bool on: index === hh.cat
                width: catRow.step; height: catRow.height
                x: index * catRow.step
                scale: on ? 1.18 : 0.92
                Behavior on scale { enabled: hh.anim; NumberAnimation { duration: 220; easing.type: Easing.OutBack } }
                Item {
                    id: ciIcon
                    anchors.horizontalCenter: parent.horizontalCenter
                    y: 14 * hh.s; width: 64 * hh.s; height: width
                    Icon { id: ciGlyph; anchors.fill: parent; name: ci.modelData.icon; size: parent.width; stroke: 1.8
                           color: ci.on ? "#9dffbe" : "#e9ece6" }
                    MultiEffect {   // the chosen category glows
                        visible: ci.on && hh.fx
                        source: ciGlyph; anchors.fill: ciGlyph
                        shadowEnabled: true; shadowColor: hh.glow; shadowBlur: 1.0; shadowScale: 1.1
                        shadowHorizontalOffset: 0; shadowVerticalOffset: 0
                    }
                }
                Text {
                    anchors.horizontalCenter: parent.horizontalCenter
                    anchors.top: ciIcon.bottom; anchors.topMargin: 8 * hh.s
                    text: ci.modelData.title
                    color: ci.on ? "white" : "#e2e6df"
                    font.pixelSize: (ci.on ? 24 : 21) * hh.s; font.weight: ci.on ? Font.DemiBold : Font.Normal
                }
                Rectangle {   // focus underline when the row itself has the focus
                    visible: ci.on && hh.zone === "cats"
                    anchors.horizontalCenter: parent.horizontalCenter; anchors.bottom: parent.bottom
                    width: 54 * hh.s; height: 3 * hh.s; radius: height / 2; color: hh.glow
                }
                MouseArea { anchors.fill: parent; onClicked: { hh.cat = ci.index; hh.zone = "tiles" } }
            }
        }
    }

    // ------------------------------------------------------------ the big wordmark (hidden while a list is open)
    Item {
        id: mark
        anchors.horizontalCenter: parent.horizontalCenter
        y: parent.height * 0.42
        width: markText.implicitWidth; height: markText.implicitHeight
        opacity: hh.zone === "list" ? 0 : 1
        Behavior on opacity { enabled: hh.anim; NumberAnimation { duration: 200 } }
        Text {   // a soft green halo (cheap: plain text, no shader) under the wordmark
            anchors.centerIn: markText
            text: "CODEAQUE"; color: "transparent"
            font: markText.font
            style: Text.Outline; styleColor: "#9946f07a"
            opacity: 0.9
        }
        Text {
            id: markText
            text: "CODEAQUE"
            color: "#f4fff6"
            font.pixelSize: 78 * hh.s; font.weight: Font.Light; font.letterSpacing: 14 * hh.s
            style: hh.fx ? Text.Normal : Text.Outline; styleColor: "#3346f07a"
        }
        MultiEffect {
            visible: hh.fx
            source: markText; anchors.fill: markText
            shadowEnabled: true; shadowColor: "#ff46f07a"; shadowBlur: 1.0; shadowScale: 1.06; blurMax: 48
            shadowHorizontalOffset: 0; shadowVerticalOffset: 0
        }
    }

    // ------------------------------------------------------------ the tile row
    Item {
        id: tileRow
        objectName: "hhTiles"
        x: 30 * hh.s; width: parent.width - 60 * hh.s
        y: parent.height - 205 * hh.s; height: 120 * hh.s
        clip: false
        opacity: hh.zone === "list" ? 0.35 : 1
        Behavior on opacity { enabled: hh.anim; NumberAnimation { duration: 200 } }
        readonly property real tw: 112 * hh.s
        readonly property real gap: 10 * hh.s
        readonly property real full: hh.tiles.length * (tw + gap) - gap
        readonly property real scrollX: full <= width ? 0
                                        : Math.max(0, Math.min(full - width, hh.ti * (tw + gap) - width / 2 + tw / 2))
        Row {
            x: -tileRow.scrollX
            Behavior on x { enabled: hh.anim; NumberAnimation { duration: 220; easing.type: Easing.OutCubic } }
            spacing: tileRow.gap
            Repeater {
                model: hh.tiles
                Item {
                    id: tl
                    required property var modelData
                    required property int index
                    readonly property bool on: index === hh.ti
                    readonly property bool hot: on && hh.zone !== "cats"
                    width: (index === 0 && hh.cat === 0 ? 1.3 : 1) * tileRow.tw; height: tileRow.height
                    scale: hot ? 1.06 : 1.0
                    z: on ? 2 : 0
                    Behavior on scale { enabled: hh.anim; NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
                    Rectangle {   // soft glow round the chosen tile
                        visible: tl.hot
                        anchors.fill: box; anchors.margins: -6 * hh.s
                        radius: 20 * hh.s; color: "transparent"; border.width: 6 * hh.s; border.color: "#5546f07a"
                    }
                    Rectangle {
                        id: box
                        anchors.fill: parent
                        radius: 14 * hh.s
                        color: tl.hot ? "#0f2416" : "#0c0f0c"
                        border.width: (tl.hot ? 3 : 1.5) * hh.s
                        border.color: tl.hot ? hh.glow : "#3a5a44"
                        Icon {
                            anchors.horizontalCenter: parent.horizontalCenter
                            y: 18 * hh.s
                            name: tl.modelData.icon.indexOf("console:") === 0 ? Theme.consoleFor(tl.modelData.icon.substring(8), "pad")
                                                                            : tl.modelData.icon
                            size: 44 * hh.s
                            color: tl.hot ? "#b5ffcc" : "#eef1ea"
                        }
                        Text {
                            anchors.horizontalCenter: parent.horizontalCenter
                            anchors.bottom: parent.bottom; anchors.bottomMargin: 12 * hh.s
                            width: parent.width - 12 * hh.s; horizontalAlignment: Text.AlignHCenter
                            elide: Text.ElideRight; maximumLineCount: 1
                            text: tl.modelData.title
                            color: "white"; font.pixelSize: 17 * hh.s; font.weight: Font.DemiBold
                        }
                        Text {   // how many games inside
                            visible: tl.modelData.kind === "list"
                            anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 7 * hh.s
                            text: tl.modelData.items.length; color: "#9fb7a6"; font.pixelSize: 14 * hh.s
                        }
                    }
                    MouseArea { anchors.fill: parent; onClicked: { hh.setTile(tl.index); hh.zone = "tiles"; hh.activateTile() } }
                }
            }
        }
        Text {
            objectName: "hhEmptyRow"
            visible: hh.tiles.length === 0
            anchors.verticalCenter: parent.verticalCenter; x: 10 * hh.s
            width: parent.width - 20 * hh.s; wrapMode: Text.WordWrap
            color: "#dfe6dc"; font.pixelSize: 24 * hh.s
            text: hh.cat === 0 || hh.cat === 2 ? "No retro games yet. Copy your own game files to the Games folder, or go to Settings > Add games. CodeAque does not come with games or BIOS files."
                                               : "Nothing here yet."
        }
    }

    // ------------------------------------------------------------ the sliding list for a tile with games
    Item {
        id: listLayer
        objectName: "hhList"
        visible: opacity > 0
        opacity: hh.zone === "list" ? 1 : 0
        Behavior on opacity { enabled: hh.anim; NumberAnimation { duration: 200 } }
        x: 90 * hh.s; width: parent.width - 180 * hh.s
        y: catRow.y + catRow.height + 14 * hh.s
        height: tileRow.y - y - 14 * hh.s
        clip: true
        readonly property real rowH: 58 * hh.s
        readonly property real selH: 92 * hh.s
        readonly property real selY: rowH * 1.4
        Text {
            anchors.right: parent.right; y: 4 * hh.s
            text: hh.tile ? hh.tile.title + "  " + (hh.items.length ? (hh.li + 1) + " / " + hh.items.length : "") : ""
            color: "#9fb7a6"; font.pixelSize: 18 * hh.s
        }
        Repeater {
            model: 8
            Item {
                id: slot
                required property int index
                readonly property int off: index - 1
                readonly property int i: hh.li + off
                readonly property var it: i >= 0 && i < hh.items.length ? hh.items[i] : null
                readonly property bool sel: off === 0
                visible: it !== null
                width: listLayer.width
                height: sel ? listLayer.selH : listLayer.rowH
                y: (off < 0 ? listLayer.selY - listLayer.rowH : off === 0 ? listLayer.selY
                              : listLayer.selY + listLayer.selH + (off - 1) * listLayer.rowH) + hh.slide * listLayer.rowH
                opacity: off < 0 ? 0.45 : (off > 3 ? 0.55 : 1)
                Rectangle {
                    id: art
                    width: (slot.sel ? 84 : 46) * hh.s; height: width
                    anchors.verticalCenter: parent.verticalCenter
                    radius: 12 * hh.s
                    color: slot.it ? Qt.hsla(0.38, 0.4, 0.10 + (slot.it.hue * 0.08), 1) : "transparent"
                    border.width: slot.sel ? 2.5 * hh.s : 1 * hh.s
                    border.color: slot.sel ? hh.glow : "#3a5a44"
                    clip: true
                    Behavior on width { enabled: hh.anim; NumberAnimation { duration: 170; easing.type: Easing.OutCubic } }
                    Image { anchors.fill: parent; anchors.margins: parent.border.width; visible: source !== ""
                            source: slot.it && slot.it.cover ? slot.it.cover : ""; fillMode: Image.PreserveAspectCrop
                            asynchronous: true; sourceSize.width: 256 }
                    Icon { anchors.centerIn: parent; visible: !(slot.it && slot.it.cover)
                           name: slot.it ? (slot.it.gid.indexOf("web:") === 0 ? "c_cloud" : Theme.consoleFor(slot.it.platform, "pad")) : "pad"
                           size: art.width * 0.56; color: slot.sel ? "#b5ffcc" : "#e6ebe3" }
                }
                Column {
                    anchors.left: art.right; anchors.leftMargin: 22 * hh.s
                    anchors.right: parent.right; anchors.verticalCenter: parent.verticalCenter
                    spacing: 4 * hh.s
                    Text { width: parent.width; elide: Text.ElideRight; text: slot.it ? (slot.it.favorite ? "\u2605 " : "") + slot.it.title : ""
                           color: "white"; font.pixelSize: (slot.sel ? 34 : 25) * hh.s; font.weight: slot.sel ? Font.DemiBold : Font.Normal }
                    Text { width: parent.width; elide: Text.ElideRight; visible: slot.sel
                           text: slot.it ? (slot.it.gid.indexOf("web:") === 0 ? "Needs internet and your own account with this service."
                                                                             : slot.it.sub + (slot.it.needsInstall ? "  -  needs its emulator (press A)" : ""))
                                         : ""
                           color: "#9dffbe"; font.pixelSize: 20 * hh.s }
                }
            }
        }
        Text {
            visible: hh.items.length === 0
            y: listLayer.selY; width: parent.width; wrapMode: Text.WordWrap
            text: "Nothing in here yet."; color: "#dfe6dc"; font.pixelSize: 26 * hh.s
        }
    }

    // ------------------------------------------------------------ hints and Online
    Rectangle { x: 0; width: parent.width; y: parent.height - 62 * hh.s; height: 1; color: "#2646f07a" }
    Row {
        x: 30 * hh.s; anchors.bottom: parent.bottom; anchors.bottomMargin: 16 * hh.s
        spacing: 30 * hh.s
        Repeater {
            model: [ { k: "A", t: "Enter" }, { k: "B", t: "Back" }, { k: "X", t: "Favourite" }, { k: "Y", t: "Settings" } ]
            Row {
                required property var modelData
                spacing: 10 * hh.s
                HandButton { letter: modelData.k; s: hh.s; anchors.verticalCenter: parent.verticalCenter }
                Text { text: modelData.t; color: "#e8ece5"; font.pixelSize: 21 * hh.s; anchors.verticalCenter: parent.verticalCenter }
            }
        }
    }
    Row {
        objectName: "hhOnline"
        anchors.right: parent.right; anchors.rightMargin: 30 * hh.s
        anchors.bottom: parent.bottom; anchors.bottomMargin: 16 * hh.s
        spacing: 10 * hh.s
        Icon { name: "c_cloud"; size: 28 * hh.s; color: hh.status.online ? hh.glow : "#6d756c"; anchors.verticalCenter: parent.verticalCenter }
        Text { text: hh.status.online ? "Online" : "Offline"; color: "#e8ece5"; font.pixelSize: 21 * hh.s; anchors.verticalCenter: parent.verticalCenter }
    }
}
