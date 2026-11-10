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
    property var tileIdx: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
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
    readonly property color glow: "#46cef0"
    readonly property bool covers: cats.length > cat && (cats[cat].key === "mygames" || cats[cat].key === "steam" || cats[cat].key === "pcgames" || cats[cat].key === "trilogies" || cats[cat].key === "coop" || cats[cat].key === "roblox")     // 0.9.5 / 0.10.32: portrait box-art tiles
    readonly property bool hubOn: cats.length > cat && cats[cat].key === "emulators"
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
    function goToKey(key) {
        for (var i = 0; i < cats.length; i++)
            if (cats[i].key === key) { cat = i; zone = "tiles"; snd("hhcat"); return true }
        return false
    }
    function activateTile() {
        if (!tile) return
        snd("hhselect")
        if (tile.kind === "list") { listIdx = 0; zone = "list" }
        else if (tile.kind === "launch") backend.launchGid(tile.gid)
        else if (tile.action === "effects") backend.cycleEffects()
        else if (tile.action === "rotate") backend.cycleScreenRotate()
        else if (tile.action && tile.action.indexOf("goto:") === 0) goToKey(tile.action.substring(5))
        else if (tile.action === "boxset") backend.enterBoxSet()
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
        if (hubOn && k === Qt.Key_Down) { if (ti + hubGrid.cols < tiles.length) moveTile(hubGrid.cols); return }
        if (hubOn && k === Qt.Key_Up && ti >= hubGrid.cols) { moveTile(-hubGrid.cols); return }
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
        gradient: Gradient { GradientStop { position: 0.0; color: "#00000000" } GradientStop { position: 0.5; color: "#14285d6a" }
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
            color: index % 4 === 0 ? "#7ad3f0" : "#7de5ff"
            x: hh.width * ((index * 0.137 + seed * 0.5) % 1)
            property real p: seed
            y: hh.height * (1.0 - (p % 1) * 1.05)
            opacity: 0.5 * Math.sin(Math.PI * (p % 1))
            NumberAnimation on p { from: mote.seed; to: mote.seed + 1; duration: 16000 + mote.seed * 12000
                                   loops: Animation.Infinite; running: hh.visible && hh.anim }
        }
    }

    Snow { anchors.fill: parent; active: Theme.snow && hh.visible }     // the Christmas theme

    // ------------------------------------------------------------ status bar
    Item {   // CodeAque's logo, small, top left
        objectName: "hhBrand"
        x: 24 * hh.s; y: 12 * hh.s
        width: 150 * hh.s; height: 50 * hh.s
        Image { anchors.fill: parent; source: Qt.resolvedUrl("../assets/codeaque-logo.png"); fillMode: Image.PreserveAspectFit
                sourceSize: Qt.size(Math.ceil(width * 2), Math.ceil(height * 2)); smooth: true; mipmap: true }
    }
    Row {
        objectName: "hhStatus"
        anchors.right: parent.right; anchors.rightMargin: 30 * hh.s; y: 14 * hh.s
        spacing: 18 * hh.s
        Rectangle {   // small "update ready" notice — tap opens the same Update screen as Settings
            objectName: "hhUpdate"
            visible: backend.updateAvailable
            anchors.verticalCenter: parent.verticalCenter
            height: 28 * hh.s; width: updText.width + 24 * hh.s; radius: height / 2
            color: "transparent"; border.width: 1.5 * hh.s; border.color: hh.glow
            Text { id: updText; anchors.centerIn: parent; text: "Update " + backend.updateVersion + " ready"; color: "white"; font.pixelSize: 15 * hh.s }
            MouseArea {
                objectName: "hhUpdateClick"
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                onClicked: hh.menuAction("update")
            }
        }
        Icon { name: "wifi"; size: 26 * hh.s; color: hh.status.online ? "white" : "#6c7175"; anchors.verticalCenter: parent.verticalCenter }
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
        Rectangle { width: 1.5 * hh.s; height: 26 * hh.s; color: "#55ffffff"; anchors.verticalCenter: parent.verticalCenter }
        Text {
            id: clock
            color: "white"; font.pixelSize: 21 * hh.s
            anchors.verticalCenter: parent.verticalCenter
            text: Qt.formatDateTime(new Date(), "ddd d MMM  HH:mm")
            Timer { interval: 15000; running: hh.visible; repeat: true; onTriggered: clock.text = Qt.formatDateTime(new Date(), "ddd d MMM  HH:mm") }
        }
    }

    // ------------------------------------------------------------ category row (0.10.31: scrolls on small screens; never overlaps)
    Item {
        id: catRow
        objectName: "hhCats"
        x: 60 * hh.s; width: parent.width - 120 * hh.s
        y: 84 * hh.s; height: 124 * hh.s
        // Icon-only width for unselected tabs; selected tab grows to fit its label. Keeps 1280x720 and 800x480 readable.
        readonly property real iconW: 72 * hh.s
        readonly property real gap: 6 * hh.s
        readonly property bool cramped: {
            // Estimate: if every tab showed a short label, would they overflow? Prefer icon-only when yes.
            var n = Math.max(1, hh.cats.length)
            return (n * (iconW + 48 * hh.s)) > width
        }
        function ensureVisible() {
            if (!catFlick || !catInner) return
            var item = catInner.children[hh.cat]
            if (!item) return
            var left = item.x
            var right = item.x + item.width
            var viewL = catFlick.contentX
            var viewR = viewL + catFlick.width
            if (left < viewL) catFlick.contentX = Math.max(0, left - 12 * hh.s)
            else if (right > viewR) catFlick.contentX = Math.min(catFlick.contentWidth - catFlick.width, right - catFlick.width + 12 * hh.s)
        }
        Connections {
            target: hh
            function onCatChanged() { Qt.callLater(catRow.ensureVisible) }
        }
        Text { x: -40 * hh.s; anchors.verticalCenter: parent.verticalCenter; text: "\u2039"; color: hh.cat > 0 ? "white" : "#444"
               font.pixelSize: 54 * hh.s }
        Text { x: parent.width + 14 * hh.s; anchors.verticalCenter: parent.verticalCenter; text: "\u203a"
               color: hh.cat < hh.cats.length - 1 ? "white" : "#444"; font.pixelSize: 54 * hh.s }
        Flickable {
            id: catFlick
            objectName: "hhCatsFlick"
            anchors.fill: parent
            clip: true
            interactive: contentWidth > width
            flickableDirection: Flickable.HorizontalFlick
            boundsBehavior: Flickable.StopAtBounds
            contentWidth: catInner.width
            contentHeight: height
            Behavior on contentX { enabled: hh.anim; NumberAnimation { duration: 180; easing.type: Easing.OutCubic } }
            Row {
                id: catInner
                objectName: "hhCatsInner"
                height: parent.height
                spacing: catRow.gap
                Repeater {
                    model: hh.cats
                    Item {
                        id: ci
                        required property var modelData
                        required property int index
                        readonly property bool on: index === hh.cat
                        // Selected: always show the label. Unselected: label only when there is room for every tab.
                        readonly property bool showLabel: on || !catRow.cramped
                        width: Math.max(catRow.iconW, (showLabel ? Math.max(catRow.iconW, lab.implicitWidth + 16 * hh.s) : catRow.iconW))
                        height: catRow.height
                        scale: on ? 1.08 : 0.94
                        Behavior on scale { enabled: hh.anim; NumberAnimation { duration: 200; easing.type: Easing.OutBack } }
                        Behavior on width { enabled: hh.anim; NumberAnimation { duration: 160; easing.type: Easing.OutCubic } }
                        Item {
                            id: ciIcon
                            anchors.horizontalCenter: parent.horizontalCenter
                            y: 10 * hh.s; width: 56 * hh.s; height: width
                            Icon { id: ciGlyph; anchors.fill: parent; name: ci.modelData.icon; size: parent.width; stroke: 1.8
                                   color: ci.on ? "#9debff" : "#e6ebec" }
                            MultiEffect {   // the chosen category glows
                                visible: ci.on && hh.fx
                                source: ciGlyph; anchors.fill: ciGlyph
                                shadowEnabled: true; shadowColor: hh.glow; shadowBlur: 1.0; shadowScale: 1.1
                                shadowHorizontalOffset: 0; shadowVerticalOffset: 0
                            }
                        }
                        Text {
                            id: lab
                            anchors.horizontalCenter: parent.horizontalCenter
                            anchors.top: ciIcon.bottom; anchors.topMargin: 6 * hh.s
                            visible: ci.showLabel
                            text: ci.modelData.title
                            color: ci.on ? "white" : "#dfe5e6"
                            font.pixelSize: (ci.on ? 20 : 16) * hh.s; font.weight: ci.on ? Font.DemiBold : Font.Normal
                            elide: Text.ElideRight; maximumLineCount: 1
                        }
                        Rectangle {   // focus underline when the row itself has the focus
                            visible: ci.on && hh.zone === "cats"
                            anchors.horizontalCenter: parent.horizontalCenter; anchors.bottom: parent.bottom
                            width: Math.min(parent.width - 8 * hh.s, 54 * hh.s); height: 3 * hh.s; radius: height / 2; color: hh.glow
                        }
                        MouseArea { anchors.fill: parent; onClicked: { hh.cat = ci.index; hh.zone = "tiles" } }
                    }
                }
            }
        }
        Component.onCompleted: Qt.callLater(catRow.ensureVisible)
    }

    // ------------------------------------------------------------ the centre piece: the CodeAque logo and tagline
    Item {
        id: mark
        objectName: "hhMark"
        visible: !hh.hubOn
        anchors.horizontalCenter: parent.horizontalCenter
        y: 196 * hh.s
        width: 600 * hh.s; height: 260 * hh.s
        opacity: hh.zone === "list" ? 0 : 1
        Behavior on opacity { enabled: hh.anim; NumberAnimation { duration: 220 } }
        Image {
            id: markText
            anchors.horizontalCenter: parent.horizontalCenter
            y: 10 * hh.s
            width: 660 * hh.s; height: 220 * hh.s
            source: Qt.resolvedUrl("../assets/codeaque-logo.png")
            fillMode: Image.PreserveAspectFit
            sourceSize: Qt.size(Math.ceil(width * 1.5), Math.ceil(height * 1.5))
            smooth: true; mipmap: true
        }
        MultiEffect {   // soft neon glow behind the white logo
            visible: hh.fx
            source: markText; anchors.fill: markText
            shadowEnabled: true; shadowColor: hh.glow; shadowBlur: 1.0; shadowOpacity: 0.8
            shadowHorizontalOffset: 0; shadowVerticalOffset: 0
        }
        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: markText.bottom; anchors.topMargin: -2 * hh.s
            text: "GAMING  \u2022  CLOUD  \u2022  EMULATION  \u2022  STREAMING"
            visible: !hh.covers
            color: "#e6ecee"; font.pixelSize: 16 * hh.s; font.letterSpacing: 4 * hh.s; font.weight: Font.Medium
        }
    }

    // ------------------------------------------------------------ Emulators: the Retro Emulators hub (every supported system)
    Item {
        id: hub
        objectName: "hhHub"
        visible: hh.hubOn
        anchors.fill: parent
        opacity: hh.zone === "list" ? 0.12 : 1
        Behavior on opacity { enabled: hh.anim; NumberAnimation { duration: 200 } }
        Canvas {   // a soft neon floor glow (drawn once)
            anchors.fill: parent
            renderStrategy: Canvas.Cooperative
            onWidthChanged: requestPaint()
            onHeightChanged: requestPaint()
            onPaint: {
                var c = getContext("2d"); c.reset()
                var H = height, W = width
                var sky = c.createRadialGradient(W / 2, H * 1.2, 10, W / 2, H * 1.2, W * 0.75)
                sky.addColorStop(0, "rgba(40,130,160,0.20)"); sky.addColorStop(1, "rgba(0,0,0,0)")
                c.fillStyle = sky; c.fillRect(0, 0, W, H)
            }
        }
        Image {   // the logo over the hub
            id: hubTitle
            anchors.horizontalCenter: parent.horizontalCenter
            y: 206 * hh.s
            width: 186 * hh.s; height: 62 * hh.s
            source: Qt.resolvedUrl("../assets/codeaque-logo.png"); fillMode: Image.PreserveAspectFit
            sourceSize: Qt.size(Math.ceil(width * 2), Math.ceil(height * 2)); smooth: true; mipmap: true
        }
        Text { id: hubSub; anchors.horizontalCenter: parent.horizontalCenter; anchors.top: hubTitle.bottom; anchors.topMargin: 0
               text: "RETRO EMULATORS HUB"; color: "#c4e6f1"; font.pixelSize: 15 * hh.s; font.letterSpacing: 6 * hh.s; font.weight: Font.DemiBold }
        Text { id: hubTag; anchors.horizontalCenter: parent.horizontalCenter; anchors.top: hubSub.bottom; anchors.topMargin: 2 * hh.s
               text: "PLAY  \u2022  EMULATE  \u2022  PRESERVE  \u2022  STREAM"; color: "#9fcdd8"; font.pixelSize: 11.5 * hh.s; font.letterSpacing: 3 * hh.s }
        Grid {
            id: hubGrid
            readonly property int cols: 11
            readonly property real tw: (hh.width - 60 * hh.s - (cols - 1) * spacing) / cols
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: hubTag.bottom; anchors.topMargin: 12 * hh.s
            columns: cols
            spacing: 8 * hh.s
            Repeater {
                model: hh.hubOn ? hh.tiles : []
                Item {
                    id: ht
                    required property var modelData
                    required property int index
                    readonly property bool on: index === hh.ti && hh.zone !== "cats"
                    readonly property bool has: modelData.items.length > 0
                    width: hubGrid.tw; height: 70 * hh.s
                    z: on ? 2 : 0
                    scale: on ? 1.1 : 1
                    Behavior on scale { enabled: hh.anim; NumberAnimation { duration: 190; easing.type: Easing.OutCubic } }
                    Rectangle {   // brighter halo on the chosen one
                        visible: ht.on
                        anchors.fill: parent; anchors.margins: -5 * hh.s
                        radius: 16 * hh.s; color: "transparent"; border.width: 5 * hh.s; border.color: "#6646cef0"
                    }
                    Rectangle {
                        anchors.fill: parent
                        radius: 12 * hh.s
                        border.width: (ht.on ? 3 : 2.2) * hh.s
                        border.color: ht.on ? "#8ae2ff" : "#2e96b8"
                        gradient: Gradient {   // inner green glow
                            GradientStop { position: 0.0; color: ht.on ? "#1d4e5a" : "#0f252a" }
                            GradientStop { position: 0.6; color: ht.on ? "#0e262c" : "#081113" }
                            GradientStop { position: 1.0; color: "#050708" }
                        }
                        Icon { anchors.horizontalCenter: parent.horizontalCenter; y: 7 * hh.s
                               name: ht.modelData.icon; size: 28 * hh.s; stroke: 1.7; metal: ""; color: Theme.gold
                               opacity: ht.has || ht.on ? 1 : 0.7 }
                        Column {
                            anchors.horizontalCenter: parent.horizontalCenter
                            anchors.bottom: parent.bottom; anchors.bottomMargin: 6 * hh.s
                            width: parent.width - 8 * hh.s
                            Text { width: parent.width; horizontalAlignment: Text.AlignHCenter; text: "CODEAQUE"
                                   color: "#5aadc9"; font.pixelSize: 7.5 * hh.s; font.letterSpacing: 1.2 * hh.s; font.weight: Font.DemiBold }
                            Text { width: parent.width; horizontalAlignment: Text.AlignHCenter; elide: Text.ElideRight
                                   text: ht.modelData.title.toUpperCase(); color: "white"
                                   font.pixelSize: 12 * hh.s; font.weight: Font.Bold }
                        }
                        Rectangle {   // how many games
                            visible: ht.has
                            anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 4 * hh.s
                            width: cnt.implicitWidth + 8 * hh.s; height: 15 * hh.s; radius: height / 2; color: "#cc46cef0"
                            Text { id: cnt; anchors.centerIn: parent; text: ht.modelData.items.length; color: "#051b21"
                                   font.pixelSize: 10 * hh.s; font.weight: Font.Bold }
                        }
                    }
                    MouseArea { anchors.fill: parent; onClicked: { hh.setTile(ht.index); hh.zone = "tiles"; hh.activateTile() } }
                }
            }
        }
        Text {   // what the chosen system is, and what you need
            anchors.horizontalCenter: parent.horizontalCenter
            anchors.top: hubGrid.bottom; anchors.topMargin: 6 * hh.s
            visible: hh.zone !== "list" && hh.tile !== null
            text: hh.tile ? (hh.tile.full + "  -  " + (hh.tile.items.length ? hh.tile.items.length + " game" + (hh.tile.items.length === 1 ? "" : "s")
                                                    : "no games yet: add your own with Settings > Add games")) : ""
            color: "#dfe9ec"; font.pixelSize: 16 * hh.s
        }
    }

    // 0.10.31: small note under the selected Trilogies tile (e.g. "Needs a strong PC, try cloud gaming.")
    Text {
        id: tileNote
        objectName: "hhTileNote"
        readonly property string noteText: (hh.tile && hh.tile.note) ? ("" + hh.tile.note) : ""
        visible: noteText !== "" && hh.zone !== "list"
        anchors.horizontalCenter: parent.horizontalCenter
        y: tileRow.y - 28 * hh.s
        width: parent.width - 80 * hh.s
        horizontalAlignment: Text.AlignHCenter
        wrapMode: Text.WordWrap
        text: noteText
        color: "#c8d6da"; font.pixelSize: 16 * hh.s
    }

    // ------------------------------------------------------------ the tile row
    Item {
        id: tileRow
        objectName: "hhTiles"
        visible: !hh.hubOn
        x: 30 * hh.s; width: parent.width - 60 * hh.s
        y: parent.height - (hh.covers ? 282 : 205) * hh.s; height: (hh.covers ? 200 : 120) * hh.s
        clip: false
        opacity: hh.zone === "list" ? 0.35 : 1
        Behavior on opacity { enabled: hh.anim; NumberAnimation { duration: 200 } }
        readonly property real tw: (hh.covers ? 136 : 112) * hh.s
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
                    readonly property string art: tl.modelData.art || ""
                    readonly property bool fit: tl.modelData.fit === true || !hh.covers   // an app icon / small picture: shown whole above the name
                    readonly property bool pic: art !== "" && artImg.status !== Image.Error
                    width: (hh.covers && index < 4 && hh.cats.length && hh.cats[hh.cat] && hh.cats[hh.cat].key === "steam") ? tileRow.tw * 1.45 : (hh.covers ? tileRow.tw : ((index === 0 && hh.cat === 0) || hh.tiles.length <= 7 ? 1.4 : 1) * tileRow.tw); height: tileRow.height
                    scale: hot ? 1.06 : 1.0
                    z: on ? 2 : 0
                    Behavior on scale { enabled: hh.anim; NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
                    Rectangle {   // soft glow round the chosen tile
                        visible: tl.hot
                        anchors.fill: box; anchors.margins: -6 * hh.s
                        radius: 20 * hh.s; color: "transparent"; border.width: 6 * hh.s; border.color: "#5546cef0"
                    }
                    Rectangle {
                        id: box
                        anchors.fill: parent
                        radius: 14 * hh.s
                        gradient: Gradient {
                            GradientStop { position: 0.0; color: tl.hot ? "#16333a" : "#0d171a" }
                            GradientStop { position: 1.0; color: tl.hot ? "#071114" : "#050809" }
                        }
                        border.width: (tl.hot ? 3 : 1.5) * hh.s
                        border.color: tl.hot ? "#7de5ff" : "#2f6b7a"
                        clip: true
                        Item {        // 0.9.5: CodeAque's own generic art for a system / emulator tile in My Games (no logos)
                            visible: hh.covers && (!tl.pic || tl.fit)
                            anchors.fill: parent; anchors.margins: box.border.width
                            readonly property real hue: tl.modelData.hue !== undefined ? tl.modelData.hue : 0.53
                            Rectangle { anchors.fill: parent; radius: 12 * hh.s
                                gradient: Gradient {
                                    GradientStop { position: 0.0; color: Qt.hsla(tl.modelData.hue !== undefined ? tl.modelData.hue : 0.53, 0.65, 0.34, 1) }
                                    GradientStop { position: 0.6; color: Qt.hsla(tl.modelData.hue !== undefined ? tl.modelData.hue : 0.53, 0.7, 0.14, 1) }
                                    GradientStop { position: 1.0; color: "#05080a" } } }
                            Repeater { model: 5      // soft diagonal light bands
                                Rectangle { x: (index * 0.28 - 0.3) * parent.width; y: -parent.height * 0.2; width: 7 * hh.s; height: parent.height * 1.4
                                            rotation: 28; color: "white"; opacity: 0.05 + 0.03 * (index % 2) } }
                            Rectangle { anchors.horizontalCenter: parent.horizontalCenter; y: parent.height * 0.18; width: parent.width * 0.62; height: width
                                        radius: width / 2; color: "transparent"; border.width: 2 * hh.s; border.color: "#33ffffff" }
                        }
                        Image {       // the game's own cover: Steam's library picture, Lutris / Heroic's saved cover, libretro box art
                            id: artImg
                            visible: tl.pic
                            x: tl.fit ? (parent.width - width) / 2 : box.border.width
                            y: tl.fit ? (hh.covers ? 40 : 12) * hh.s : box.border.width
                            width: tl.fit ? 60 * hh.s : parent.width - 2 * box.border.width
                            height: tl.fit ? 60 * hh.s : parent.height - 2 * box.border.width
                            source: tl.art
                            fillMode: tl.fit ? Image.PreserveAspectFit : Image.PreserveAspectCrop; asynchronous: true; cache: true; smooth: true
                            sourceSize: Qt.size(272, 400)
                        }
                        Rectangle {   // dark band so the name stays readable on a picture
                            visible: tl.pic && !tl.fit
                            anchors.left: parent.left; anchors.right: parent.right; anchors.bottom: parent.bottom
                            anchors.margins: box.border.width; height: parent.height * 0.3
                            gradient: Gradient { GradientStop { position: 0.0; color: "#00000000" } GradientStop { position: 1.0; color: "#e6000000" } }
                        }
                        Rectangle {   // Christmas: a soft snow cap on the tile
                            visible: Theme.snow
                            x: 4 * hh.s; y: -4 * hh.s; width: parent.width - 8 * hh.s; height: 9 * hh.s; radius: height / 2
                            color: "#f4fbff"; opacity: 0.95
                            Repeater { model: 4
                                Rectangle { x: (index + 0.5) * parent.width / 4 - width / 2; y: parent.height - height * 0.6
                                            width: 10 * hh.s; height: 7 * hh.s; radius: width / 2; color: "#f4fbff" } }
                        }
                        Icon {
                            visible: !tl.pic
                            anchors.horizontalCenter: parent.horizontalCenter
                            y: (hh.covers ? 52 : 16) * hh.s
                            stroke: 1.8
                            name: tl.modelData.icon.indexOf("console:") === 0 ? Theme.consoleFor(tl.modelData.icon.substring(8), "pad")
                                                                            : tl.modelData.icon
                            size: 44 * hh.s
                            color: tl.hot ? "#b5f0ff" : "#7cd9f0"
                        }
                        Column {
                            anchors.horizontalCenter: parent.horizontalCenter
                            anchors.bottom: parent.bottom; anchors.bottomMargin: 10 * hh.s
                            width: parent.width - 10 * hh.s
                            spacing: 1 * hh.s
                            Text { visible: !tl.pic || tl.fit; width: parent.width; horizontalAlignment: Text.AlignHCenter; text: "CODEAQUE"
                                   color: tl.hot ? "#c8f4ff" : "#aab2b8"; font.pixelSize: 9.5 * hh.s; font.letterSpacing: 1.4 * hh.s; font.weight: Font.DemiBold }
                            Text { width: parent.width; horizontalAlignment: Text.AlignHCenter
                                   elide: Text.ElideRight; maximumLineCount: 2; wrapMode: Text.WordWrap; lineHeight: 0.9
                                   text: tl.modelData.title.toUpperCase()
                                   color: "white"; font.pixelSize: 14.5 * hh.s; font.weight: Font.Bold; font.letterSpacing: 0.3 * hh.s }
                        }
                        Text {   // how many games inside
                            visible: tl.modelData.kind === "list"
                            anchors.right: parent.right; anchors.top: parent.top; anchors.margins: 7 * hh.s
                            text: tl.modelData.items.length; color: "#9fb2b7"; font.pixelSize: 14 * hh.s
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
            color: "#dce4e6"; font.pixelSize: 24 * hh.s
            text: hh.cats.length > hh.cat && hh.cats[hh.cat].key === "pcgames" ? "No PC games found yet. Games you install with Lutris or Heroic (Epic, GOG, Amazon) show up here."
                : hh.cats.length > hh.cat && hh.cats[hh.cat].key === "games" ? "No retro games yet. Copy your own game files to the Games folder, or go to Settings > Add games. CodeAque does not come with games or BIOS files."
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
            color: "#9fb2b7"; font.pixelSize: 18 * hh.s
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
                    border.color: slot.sel ? hh.glow : "#3a545a"
                    clip: true
                    Behavior on width { enabled: hh.anim; NumberAnimation { duration: 170; easing.type: Easing.OutCubic } }
                    Image { anchors.fill: parent; anchors.margins: parent.border.width; visible: source !== ""
                            source: slot.it && slot.it.cover ? slot.it.cover : ""; fillMode: Image.PreserveAspectCrop
                            asynchronous: true; sourceSize.width: 256 }
                    Icon { anchors.centerIn: parent; visible: !(slot.it && slot.it.cover)
                           name: slot.it ? (slot.it.gid.indexOf("web:") === 0 ? "c_cloud" : Theme.consoleFor(slot.it.platform, "pad")) : "pad"
                           size: art.width * 0.56; color: slot.sel ? "#b5f0ff" : "#e3e9eb" }
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
                           color: "#9debff"; font.pixelSize: 20 * hh.s }
                }
            }
        }
        Text {
            visible: hh.items.length === 0
            y: listLayer.selY; width: parent.width; wrapMode: Text.WordWrap
            text: "Nothing in here yet."; color: "#dce4e6"; font.pixelSize: 26 * hh.s
        }
    }

    // ------------------------------------------------------------ hints and Online
    Rectangle { x: 0; width: parent.width; y: parent.height - 62 * hh.s; height: 1; color: "#2646cef0" }
    Row {
        x: 30 * hh.s; anchors.bottom: parent.bottom; anchors.bottomMargin: 16 * hh.s
        spacing: 30 * hh.s
        Repeater {
            model: [ { k: "A", t: "Enter" }, { k: "B", t: "Back" }, { k: "X", t: "Favourite" }, { k: "Y", t: "Settings" } ]
            Row {
                required property var modelData
                spacing: 10 * hh.s
                HandButton { letter: modelData.k; s: hh.s; anchors.verticalCenter: parent.verticalCenter }
                Text { text: modelData.t; color: "#e5ebec"; font.pixelSize: 21 * hh.s; anchors.verticalCenter: parent.verticalCenter }
            }
        }
    }
    Row {
        objectName: "hhOnline"
        anchors.right: parent.right; anchors.rightMargin: 30 * hh.s
        anchors.bottom: parent.bottom; anchors.bottomMargin: 16 * hh.s
        spacing: 10 * hh.s
        Icon { name: "globe"; size: 28 * hh.s; color: hh.status.online ? hh.glow : "#6c7175"; anchors.verticalCenter: parent.verticalCenter }
        Text { text: hh.status.online ? "Online" : "Offline"; color: "#e5ebec"; font.pixelSize: 21 * hh.s; anchors.verticalCenter: parent.verticalCenter }
    }
}
