"""
"""
import dataclasses

from anson.io.odysz.ansons import Anson

@dataclasses
class ResPath(Anson):
    path: str
    resource: str
    allowDir: bool

    def __init__(self):
        self.path, self.resource = '', None
        self.allowDir = False


@dataclasses
class WebConfig(Anson):
    port: int
    path: [ResPath]
    startHandler: [str]

    def __init__(self):
        self.port = 8964
        self.path = []
        self.startHandler = None
