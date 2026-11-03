# Doc-tree on synodes — architecture (agreed 2026-11-03)

Design / Decision on integrating semantic-doc to synodes.

## Doctree Data Structure

- **Working/editing copy**: local `doctree` table on the editing synode (doctree.jserv), not a syntity.
  parentId, auto-keys, reshape (`onModifyTree`) and autosave work locally; nothing here syncs.

- **Committed doc**: a `h_photos` record, mime `application/vnd.semantic-doc.doctree+json`,
  file = package `{doc, nodes}` (DoctreeResp shape). Synced by docsync like any upload.
  - fields: formatVersion, docId (stable), version / baseVersion, derivedFrom
  - commit = replace the record or create a new one — user's choice
  - replacing a record is a delete & insert, which changes its syn-uid, see issue

      * semantic-DA, DBSyntableTest.test06Doctree(): doc-tree records in h_photos are verified.

        see semantic.DA/test/res/console-2026-11-03-extend-doctree-rec.txt

      * docsync: the same with extFilev2 and file transfer (doc-ref resolving).
      * Updating uri is not supported by the engine, see issue

    [Updating an ext-file record's content changes its syn-uid](docsphinx/issues.rst#issue-extfile-uri-update)
  - album views filter this mime out

- **References** (media and other docs): global ids only (io_oz_synuid / (docId, nid)).

  Unresolved refs render as placeholders. A ref to a local/private resource renders only where
  it lives — intended; referencing public resources only is natural content protection.

## Locking
- Lock table on the hub only, plain table, not synced.
- Docs are editable at the hub unless locked; a synode may batch-lock docs (also for offline work).
- Locks expire; hub admin can release. Without a lock (or hub unreachable): read-only.
- Lock is needed only to modify a committed record; otherwise the user creates a new doc
  (derivedFrom the source). No conflicts by construction.

## HTML
- Not synced. Each synode builds html locally from its copy of the record, with native
  Sphinx + Portfolio theme, versions pinned to the client's Pyodide preview (enforced per domain/user).
- Built in a background job after a record arrives/changes or a local commit — never inside
  the sync transaction. Differences while a record isn't synced yet are accepted.
