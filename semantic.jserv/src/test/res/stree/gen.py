"""
Reference model and test-data generator for SemanticTreeTest (s-tree modification, SQLite).

    cd semantic.jserv/src/test/res/stree && python3 gen.py

Generates stree-cases.json, loaded by SemanticTreeTest with Anson (types StreeCases, StreeCase,
StreeStep, StreeOp). Don't edit the json by hand.

Model rules (mirroring the Java code):
  fullpath   top-level: pad(sort); child: parent.fullpath + '.' + pad(sort)   (Reforest.shapeSubtree, width 2)
  sort       1..n per parent, renumbered after every change
  indents    TreeIndenode.indents(): top-level []; child = parentChildIndents + (childx if last else childi)
             parentChildIndents = parent.indents with last flag -> space if parent is last, else vlink
             (top-level nodes are never tagged last; their child prefix is [])
  move       toIndex = final position among the target's children (after detaching the node)
  root       closest ancestor whose subtree contains every node with changed fullpath/indents:
             LCA of old/new parent by fullpath prefix; None (= full reload) when the top level is involved
  rejected   "cycle" (into itself or a descendant), "no-op" (same parent, same index); the table is unchanged
"""
import json, os

W = 2
def pad(n): return str(n).zfill(W)

TABLE = "doctree"

FIXTURE = [  # nid, parent, nodetype, text   (order within parent = sort)
    ("D1", None, "doc", "Doc 1"),
    ("A", "D1", "section", "A"),
    ("a1", "A", "para", "a1"),
    ("a2", "A", "section", "a2"),
    ("a21", "a2", "para", "a21"),
    ("a22", "a2", "para", "a22"),
    ("a3", "A", "para", "a3"),
    ("B", "D1", "section", "B"),
    ("b1", "B", "para", "b1"),
    ("C", "D1", "section", "C (empty)"),
    ("E", "D1", "section", "E"),
    ("e1", "E", "para", "e1"),
    ("e2", "E", "section", "e2"),
    ("e21", "e2", "para", "e21"),
    ("D2", None, "doc", "Doc 2"),
    ("F", "D2", "section", "F"),
    ("f1", "F", "para", "f1"),
]

class Tree:
    def __init__(self):
        self.kids = {None: []}; self.par = {}; self.meta = {}
        for nid, p, t, txt in FIXTURE:
            self.kids.setdefault(p, []).append(nid); self.kids.setdefault(nid, [])
            self.par[nid] = p; self.meta[nid] = (t, txt)

    def ancestors(self, n):
        while n is not None:
            yield n; n = self.par[n]

    def rows(self):
        out = {}
        def walk(p, fp_prefix, prefix):
            ks = self.kids[p]
            for i, n in enumerate(ks, 1):
                last = i == len(ks)
                fp = pad(i) if p is None else f"{fp_prefix}.{pad(i)}"
                ind = [] if p is None else prefix + ["childx" if last else "childi"]
                out[n] = dict(nid=n, parent=p, sort=i, fullpath=fp, indents=ind,
                              nodetype=self.meta[n][0], text=self.meta[n][1])
                walk(n, fp, [] if p is None else ind[:-1] + ["space" if last else "vlink"])
        walk(None, None, [])
        return out

def lca_by_fullpath(rows, a, b):
    if a is None or b is None: return None
    pa, pb = rows[a]["fullpath"].split("."), rows[b]["fullpath"].split(".")
    common = []
    for x, y in zip(pa, pb):
        if x != y: break
        common.append(x)
    if not common: return None
    fp = ".".join(common)
    return next(r["nid"] for r in rows.values() if r["fullpath"] == fp)

def apply(t, op):
    """Returns (rejected | None, root). Mutates t."""
    rows = t.rows(); kind = op["op"]
    if kind == "move":
        n, to, ix = op["id"], op["toParent"], op["toIndex"]
        if to == n or (to is not None and n in t.ancestors(to)): return "cycle", None
        frm = t.par[n]
        if frm == to and t.kids[frm].index(n) == ix: return "no-op", None
        root = lca_by_fullpath(rows, frm, to)
        t.kids[frm].remove(n)
        if not 0 <= ix <= len(t.kids[to]): raise ValueError(op)
        t.kids[to].insert(ix, n); t.par[n] = to
    elif kind == "insert":
        n, to, ix = op["id"], op["toParent"], op["toIndex"]
        root = to
        t.kids[to].insert(ix, n); t.kids[n] = []; t.par[n] = to
        t.meta[n] = (op["nodetype"], op["text"])
    elif kind == "remove":
        n = op["id"]; root = t.par[n]
        t.kids[root].remove(n)
        def drop(x):
            for k in t.kids.pop(x): drop(k)
            del t.par[x]; del t.meta[x]
        drop(n)
    else: raise ValueError(op)
    return None, root

def covers(rows, root, before):
    """Every node whose fullpath/indents changed, or that was added, lies in root's subtree."""
    if root is None: return True
    fp = rows[root]["fullpath"]
    dirty = [n for n, r in rows.items()
             if n not in before or (before[n]["fullpath"], before[n]["indents"]) != (r["fullpath"], r["indents"])]
    return all(rows[n]["fullpath"] == fp or rows[n]["fullpath"].startswith(fp + ".") for n in dirty)

def mv(n, p, i): return {"op": "move", "id": n, "toParent": p, "toIndex": i}
def ins(n, p, i, t="para"): return {"op": "insert", "id": n, "toParent": p, "toIndex": i, "nodetype": t, "text": n}
def rm(n): return {"op": "remove", "id": n}

