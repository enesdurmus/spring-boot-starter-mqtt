# Releasing

Pushing a `v*.*.*` tag is the only way to publish: the
[publish workflow](.github/workflows/maven-publish.yml) has no other trigger, and it stops before
uploading anything if the tag and the POM version disagree.

## Checklist

1. `pom.xml` — set `<version>` to the release version (no `-SNAPSHOT`).
2. `pom.xml` — set `<project.build.outputTimestamp>` to the release date; a stale value makes that
   version's build non-reproducible.
3. `CHANGELOG.md` — replace `Unreleased` with the release date and check the link at the bottom
   points at the new tag.
4. `README.md` — bump the version in the dependency snippets.
5. Verify the release artifacts build locally:
   ```bash
   ./mvnw -B -P release verify -Dgpg.skip=true -Dmqtt.it.required=true
   ```
6. Commit, then tag and push:
   ```bash
   git tag -a v2.0.0 -m "v2.0.0"
   git push origin main --follow-tags
   ```
7. The workflow verifies the version, deploys to Central and opens the GitHub release. Check
   [Central](https://central.sonatype.com/artifact/io.github.enesdurmus/mqtt-spring-boot-starter)
   once it finishes; publication can take a few minutes to become searchable.

## If a release fails

Re-run the failed run from the Actions tab. If the fix needs a code change, delete the tag locally
and remotely (`git tag -d v2.0.0 && git push --delete origin v2.0.0`), fix, and tag again — but
only if nothing reached Central. Published versions are immutable there, so after a successful
upload the fix is a new version.

## Required secrets

| Secret | Purpose |
|---|---|
| `CENTRAL_TOKEN_USERNAME` / `CENTRAL_TOKEN_PASSWORD` | Central Portal user token |
| `GPG_SIGNING_KEY` | ASCII-armoured private key used to sign the artifacts |
| `GPG_PASSPHRASE` | Passphrase for that key |

## After the release

Open the next development version in `pom.xml` (`2.1.0-SNAPSHOT`) and add a fresh `Unreleased`
section to `CHANGELOG.md`.
