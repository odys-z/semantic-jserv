"""
"""
from dataclasses import dataclass

from anson.io.odysz.anson import Anson

@dataclass
class ResPath(Anson):
    path: str
    resource: str
    allowDir: bool

    def __init__(self, **kwargs):
        super().__init__()
        self.path, self.resource = kwargs.get('path', '/'), kwargs.get('resources', 'web-dist')
        self.allowDir = kwargs.get('allowDir', False)


@dataclass
class WebConfig(Anson):
    port: int
    paths: [ResPath]
    startHandler: [str]

    def __init__(self):
        super().__init__()

        self.port = 8900
        self.paths = [ResPath(path='/', resource='web-dist')]
        self.startHandler = None
