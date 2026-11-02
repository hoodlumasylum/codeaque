import QtQuick

// Gentle falling snow (the Christmas theme). Original: plain soft dots at three depths, drifting sideways a little.
// Light on weak chips: 70 flakes with Reduce effects off, 25 with it on; nothing moves with Reduce animations.
Item {
    id: snow
    property bool active: true
    visible: active
    clip: true
    Repeater {
        model: snow.active ? (Theme.fx ? 70 : 25) : 0
        Rectangle {
            id: flake
            required property int index
            readonly property real depth: ((index * 37) % 100) / 100          // 0 far .. 1 near
            readonly property real seed: ((index * 61) % 97) / 97
            property real p: seed
            width: (2.5 + depth * 5) * Math.max(1, snow.height / 720); height: width; radius: width / 2
            color: "white"; opacity: 0.35 + 0.55 * depth
            x: snow.width * ((index * 0.6180339) % 1) + Math.sin((p % 1) * 6.283 * 2 + index) * 22 * depth
            y: -20 + (p % 1) * (snow.height + 40)
            NumberAnimation on p { from: flake.seed; to: flake.seed + 1; loops: Animation.Infinite
                                   duration: 26000 - flake.depth * 14000; running: snow.active && Theme.anim }
        }
    }
}
