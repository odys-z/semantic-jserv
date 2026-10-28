# ISSUE anson-body-fields: AnsonBody fields not in Java AnsonBody

- Found: 2026-10-25, master & Portfolio 0.8.0 (`portfolio-0.8`)
- Status: closed, 2026-11-02, fixed in semantics.py3 0.6.18, semantic-jserv 1e3731f
- Tags: `ISSUE anson-body-fields` in
  - `antson/semantics.py3/src/semanticshare/io/odysz/semantic/jprotocol.py`, `AnsonBody`
  - `Anclient/js/semantier/protocol.ts`, `AnsonBody`

## Problem

Java, Python, C++ (anson.cmake, anclient.cmake) and TS share the same json structure.
`io.odysz.semantic.jprotocol.AnsonBody` (Java) has `parent` (`@AnsonField(ref=enclosing)`), `uri` and `a`;
anson.cmake's `jprotocol.h` has `a` and `uri` only.

Other versions declare more fields on AnsonBody:

| field | Java owner | Python (semantics.py3) | TS (semantier) |
|---|---|---|---|
| `parent` | AnsonBody, ref = enclosing, planned to be removed | always sent, `null` | set to the message's type |
| `rs`, `m`, `map` | AnsonResp | declared | - |
| `opts`, `addr`, `version`, `seq` | AnsonMsg | declared | `version`, `seq` declared |
| `ajax`, `ssInf` | - (`ssInf`: AnSessionResp) | - | declared |

## Decisions

- Python: the fields are marked `@deprecated ISSUE anson-body-fields` (not removed yet).
  Before removing, `AnsonResp` must declare `rs`, `m`, `map` itself (`AnsonResp.msg()` reads `m`),
  and the py3 clients (anclient.py3, synode.py) and jserv-gen.py3 checked.
- TS: removed from `AnsonBody`; `ajax` moved to `AnsonResp` (client side only), `ssInf` is `AnSessionResp`'s.
- `parent`: planned to be removed in all versions. Proposed (not settled): Java's `AnsonBody.parent`
  gets `@AnsonField(ignoreFrom)` meanwhile.
