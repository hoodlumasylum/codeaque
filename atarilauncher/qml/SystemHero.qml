import QtQuick

// The header above the games: the system's name, a line about it, an original drawing of that kind of machine and
// a few plain numbers (no ratings, no logos). Dark charcoal with a neon accent and a drawn perspective-grid backdrop.
Rectangle {
    id: hero
    property real u: 1
    property var info: ({})          // the tile of the system shown ({key, label, short, full, blurb, glyph, count})
    property string title: ""
    property string subtitle: ""
    property string blurb: ""
    property var chips: []           // [{icon, text}]
    radius: 24 * u
    clip: true
    gradient: Gradient {
        orientation: Gradient.Horizontal
        GradientStop { position: 0.0; color: "#c00c1218" }
        GradientStop { position: 0.55; color: "#80142836" }
        GradientStop { position: 1.0; color: "#400a0e14" }
    }
    border.width: 1.5 * u
    border.color: "#333fd4ff"

    Canvas {           // original backdrop: a neon glow and a perspective floor grid in thin cyan lines
        id: bg
        anchors.fill: parent
        opacity: 0.55
        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
        renderTarget: Canvas.FramebufferObject
        onPaint: {
            var c = getContext("2d"); var w = width, h = height
            c.reset()
            var g = c.createRadialGradient(w * 0.72, h * 0.4, 0, w * 0.72, h * 0.4, h * 0.9)
            g.addColorStop(0, "rgba(63,212,255,0.25)"); g.addColorStop(1, "rgba(63,212,255,0)")
            c.fillStyle = g; c.fillRect(0, 0, w, h)
            c.lineWidth = Math.max(1, 1.2 * hero.u)
            var hz = h * 0.55, vx = w * 0.72
            for (var i = -10; i <= 10; i++) {          // lines running to the horizon
                c.strokeStyle = "rgba(63,212,255," + (0.35 - Math.abs(i) * 0.02) + ")"
                c.beginPath(); c.moveTo(vx, hz); c.lineTo(vx + i * w * 0.09, h); c.stroke()
            }
            for (var k = 1; k <= 6; k++) {             // and across, closer together near the horizon
                var y = hz + (h - hz) * Math.pow(k / 6, 1.8)
                c.strokeStyle = "rgba(63,212,255," + (0.12 + k * 0.04) + ")"
                c.beginPath(); c.moveTo(w * 0.3, y); c.lineTo(w, y); c.stroke()
            }
            c.strokeStyle = "rgba(160,235,255,0.45)"
            c.beginPath(); c.moveTo(w * 0.3, hz); c.lineTo(w, hz); c.stroke()
        }
    }
    Rectangle {        // darker left side so the text is easy to read
        anchors.fill: parent
        radius: parent.radius
        gradient: Gradient {
            orientation: Gradient.Horizontal
            GradientStop { position: 0.0; color: "#cc080b10" }
            GradientStop { position: 0.55; color: "#40080b10" }
            GradientStop { position: 1.0; color: "#00000000" }
        }
    }
    Icon {             // the machine
        anchors.right: parent.right
        anchors.rightMargin: parent.width * 0.04
        anchors.verticalCenter: parent.verticalCenter
        size: parent.height * 0.86
        name: Theme.consoleFor(hero.info.key, hero.info.glyph)
        color: Theme.accent
        stroke: 0.9
    }
    Column {
        anchors.left: parent.left
        anchors.leftMargin: 28 * hero.u
        anchors.verticalCenter: parent.verticalCenter
        width: parent.width * 0.6
        spacing: 6 * hero.u
        Text {
            width: parent.width
            text: hero.title.toUpperCase()
            color: "white"
            font.pixelSize: 54 * hero.u
            font.bold: true
            font.letterSpacing: 4 * hero.u
            elide: Text.ElideRight
            fontSizeMode: Text.HorizontalFit
            minimumPixelSize: 26 * hero.u
        }
        Text {
            visible: text !== "" && text.toUpperCase() !== hero.title.toUpperCase()
            width: parent.width
            text: hero.subtitle.toUpperCase()
            color: Theme.accent
            font.pixelSize: 17 * hero.u
            font.letterSpacing: 3 * hero.u
            elide: Text.ElideRight
        }
        Text {
            width: parent.width
            text: hero.blurb
            color: "#d9dde8"
            font.pixelSize: 17 * hero.u
            wrapMode: Text.Wrap
            maximumLineCount: 2
            elide: Text.ElideRight
        }
        Item { width: 1; height: 4 * hero.u }
        Row {
            spacing: 10 * hero.u
            Repeater {
                model: hero.chips
                Rectangle {
                    required property var modelData
                    height: 38 * hero.u
                    width: chipRow.implicitWidth + 24 * hero.u
                    radius: 10 * hero.u
                    color: "#a60b0f14"
                    border.width: 1.2 * hero.u
                    border.color: "#66e8c25a"
                    Row {
                        id: chipRow
                        anchors.centerIn: parent
                        spacing: 8 * hero.u
                        Icon { name: modelData.icon; size: 20 * hero.u; color: Theme.accent; anchors.verticalCenter: parent.verticalCenter }
                        Text { text: modelData.text; color: "white"; font.pixelSize: 15 * hero.u; font.bold: true
                               anchors.verticalCenter: parent.verticalCenter }
                    }
                }
            }
        }
    }
}