CASES = [
  # reorder within one parent
  ("R1", "a1 down (middle swap; fullpaths change, indents don't)", [mv("a1", "A", 1)]),
  ("R2", "a3 (last) to top: a2 becomes last", [mv("a3", "A", 0)]),
  ("R3", "a1 to bottom: a3 stops being last", [mv("a1", "A", 2)]),
  ("R4", "a1 up at index 0: no-op", [mv("a1", "A", 0)]),
  ("R5", "E (last section) up: C becomes last", [mv("E", "D1", 2)]),
  # move between parents
  ("M1", "b1 into empty sibling section C", [mv("b1", "C", 0)]),
  ("M2", "a1 appended to E: e2 stops being last", [mv("a1", "E", 2)]),
  ("M3", "a3 (last of A) to front of B: a2 becomes last", [mv("a3", "B", 0)]),
  ("M4", "subtree a2 (with children) to front of e2, deep to deep", [mv("a2", "e2", 0)]),
  ("M5", "section B (with child) into section A, middle", [mv("B", "A", 1)]),
  # indent / outdent
  ("I1", "indent a3 under previous sibling a2, as last child", [mv("a3", "a2", 2)]),
  ("I2", "indent C under previous sibling B", [mv("C", "B", 1)]),
  ("O1", "outdent a22 to right after a2: a21 becomes last", [mv("a22", "A", 2)]),
  ("O2", "outdent e21 (only child) to after e2, end of E", [mv("e21", "E", 2)]),
  # top level / cross tree: root null = full reload
  ("X1", "f1 from Doc 2 into A, cross tree", [mv("f1", "A", 1)]),
  ("X2", "C to top level between D1 and D2", [mv("C", None, 1)]),
  ("X3", "D2 before D1, reorder top level", [mv("D2", None, 0)]),
  # insert / remove
  ("N1", "insert n into empty C", [ins("n", "C", 0)]),
  ("N2", "append n to E: e2 stops being last", [ins("n", "E", 2)]),
  ("N3", "insert n at front of B", [ins("n", "B", 0)]),
  ("N4", "insert top-level doc D0 first", [ins("D0", None, 0, "doc")]),
  ("RM1", "remove a3 (last): a2 becomes last", [rm("a3")]),
  ("RM2", "remove subtree a2: a3 renumbered", [rm("a2")]),
  ("RM3", "remove E (last section, with subtree): C becomes last", [rm("E")]),
  # rejected (moving into a paragraph is semantic-doc's rule, not tested here)
  ("V1", "move A into its own descendant a21", [mv("A", "a21", 0)]),
  ("V2", "move a1 into itself", [mv("a1", "a1", 0)]),
  # sequences
  ("Q1", "a1 down three times, the third is a no-op at bottom",
     [mv("a1", "A", 1), mv("a1", "A", 2), mv("a1", "A", 2)]),
  ("Q2", "indent a3 under a2, then outdent it back: equals the fixture",
     [mv("a3", "a2", 2), mv("a3", "A", 2)]),
]

def lines(rows):
    out = []
    for r in sorted(rows.values(), key=lambda r: r["fullpath"]):
        out.append(f'{r["nid"]} {r["parent"] or "-"} {r["sort"]} {r["fullpath"]} {",".join(r["indents"]) or "-"}')
    return out

PKG = "io.odysz.semantic.ext"

def op_json(op):
    return {"type": f"{PKG}.StreeOp", "a": op["op"], "nid": op["id"],
            "toParent": op.get("toParent"), "toIndex": op.get("toIndex", -1),
            "nodetype": op.get("nodetype"), "text": op.get("text")}

def main():
    fixture = Tree().rows()
    inserts = []
    for r in sorted(fixture.values(), key=lambda r: r["fullpath"]):
        p = "null" if r["parent"] is None else f"'{r['parent']}'"
        inserts.append(f"insert into {TABLE} (nid, parent, sort, fullpath, nodetype, text) values "
                       f"('{r['nid']}', {p}, {r['sort']}, '{r['fullpath']}', '{r['nodetype']}', '{r['text']}')")

    cases = []
    for cid, desc, ops in CASES:
        t = Tree(); steps = []
        for op in ops:
            before = t.rows()
            rejected, root = apply(t, op)
            after = t.rows()
            if rejected: assert lines(before) == lines(after)
            else: assert covers(after, root, before), (cid, op, root)
            steps.append({"type": f"{PKG}.StreeStep", "op": op_json(op),
                          "root": root, "rejected": rejected, "tree": lines(after)})
        cases.append({"type": f"{PKG}.StreeCase", "id": cid, "desc": desc, "steps": steps})

    doc = {
        "type": f"{PKG}.StreeCases",
        "table": TABLE,
        "stree": f",{TABLE},nid,parent,fullpath,sort,text,nodetype,false",
        "ddl": [f"drop table if exists {TABLE}",
                f"create table {TABLE} (nid varchar(12) primary key, parent varchar(12), sort integer, "
                f"fullpath varchar(256), nodetype varchar(12), text varchar(256))",
                f"create index {TABLE}_fullpath on {TABLE}(fullpath)"],
        "fixture": inserts,
        "fixtureTree": lines(fixture),
        "cases": cases,
    }
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, "stree-cases.json")
    with open(out, "w") as f: json.dump(doc, f, indent=1)
    print("written", out, f"({len(cases)} cases)")

main()
