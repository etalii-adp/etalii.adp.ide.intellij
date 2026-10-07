# EtAlii.Adp.IntelliJ

An IntelliJ Platform plug-in that adds ADP tools for text-based files, for every IntelliJ Platform IDE (IntelliJ IDEA, Rider, WebStorm, PyCharm and the rest). Each tool opens in a proper IDE editor: registered for its file type, on the IDE's undo/redo stack, with the IDE's modified state and save behaviour, and interoperable with the IDE's text editor on the same document. The tools today are two diagrams: FreeMind mind maps (`.mm`) and draw.io diagrams (`.drawio`).

ADP's vocabulary is defined in the glossary, [`docs/terminology.md` in etalii.adp](https://github.com/etalii-adp/etalii.adp/blob/develop/docs/terminology.md): a **tool** is a diagram, a designer or an editor. Say "tool" when all kinds are meant and the kind when one is meant; never "designer" for a tool in general. Platform API names (`FileEditor`, `FileEditorProvider`, tool windows) and third-party names ("draw.io diagram", FreeMind) keep theirs.

## How work is done here: spec-driven development (GitHub Spec Kit)

Every change starts as a specification, written in [etalii.adp](https://github.com/etalii-adp/etalii.adp) rather than here: this repository has no Spec Kit setup of its own. A feature is `specs/NNN-feature-name/` in etalii.adp, specified, planned and split into tasks with etalii.adp's Spec Kit skills as its `CLAUDE.md` describes; its tasks name files here as `etalii.adp.ide.intellij/...`, and the code arrives here in a pull request of its own, on a branch named as the feature's. That pull request's description links the feature's folder in etalii.adp and names the etalii.adp commit its tasks were taken from, and the tasks are ticked in etalii.adp only once it is merged. Work on it from etalii.adp's folder, with this repository's clone beside it, or set `SPECIFY_INIT_DIR` to etalii.adp's folder.

This repository's principles, which every plan for it is checked against, are in etalii.adp's [`.specify/memory/repositories/etalii.adp.ide.intellij.md`](https://github.com/etalii-adp/etalii.adp/blob/develop/.specify/memory/repositories/etalii.adp.ide.intellij.md). Its earlier features keep their numbers in etalii.adp's [`specs/etalii.adp.ide.intellij/`](https://github.com/etalii-adp/etalii.adp/tree/develop/specs/etalii.adp.ide.intellij), so a "spec 003" in code, commits or pull requests here is `specs/etalii.adp.ide.intellij/003-*` there.

Specs say *what* and *why*; plans say *how*. Do not put implementation choices in a spec.

## Conventions

- The build is Gradle with the IntelliJ Platform Gradle Plugin: `./gradlew build` runs everything, `./gradlew test` the headless tests, `./gradlew integrationTest` the real-IDE tests, `./gradlew runIde` a sandbox IDE. Modules: `core` (framework), `freemind` and `drawio` (formats), `fbl` (the generic FBL implementation, used by no tool yet), `testing` (test kit).
- One feature per branch, named `features/<number>-<name>` (the name etalii.adp's Spec Kit gave the feature). The one exception is `claude/<name>`, which Claude's cloud sessions are handed by their harness.
- A feature branch is never merged locally into `develop`. When its tasks are done, push the branch from the worktree it was built in to `origin` and open a pull request into `develop`; nothing reaches `develop` except through a pull request. When the pull request is merged or closed, delete the branch locally and on `origin`, and remove the worktree.
- End commit messages written by an agent with a `Co-Authored-By:` trailer naming the model.
- When writing markdown files do not split lines to ensure a maximum line length is honored.
