"""
"""
from dataclasses import dataclass

from anson.io.odysz.ansons import Anson

@dataclass
class ResPath(Anson):
    path: str
    resource: str
    allowDir: bool

    def __init__(self):
        self.path, self.resource = '', None
        self.allowDir = False


@dataclass
class WebConfig(Anson):
    port: int
    path: [ResPath]
    startHandler: [str]

    def __init__(self):
        self.port = 8900
        self.path = []
        self.startHandler = None
