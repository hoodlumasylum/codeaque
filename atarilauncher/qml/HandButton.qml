import QtQuick

// A neutral face-button picture for Handheld mode: a plain outlined circle with its letter (no console maker's symbols).
Rectangle {
    property string letter: "A"
    property real s: 1
    width: 30 * s; height: width; radius: width / 2
    color: "transparent"
    border.width: 2 * s
    border.color: "#d9e0d6"
    Text { anchors.centerIn: parent; text: parent.letter; color: "#eef2ea"; font.pixelSize: 16 * parent.s; font.weight: Font.DemiBold }
}
