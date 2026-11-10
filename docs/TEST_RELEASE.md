# Test release publication

The public source repository builds signed releases only via the manual **Publish signed Test update** workflow on `main`. Pull requests run the separate debug build and never receive signing credentials.

## One-time setup

Create the `test-release` GitHub Actions environment. Configure required reviewers if desired. Add these Actions secrets to this repository or the environment (using the **existing** production release key, not a new key):

- `SIGNING_KEY_BASE64`: base64 encoding of the existing Android release JKS (one line).
- `SIGNING_STORE_PASSWORD`: existing keystore password.
- `SIGNING_KEY_ALIAS`: existing signing alias.
- `SIGNING_KEY_PASSWORD`: existing key password.
- `UPDATES_DEPLOY_KEY_BASE64`: base64 encoding of an SSH private key whose public half is installed as a **write-enabled deploy key** on `Probnik67/StudyNotesSFU-Updates`.

The original private `Probnik67/StudyNotesSFU` repository already has a working signing and publishing workflow. Transfer its secrets using GitHub's secret settings or migrate the release workflow securely; **never commit, print, or upload signing keys or private deploy keys**. GitHub does not reveal existing secret values, so this step requires the repository owner.

## Release process

1. Merge an approved feature PR into public `main` after debug CI passes.
2. Increment `versionCode` and `versionName` in `app/build.gradle.kts` (each new test release needs a strictly increasing code). For the homework update the candidate is **0.8.59 / 86** on PR #14.
3. In Actions, select **Publish signed Test update** > **Run workflow**, branch `main`.
4. The job validates signing secrets, builds and verifies the signed release APK, then pushes it and `test.json` to the existing update repository. It refuses a versionCode that is not newer than the current Test version.
5. In the Android app select the **Test** update channel and check for updates. The existing updater downloads the release APK. Keep the installed app and its local data; do not uninstall it.
6. Promote to Stable only after testing and separate approval. This workflow does **not** modify `stable.json`.

## Safety

Only the manually triggered workflow on `main` can publish. Never run a secret-bearing build on pull-request code. Use the **same** Android signing certificate as existing installed releases, otherwise Android will reject an in-place update. Secrets cannot be recovered from GitHub via API and must be configured by an authorized maintainer.

The source code, not any personal homework or notes, is published in this repository. Do not commit local `state.json` files or screenshots containing personal data.
