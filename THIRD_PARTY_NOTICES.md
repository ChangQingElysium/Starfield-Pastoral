# Third-party notices

## Immersive Portals Mod for NeoForge

The town doorway renderer adapts the stencil/depth render sequence, nested world context, front clipping,
Sodium view-context handling, pre-camera traversal, and the resolved-color Iris compatibility rendering approach
from Immersive Portals Mod for NeoForge.

- Copyright 2020 qouteall and contributors
- Source: https://github.com/iPortalTeam/ImmersivePortalsModForNeo
- Source revision studied: `aede93a4865fe4aab5dd2781fb38ab3e5cecd63b`
- License: Apache License 2.0; see [LICENSES/Immersive-Portals-Apache-2.0.txt](LICENSES/Immersive-Portals-Apache-2.0.txt)

The adapted implementation was changed into one fixed, same-level town doorway. It removes Immersive Portals'
portal entity, registries, multi-dimension loader, configuration API, and all compile/runtime dependencies on that mod.
