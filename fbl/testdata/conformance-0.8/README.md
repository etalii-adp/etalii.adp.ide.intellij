# FBL conformance corpus, FBL 0.8: the insert constant

The four round-trip fixtures of the insert constant that FBL 0.8 added (section 6.2), `fixtures/insert-constant-yaml`, `-json`, `-yaml-read` and `-json-read`, and `refused/insert-constant-collision.fbl`, a binding a host must refuse (section 14.1, step 6). etalii.adp's `refused/README.md` is not copied, since its links point into that folder. Each fixture carries its own binding, `board.fbl`, beside its body.

| | |
| --- | --- |
| Source | [`specifications/fbl/`](https://github.com/etalii-adp/etalii.adp/tree/59e2117738fb0e254f961d093b25038c90af78fe/specifications/fbl) in `etalii-adp/etalii.adp` |
| Commit | `59e2117738fb0e254f961d093b25038c90af78fe`, the merge of etalii.adp #427, which corrected the undo steps of the two add fixtures etalii.adp #426 introduced |
| Feature | [etalii.adp spec 033](https://github.com/etalii-adp/etalii.adp/tree/develop/specs/033-fbl-insert-constant), tasks T029 to T031 |
| Licence | [Apache License 2.0](https://github.com/etalii-adp/etalii.adp/blob/59e2117738fb0e254f961d093b25038c90af78fe/LICENSE) (`Apache-2.0`), Copyright © Peter Vrenken 2026 |

**Why it sits apart from `../conformance/`.** That folder is the whole corpus of FBL 0.1, and its rule is that a newer FBL is taken up by copying the whole corpus again. This library reads FBL 0.1 and implements the insert constant of 0.8 without the rest of 0.2 to 0.8, so a whole copy would bring in fixtures it does not pass. These files are copied on their own instead, and `../conformance/` keeps its rule. When the whole corpus is copied again at a commit that has them, this folder goes.

These files are never edited here. Each is byte for byte the blob of that commit (`.gitattributes` keeps `fbl/testdata/**` from line-ending conversion), and `CorpusUnchangedTest` compares each with its line in `SHA256SUMS`. `ConformanceFixturesTest` runs the four fixtures with those of `../conformance/`, and `LoadingTest` loads the refused binding and expects one refusal, at the rule `task`'s constant. The fixtures' bindings declare `"fbl": "0.8"`, which this library loads with the newer-version warning of section 2.7.

They were copied with:

```sh
git -C ../etalii.adp -c core.autocrlf=false -c core.eol=lf archive --format=tar 59e2117738fb0e254f961d093b25038c90af78fe \
    specifications/fbl/fixtures/insert-constant-json specifications/fbl/fixtures/insert-constant-json-read \
    specifications/fbl/fixtures/insert-constant-yaml specifications/fbl/fixtures/insert-constant-yaml-read \
    specifications/fbl/refused/insert-constant-collision.fbl \
  | tar -xf - -C fbl/testdata/conformance-0.8 --strip-components=2
```

What it does not demonstrate: no fixture gives a constant beside a `skeleton`, and `refused/` holds one document, refused for a writable attribute on the constant's key. The refusals for `id.from`, a relation's ends, `emit`, a value that is not a scalar and a family other than yaml and json are cases of `LoadingTest`. This library's runner compares a fixture's element types, not the attribute values a `-read` fixture lists.
