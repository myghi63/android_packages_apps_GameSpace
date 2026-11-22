# Sourced by build/envsetup.sh. envsetup only finds vendorsetup.sh under
# device/, vendor/ or product/, so the ROM manifest copies this file there at
# every repo sync (<copyfile src="vendorsetup.sh" dest="vendor/gamespace-hooks/vendorsetup.sh"/>).
# Patches become commits on the project's detached HEAD, which the next repo
# sync discards, so re-source envsetup.sh after syncing. Check the output for
# "FAILED to apply".
__gamespace_apply_patches() {
    local top root patch rel project
    top="$(gettop)"
    root="$top/packages/apps/GameSpace/patches"
    [ -d "$root" ] || return 0
    while IFS= read -r patch; do
        rel="${patch#"$root"/}"
        project="${rel%/*}"
        git -C "$top/$project" apply --reverse --check "$patch" >/dev/null 2>&1 && continue
        if git -C "$top/$project" -c user.name=gamespace -c user.email=gamespace@localhost \
                am -q --no-gpg-sign "$patch" >/dev/null 2>&1; then
            echo "packages/apps/GameSpace: applied $rel"
        else
            git -C "$top/$project" am --abort >/dev/null 2>&1
            echo "packages/apps/GameSpace: FAILED to apply $rel" >&2
        fi
    done < <(find "$root" -type f -name '*.patch' | sort)
}
__gamespace_apply_patches
unset -f __gamespace_apply_patches
