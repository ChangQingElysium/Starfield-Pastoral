# Apothic Attributes 1.20.1 sweep compatibility

Issue: [#16](https://github.com/ChangQingElysium/Starfield-Pastoral/issues/16).

`PlayerSweepAttackMixin` wraps the sweep `LivingEntity.hurt` invocation in
`Player.attack` with MixinExtras `@WrapOperation`. For ordinary weapons it
forwards through `Operation.call`, preserving Apothic Attributes' existing
auxiliary-damage kill result. Stardew weapons retain their existing native
sweep suppression. Primary-hit authority and knockback ownership are unchanged.

The previous exclusive `@Redirect` targeted the same invocation as Apothic's
`apoth_handleKilledByAuxDmg`; the second redirect failed its required injection
check during startup. Raising priority or making either injection optional would
discard one mod's behavior, so both handlers must remain in the call chain.

Run the focused check after compilation:

```sh
./gradlew classes printCompileClasspath
bash scripts/port/test_apothic_attributes_1201.sh
```

The script downloads the official Apothic Attributes 1.3.7 release from Modrinth
into its test output directory and verifies its pinned SHA-512. It does not
install a mod into a game profile or build an installation JAR. Fresh production
bytecode, Forge's generated SRG-to-official mapping, and the published third-party
`PlayerMixin` are passed through the real Mixin/MixinExtras transformer.

Three configurations are checked: StardewCraft alone, Apothic first, and
StardewCraft first. The transformed `Player.attack` call graph must retain the
sweep wrapper, primary-hit authority, knockback ownership, and (when present)
Apothic's redirect. The actual production and published handler bodies are then
executed on CPU receiver fixtures for vanilla/Stardew weapon identity, accepted
or rejected damage, and the auxiliary-kill flag. Every forwarded hit must occur
exactly once with its original amount. Receiver fixtures replace Minecraft's
world/item initialization; they do not test weapon registration or full gameplay.

`JAVA17_HOME` or `JAVA_HOME` selects the JDK. `PORT_APOTHIC_OUTPUT` selects a
task-specific output directory, and `PORT_MAIN_CLASSES` follows the existing port
harness convention. The regression was verified under Java 17 and Java 25.
The `expect-conflict` diagnostic mode can reproduce the original failure when
run against the pre-fix production class; it intentionally fails on fixed code.

This gate verifies the reported injection collision and preserved handler
semantics. It does not claim full client startup, rendering, or every
Apothic/Placebo mechanic has been play-tested. The current compiled Forge target
is the repository's 47.4.10; the report used 47.4.26.
