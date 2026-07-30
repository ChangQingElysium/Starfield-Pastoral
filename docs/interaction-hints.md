# Crosshair interaction hints

The crosshair-side hint is resolved on the server. It describes the action
available on the block or entity currently under the player's crosshair;
it does not perform the interaction.

## Built-in semantics

| Type | Typical use |
| --- | --- |
| `GRAB` | Open, operate, pet, sit, travel, or collect a special object |
| `GIFT` | Give a gift, quest item, or festival gift to an NPC |
| `TALK` | Talk to an NPC or invite another player |
| `LOOK` | Read a map-authored text point or lost book |
| `HARVEST` | Harvest a mature crop, forage, fruit, berry, produce, or machine output |

`TALK` and `LOOK` support a `done` state. Pending hints use the supplied
floating icon; completed hints use the supplied `_done` texture, stay still,
and render at reduced opacity. The other semantic types are static.

## Data-pack tags

A data pack can assign a static semantic to block or entity types:

- `stardewcraft:interaction_hints/grab`
- `stardewcraft:interaction_hints/gift`
- `stardewcraft:interaction_hints/talk`
- `stardewcraft:interaction_hints/look`
- `stardewcraft:interaction_hints/harvest`
- `stardewcraft:interaction_hints/none`

Use the tag under `tags/block` for blocks and `tags/entity_type` for entity
types. `none` suppresses built-in resolution. Static tags do not manufacture
per-player completion state; use the API for a dynamic `TALK` or `LOOK`
result.

## Add-on API

Register a server-side, mutation-free provider during add-on setup:

The extension surface consists of `StardewInteractionHints`,
`StardewInteractionHintProvider`, `StardewInteractionHintContext`,
`StardewInteractionHintDecision`, `StardewInteractionHint`, and
`StardewInteractionHintType`.

```java
StardewInteractionHints.register(
    ResourceLocation.fromNamespaceAndPath("example", "notice_board"),
    100,
    context -> {
        if (!isNoticeBoard(context)) {
            return StardewInteractionHintDecision.pass();
        }
        boolean read = hasRead(context.player());
        return StardewInteractionHintDecision.show(
            new StardewInteractionHint(
                StardewInteractionHintType.LOOK,
                read,
                ResourceLocation.fromNamespaceAndPath(
                    "example", "notice_board")));
    });
```

Providers run by descending priority and then by identifier. A provider may
return:

- `pass()` to allow later providers and built-in resolution;
- `hide()` to deliberately suppress the hint;
- `show(hint)` to supply the semantic, completion state, and stable identity.

Provider code must only inspect state. Opening screens, consuming items,
advancing quests, or changing friendship belongs in the real interaction
handler.

## Resolution order

1. Add-on providers.
2. The `none` data-pack tag.
3. Precise built-in probes for stateful project systems.
4. Static semantic data-pack tags.
5. A menu hint when the current block state exposes a menu provider.

This order lets an add-on describe dynamic behavior precisely while still
allowing data packs to cover simple content without Java code. The resolver
does not infer an action merely because a block class declares a right-click
method: forwarding and conditional handlers may still return `PASS`.
