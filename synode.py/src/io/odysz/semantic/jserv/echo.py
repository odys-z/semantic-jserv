from src.io.odysz.semantic.jprotocol import AnsonBody, AnsonMsg


class A:
    echo = "echo"
    inet = "inet"


class EchoReq(AnsonBody):

    def __init__(self, parent: AnsonMsg = None):
        super().__init__(parent)
