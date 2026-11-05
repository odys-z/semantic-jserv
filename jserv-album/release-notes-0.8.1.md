# Release notes

## 0.8.1 — doc-tree tier (semantic-doc), in progress

album-web edits doc trees (semantic-doc's AnDoctreeditor) on a synode, previews them in the
browser and gets html built by the synode. See semantic-doc's `architecture-doctree-synode`.

### Working copy

- Own SQLite file, `doctree.db`, with its own connection; created / migrated by the doc-tree
  tier at start-up. The album's main db is unchanged.
- Tables: doc header (DocInf, state) and `s_docnodes` (docId, nid, parentId, sort, fullpath,
  kind, json, rst, rstver, text, src, style, resId, refId); semantics in xml.
- Imported docs carry structure and text only; the client fills Tiptap json / rst and autosaves
  each paragraph.
- RST is a node field, one file per section (`<nid>.rst`, the doc root is `index.rst`),
  assembled at the client.

### Doc-tree tier (port `doctree`, doctree.jserv)

- DoctreeReq / DoctreeResp extend AnDatasetReq / AnDatasetResp.
- `r/docs`, `r/tree`, `u/node`, `u/move | i | d` (SemanticTree.onModifyTree), `c/doc`.
- `c/import`: answers at once, parses the docx in the background (state parsing → ready / failed).
- `c/commit`: exports the doc into an h_photos record (doc-tree mime, folder `.doctree`);
  replace (delete & insert) or new.

### Html

- Client preview: Sphinx in Pyodide (wheels from CDN), in album-web's doc-tree page.
- Synode: built in the background after a local commit, after a synced record's file is resolved,
  removed with the record; reconciled at start-up. Never inside the sync transaction.
- `sphinx-build` is packed as another executable of synode.py's `synode-setup.spec`, sharing the
  frozen Python (Windows 10+, Linux). Versions pinned in semantic-doc's `sphinx-req.txt`, the same
  list the preview loads. A missing toolchain skips the build with a warning.

### album-web

- `/doctree` page served by the synode (JservDoctier), with the preview pane.
