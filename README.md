# GameSpace

chaldeaprjkt's GameSpace is an alternative to the Google's proprietary implementation of the Game Dashboard with a goals of providing basic user-interface for the [Android Game Mode API](https://developer.android.com/games/gamemode/gamemode-api).

Gamespace requires several patches to be applied on the AOSP's SystemUI and Settings :
- [frameworks/base](https://github.com/chaldeaprjkt/chaldea_frameworks_base/commits/13/gamespace)
- [packages/apps/Settings](https://github.com/chaldeaprjkt/chaldea_packages_apps_Settings/commits/13/gamespace)

## LineageOS 24.0 fork notes

This fork adds per-game touch boost and bypass/thermal session hooks on top of
crDroid's GameSpace. The matching framework hooks live right here, so the fork
is self-sufficient (no separate patches repo needed):

- `patches/frameworks/base/*.patch` are applied to `frameworks/base` by
  `vendorsetup.sh` (reverse-check skip + `git am`) whenever `build/envsetup.sh`
  is sourced; check the output for `FAILED to apply`.
- envsetup.sh only sources `vendorsetup.sh` found under `device/`, `vendor/`
  or `product/`, so the ROM manifest copies this script there at every
  `repo sync`, inside this project's `<project>` entry:

  ```xml
  <copyfile src="vendorsetup.sh" dest="vendor/gamespace-hooks/vendorsetup.sh" />
  ```

- The applied patches become commits on frameworks/base's detached HEAD, which
  the next `repo sync` discards: re-source `envsetup.sh` after syncing.

## License

This work is licensed under [Apache 2.0 License](LICENSE.md).
