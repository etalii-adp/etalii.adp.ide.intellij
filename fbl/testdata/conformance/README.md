# FBL conformance corpus

The example bindings (`*.fbl`), round-trip fixtures (`fixtures/`) and example registrations (`registrations/`) of FBL, the Format Binding Language, version 0.1.

| | |
| --- | --- |
| Source | [`specifications/fbl/`](https://github.com/etalii-adp/etalii.adp/tree/2c873cf8f550512f3ced03446165822daa81f94a/specifications/fbl) in `etalii-adp/etalii.adp` |
| Commit | `2c873cf8f550512f3ced03446165822daa81f94a` |
| Licence | [Apache License 2.0](https://github.com/etalii-adp/etalii.adp/blob/2c873cf8f550512f3ced03446165822daa81f94a/LICENSE) (`Apache-2.0`), Copyright © Peter Vrenken 2026 |

These files are never edited here. Every one is byte for byte the blob of that commit: fixtures state byte offsets, so `.gitattributes` keeps `fbl/testdata/**` from line-ending conversion, and `CorpusUnchangedTest` compares each file with its line in `SHA256SUMS`.

A newer FBL is taken up by copying the whole corpus again at a newer commit, with conversion off, and writing `SHA256SUMS` anew:

```sh
git -C ../etalii.adp -c core.autocrlf=false -c core.eol=lf archive --format=tar <commit> \
    $(git -C ../etalii.adp ls-tree --name-only <commit> specifications/fbl/ | grep '\.fbl$') \
    specifications/fbl/fixtures specifications/fbl/registrations \
  | tar -xf - -C fbl/testdata/conformance --strip-components=2
```

Without the two `-c` settings `git archive` converts the `.fbl` files as a checkout would.
