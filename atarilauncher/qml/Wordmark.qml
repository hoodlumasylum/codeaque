import QtQuick

// The CodeAque logo (the project owner's white CODEAQUE wordmark with the A, gamepad and ring).
// `u` scales it like everything else; the full logo is 3:1, the compact one (A + ring) is square.
Item {
    id: wm
    property real u: 1
    property bool compact: false          // the top bar: the A + ring alone (the wordmark is unreadable at that size)
    width: compact ? height : height * 3
    height: (compact ? 96 : 80) * u
    Image {
        anchors.fill: parent
        source: Qt.resolvedUrl(wm.compact ? "../assets/codeaque-icon.png" : "../assets/codeaque-logo.png")
        fillMode: Image.PreserveAspectFit
        smooth: true
        mipmap: true
        asynchronous: false
    }
}
