class Utils:
    def join(obj: dict, sep: str = ", ", mapper: map = None) -> str:
        if mapper is None:
            mapper = lambda x: obj[x]

        if obj:
            print(sep.join(obj))
            print(sep.join(map(mapper, obj)))
            return sep.join(map(mapper, obj))
        return None
