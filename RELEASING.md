# Release Process

Releases are published manually from the repository root. GitHub Actions does
not publish artifacts to Maven Central.

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Java 21+ | Enforced by `maven-enforcer-plugin` |
| Maven 3.9+ | `mvn` available on `PATH` |
| GPG key | Must be published to a keyserver (`keys.openpgp.org` or `keyserver.ubuntu.com`) |
| `~/.m2/settings.xml` server entry with id `central` | Username = Sonatype Central Portal token user, password = token secret |

### `~/.m2/settings.xml` server block

```xml
<servers>
  <server>
    <id>central</id>
    <username><!-- Central Portal token username --></username>
    <password><!-- Central Portal token password --></password>
  </server>
</servers>
```

Generate tokens at: https://central.sonatype.com → **Account** → **Generate User Token**.

---

## Steps

### 1. Verify tests pass

```
mvn clean test
```

Parquet consumers receive `hadoop-common` and `hadoop-mapreduce-client-core`
transitively. Verify the published POM with a consumer application that declares
only this library before release.

### 2. Bump version in `pom.xml`

Edit `<version>` in `pom.xml`. Use [Semantic Versioning](https://semver.org):

- **Patch** (bug-fix only, no API change): `X.Y.Z+1`
- **Minor** (new backward-compatible feature): `X.Y+1.0`
- **Major** (breaking change): `X+1.0.0`

### 3. Update `RELEASE_NOTES.md`

Add a section for the new version. Include:
- Breaking changes (with before/after code snippets)
- New features
- Bug fixes
- Dependency changes

### 4. Update `README.md`

Change the version badge and the Maven/Gradle coordinate snippets to the new version.

### 5. Commit and tag

```
git add pom.xml RELEASE_NOTES.md README.md RELEASING.md src/main/java src/test/java
 git commit -m "release: 3.5.0"
 git tag v3.5.0
git push origin main --tags
```

### 6. Deploy to Maven Central

From the repository root, run with the `release` profile. The plugin reads the
`central` credentials from your local `~/.m2/settings.xml`, prompts for the GPG
passphrase, and blocks until Central confirms publication.

```
mvn clean deploy -Prelease
```

GPG passphrase prompt appears interactively. To suppress it (CI / scripted), set:

```
set MAVEN_GPG_PASSPHRASE=<passphrase>
```

`maven-gpg-plugin` is configured with `--pinentry-mode loopback` so it reads from that env var without a TTY.

### 7. Verify on Central

After the command exits, confirm at:
```
https://central.sonatype.com/artifact/io.github.jabhijeet/java-pojo-to-parquet-schema
```

Also check the deployment state at:
```
https://central.sonatype.com/publishing/deployments
```

Do not rerun `deploy` after a bundle has uploaded until its deployment state is
known. A local Maven failure while polling Central does not necessarily mean the
uploaded deployment failed, and released coordinates cannot be uploaded again.

---

## What the `release` profile does

```
clean deploy -Prelease
        │
        ├── maven-source-plugin   → *-sources.jar
        ├── maven-javadoc-plugin  → *-javadoc.jar
        ├── maven-gpg-plugin      → .asc signatures for all artifacts
        └── central-publishing-maven-plugin
                publishingServerId = central   (reads ~/.m2/settings.xml)
                autoPublish        = true      (skips manual "Publish" click)
                waitUntil          = published (blocks until Central syncs)
```

---

## Snapshot builds

For local testing before a release, use a `-SNAPSHOT` version and run:

```
mvn clean install
```

This publishes to `~/.m2` only. Snapshot deployment to Central (`deploy` without `-Prelease`) goes to `https://central.sonatype.com/repository/maven-snapshots/`.

---

## Rollback / yanking

Maven Central does not support deletion of released artifacts. If a release is broken:

1. Release a patch version immediately with the fix.
2. Add a **Known limitations** note in `RELEASE_NOTES.md` for the broken version.
