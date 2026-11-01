import QtQuick
import QtQuick.Effects

// Text with a metal finish: "chrome" (silver) or "gold". With effects on, a vertical metal gradient is cut out by the
// letters and a soft coloured glow sits behind; with Reduce effects, plain light metal colour and no glow.
Item {
    id: ct
    property string text: ""
    property real px: 40
    property real spacing: 4
    property int weight: Font.Black
    property string metal: "chrome"
    property color glowColor: "transparent"
    property real glow: 0                    // 0 .. 1
    implicitWidth: label.implicitWidth
    implicitHeight: label.implicitHeight
    width: implicitWidth; height: implicitHeight

    Text {
        id: label
        text: ct.text
        font.pixelSize: ct.px; font.weight: ct.weight; font.letterSpacing: ct.spacing
        color: ct.metal === "gold" ? "#f0cf6e" : "#e4e9ee"
    }
    ShaderEffectSource { id: letters; sourceItem: label; hideSource: Theme.fx; live: true; visible: false }
    Image {    // the glow behind: one soft light picture tinted in the glow colour (no shader, costs nothing)
        visible: ct.glow > 0
        anchors.centerIn: label
        width: label.width * 1.35; height: label.height * 1.9
        source: Qt.resolvedUrl(ct.metal === "gold" ? "../assets/hh_glow_gold.png" : "../assets/hh_glow.png")
        smooth: true
        opacity: ct.glow * (Theme.fx ? 0.55 : 0.35)
    }
    Rectangle {
        anchors.fill: label
        visible: Theme.fx
        layer.enabled: Theme.fx
        layer.effect: MultiEffect { maskEnabled: true; maskSource: letters; maskThresholdMin: 0.45; maskSpreadAtMin: 0.25 }
        gradient: Gradient {
            GradientStop { position: 0.0; color: ct.metal === "gold" ? "#fff6cf" : "#ffffff" }
            GradientStop { position: 0.42; color: ct.metal === "gold" ? "#ecc55c" : "#cfd6dc" }
            GradientStop { position: 0.54; color: ct.metal === "gold" ? "#8f6418" : "#6a737b" }
            GradientStop { position: 0.78; color: ct.metal === "gold" ? "#d9ac45" : "#bcc5cc" }
            GradientStop { position: 1.0; color: ct.metal === "gold" ? "#fbe7a1" : "#f4f7f9" }
        }
    }
}
