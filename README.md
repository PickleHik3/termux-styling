# Termux:Styling

[![Build status](https://github.com/termux/termux-styling/workflows/Build/badge.svg)](https://github.com/termux/termux-styling/actions)
[![Join the chat at https://gitter.im/termux/termux](https://badges.gitter.im/termux/termux.svg)](https://gitter.im/termux/termux)

A [Termux](https://termux.org) add-on app to customize the terminal font and
color theme.

When developing (or packaging), note that this app needs to be signed with the
same key as the main Termux app in order to have the permission to modify the
required font or color files.

## Targeting a launcher edition

This fork styles the Termux Launcher, which ships several editions with different
application ids: `com.termux` (main release), `io.vaj.tl` (VAJ),
`com.termux.launcher` (standalone) and `com.termux.launcher.dev` (dev builds).

The target edition is pinned at build time and defaults to `com.termux`:

```bash
./gradlew assembleDebug                                  # targets com.termux
./gradlew assembleDebug -PtermuxLauncherPackageName=io.vaj.tl
```

The property (also settable via the
`TERMUX_STYLING_APP_BUILD__LAUNCHER_PACKAGE_NAME` environment variable, or by
editing `termuxLauncherPackageName` in `gradle.properties`) feeds both the
`BuildConfig` target package and the `android:sharedUserId` of the manifest,
because writing into the launcher's private files directory requires sharing its
user id, and the launcher's shared user id is always its own application id.

Consequences:

* **One styling APK per launcher edition.** A build pinned to `com.termux`
  cannot style `io.vaj.tl` and vice versa. It detects this case and says so
  rather than failing on the file write.
* **Changing the pinned edition requires an uninstall.** Android rejects an
  update that changes `sharedUserId`, so the previously installed styling app
  must be uninstalled before installing a build pinned to a different edition.
* The styling app must still be signed with the same key as the launcher edition
  it targets.

## Installation

Termux:Styling application can be obtained from [F-Droid](https://f-droid.org/en/packages/com.termux.styling/).

Additionally we provide per-commit debug builds for those who want to try
out the latest features or test their pull request. This build can be obtained
from one of the workflow runs listed on [Github Actions](https://github.com/termux/termux-styling/actions/workflows/github_action_build.yml?query=branch%3Amaster+event%3Apush)
page.

Signature keys of all offered builds are different. Before you switch the
installation source, you will have to uninstall the Termux application and
all currently installed plugins. Check https://github.com/termux/termux-app#Installation for more info.

## How to use

1. When inside Termux, long press anywhere on the terminal.
2. Select `More...` in the resulting dialog.
3. Select `Style` in the next dialog.
4. Click either `CHOOSE COLOR` or `CHOOSE FONT` depending on what you want to customize.
