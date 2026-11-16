# Pigu × Pigu-API — submodule cheatsheet

## The map

| Thing | Value |
|---|---|
| App repo | `8mpty/Pigu`, branch `dev`, local `~/Documents/GitHub/Pigu` |
| API fork | `8mpty/Pigu-API`, local `~/Documents/GitHub/Pigu-API` |
| Upstream API | `thedjchi/Shizuku-API` → remote `upstream` inside Pigu-API |
| Submodule | `Pigu/api` → Pigu-API, **pinned to a commit** |
| Current pin | `37ebcd3` (2025-11-27), tag `base-37ebcd3` |
| Upstream head at setup | `fa4e6a9` (2026-05-14) — 8 commits ahead |

## Rules

1. **The pin is law.** Builds use the commit recorded in Pigu's index, never Pigu-API's tip.
2. **Never `git pull` in Pigu-API** while `master` tracks `upstream/master` — it silently unpins. Use explicit refs: `git push -u origin master`, `git rebase upstream/master`.
3. **Never rename Java/AIDL packages** (`rikka.shizuku.*`, `moe.shizuku.*`). It breaks every client app and the wire contract.
4. **Keep both licenses**: `LICENSE` (Apache-2.0, Pigu) and `api/LICENSE` (MIT, RikkaW).
5. **Bump only when needed**, and build before pushing the new pin.

---

## Monthly: what changed upstream

```bash
cd ~/Documents/GitHub/Pigu-API
git fetch upstream
git log --oneline HEAD..upstream/master
git diff --stat HEAD..upstream/master
```

## Sync — no local changes yet

```bash
cd ~/Documents/GitHub/Pigu-API
git fetch upstream
git merge --ff-only upstream/master
git push origin master
```

## Sync — you have your own commits

```bash
cd ~/Documents/GitHub/Pigu-API
git fetch upstream
git branch backup/pre-sync
git rebase upstream/master        # conflicts: fix → git add <f> → git rebase --continue
                                  # give up:  git rebase --abort
git tag api-$(git rev-parse --short HEAD)
git push --force-with-lease origin master --tags
```

Prefer no force-push? `git merge upstream/master` instead (merge commit, stable SHAs).

## Sync — take only specific fixes

```bash
git cherry-pick <sha>
git push origin master
```

---

## Bump the app pin (after any sync)

```bash
cd ~/Documents/GitHub/Pigu
git submodule update --remote api
git status                          # "modified: api (new commits)"
./gradlew :manager:assembleDebug    # build BEFORE committing the pin
git add api
git commit -m "api: sync to $(git -C api rev-parse --short HEAD)"
git push
```

## Verify state

```bash
cd ~/Documents/GitHub/Pigu
git submodule status          # " 37ebcd3… api (37ebcd3)" — space = in sync, "+" = differs
git ls-tree HEAD api          # the recorded pin
git -C api remote -v          # origin → 8mpty/Pigu-API
git config --get submodule.api.url
```

---

## Making your own API changes (when you get there)

Commit them **in Pigu-API first**, on top of `master`, then bump the pin in Pigu.

```bash
cd ~/Documents/GitHub/Pigu-API
# edit aidl/, api/, provider/, rish/, server-shared/
git add -A
git commit -m "feat(api): …"
git push origin master
# then: bump the pin (section above)
```

See your own patches:

```bash
git diff base-37ebcd3..master --stat      # includes upstream if you rebased
git log --oneline base-37ebcd3..master
```

## Paths (easy to mix up)

| What | In `Pigu` | In `Pigu-API` |
|---|---|---|
| AIDL contract | `api/aidl/src/main/aidl/moe/shizuku/server/` | `aidl/src/main/aidl/moe/shizuku/server/` |
| Client API | `api/api/src/main/java/rikka/shizuku/` | `api/src/main/java/rikka/shizuku/` |
| Provider lib | `api/provider/` | `provider/` |
| Module list | `Pigu/settings.gradle` | `Pigu-API/settings.gradle` |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `error: remote upstream already exists` | It exists — drop the `remote add` line |
| `You are in 'detached HEAD' state` | `git switch master` |
| `push` rejected after rebase | `git push --force-with-lease origin master` |
| Submodule shows `+` | Pin differs from checkout — `git add api` after checking out |
| `api/` is empty | `git submodule update --init --recursive` |
| Gradle ignores the submodule | Remove `api.useLocal` / `api.dir` from `Pigu/local.properties` |
| "behind upstream/master" in Pigu-API | Expected — you're the fork. Always push explicit refs |
| CI can't fetch the submodule | Public repo → HTTPS URL in `.gitmodules` is enough; private → add a token to `actions/checkout` |

---

## TL;DR

```bash
# check
cd ~/Documents/GitHub/Pigu-API && git fetch upstream && git log --oneline HEAD..upstream/master
# sync
git rebase upstream/master && git push --force-with-lease origin master
# re-pin + build
cd ../Pigu && git submodule update --remote api && ./gradlew :manager:assembleDebug \
  && git add api && git commit -m "api: sync" && git push
```
