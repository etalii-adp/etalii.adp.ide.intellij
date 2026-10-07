# Real files for the FBL bindings

Real files of `etalii.adp.ide.standalone`, on which the example bindings are tried: 41 bodies and the 32 registrations beside them that name one of them. The repository's own mind maps under `freemind/testdata` are the corpus of the sixth declared binding and are not copied here.

| | |
| --- | --- |
| Source | [`etalii-adp/etalii.adp.ide.standalone`](https://github.com/etalii-adp/etalii.adp.ide.standalone/tree/25fc7b4af7a99989d23d75d1af9d844303243b9d/src), with the paths under `src/` kept |
| Commit | `25fc7b4af7a99989d23d75d1af9d844303243b9d` |
| Licence | [Apache License 2.0](https://github.com/etalii-adp/etalii.adp.ide.standalone/blob/25fc7b4af7a99989d23d75d1af9d844303243b9d/LICENSE) (`Apache-2.0`). None of these files comes from a third party. |

| Binding | Files | Selected by |
| --- | --- | --- |
| timeline | 15 | `.tml` |
| causal loop | 4 | `.cld` |
| structurizr | 16 | `.dsl` |
| databricks job | 4 | `.yml` or `.yaml` holding `task_key` |
| databricks pipeline | 2 | `.json` holding `"libraries"` |
| registrations | 32 | `.adp` whose `body` header, or whose base name, names one of the 41 |

The selection is that of standalone's own real-file tests (`RealFiles/RealFileCorpus.cs`), so both hosts try the same files and their divergence records can be compared entry by entry.

The files are never edited here. They were written with `git archive` at that commit, which gives each file as standalone checks it out: its `.gitattributes` asks for CRLF, so most of these files end their lines with CRLF although the stored blobs have LF. That is the form standalone's tests read. `.gitattributes` here keeps `fbl/testdata/**` from any further conversion.
