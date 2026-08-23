# Pirate Ship Naval Combat Implementation

## Goal

NPC-controlled pirate ships use a deliberately simple age-of-sail combat loop. A ship patrols in a bounded circle, retains the closest valid hostile target, chases it, turns an effective broadside toward it, stops to let its cannons fire independently, changes broadside when battle damage makes the selected side too weak, and flees when both broadsides fall below half strength.

This implementation does not include boarding, wind tactics, formations, morale, obstacle avoidance, coastline navigation, or forced synchronized volleys.

## State flow

```text
PATROL -> CHASE -> ALIGN_BROADSIDE -> HOLD_BROADSIDE
             ^              |                 |
             +---- target leaves firing range-+

ALIGN/HOLD -> opposite ALIGN when selected broadside falls below 50%
Any active combat state -> FLEEING when both broadsides fall below 50%
```

Combat state is keyed by dimension and Valkyrien Skies ship ID. It is not stored in the Motion Invoking Block, Eureka helm, or a crew entity.

`FLEEING` is terminal for the current loaded combat state. A fleeing ship does not resume combat or patrol unless its controller state is recreated, such as after a server/world reload.

## Target selection and retention

Every `naval-combat-target-update-ticks`:

1. Retain the current target while it exists, remains within pursuit range, and is not on the same scoreboard team.
2. If the target becomes invalid, traverse the loaded Valkyrien Skies ships.
3. Exclude the current ship.
4. Exclude ships on the same non-null scoreboard team.
5. Choose the closest remaining ship by squared world-space distance.

A closer enemy does not replace a still-valid retained target.

If either ship has no scoreboard team, the current implementation treats the pair as hostile. There is no separate neutral-faction rule in the combat controller.

`pursuit-distance` is compared directly with squared distance, preserving the existing behavior. A configured value of `10000` therefore represents an effective distance of approximately 100 blocks.

## Cannon discovery and caching

On the first cannon refresh for a loaded ship, traverse its blocks and cache every cannon activator position. Each activator is classified relative to the ship-forward direction supplied by the Motion Invoking Block controller:

- `FRONT`: activator faces the bow direction.
- `BACK`: activator faces opposite the bow.
- `LEFT`: activator faces counterclockwise from the bow.
- `RIGHT`: the remaining horizontal direction.

The classification is performed in ship/block coordinates and remains stable while the ship rotates in world space.

Subsequent target refreshes inspect only cached cannon positions. Destroyed activators are removed from the cache.

## Operational cannon definition

A cannon is operational when all of the following are true:

- Its cannon activator block exists.
- The adjacent block in the activator's facing direction is the dispenser cannon block.
- The activator has `DISARMED == false`.

The controller does not scan for Cannoneer entities or compare crew station coordinates. The activator's armed state is authoritative, avoiding entity searches and delayed crew-spawn races.

Reload state, ammunition, raycast visibility, and firing cooldown do not affect the strategic operational count. Each cannon continues to enforce those conditions itself.

## Original broadside strength

The controller records the original port and starboard cannon totals once per loaded combat state.

When `pirates_ship_blueprints.dat` contains a repair blueprint for the VS ship ID:

1. Load that cached blueprint and rotation.
2. Count blueprint cannon activators that have their matching dispenser cannon.
3. Classify them as port or starboard relative to ship forward.

This preserves the undamaged broadside totals across save/rejoin for ships with cached blueprints.

If no cached blueprint is available, the first operational cannon scan becomes the runtime baseline. The controller does not perform an expensive blueprint similarity search merely to initialize combat.

## Broadside selection and damage response

When both port and starboard have operational cannons, choose the side with the larger current count. A tie selects port (`LEFT`). Front or back is selected only when neither broadside has an operational cannon.

A broadside is below half strength when:

```text
currentOperational * 2 < originalOperational
```

Exactly 50% remains combat-capable.

During combat:

- If the selected broadside falls below 50% and the opposite broadside remains at least 50%, switch to the opposite side and enter `ALIGN_BROADSIDE` again.
- If both port and starboard are below 50%, enter `FLEEING`.
- A side whose original count is zero is not considered damaged below half.

The strength check begins only after the existing startup grace period and at least three broadside scans.

## Chase behavior

When the target is outside cannon range:

- Clear the selected firing side.
- Point the bow toward the center of the target's world-space AABB.
- Eureka control applies full forward input (`1.0`). Valkyrien Sails control leaves forward input untouched and uses wind propulsion.
- Use proportional signed-angle steering with a configurable deadzone and maximum rudder impulse.
- Recalculate steering every `naval-combat-steering-update-ticks`.

## Broadside firing solution

When the target enters range, select a firing side and build the solution from the actual cached cannons on that side:

1. Transform each cannon's center from ship coordinates into world coordinates.
2. Transform a point ahead of each cannon to derive its real world-space firing direction.
3. Average the operational cannon positions to obtain the effective center of the row.
4. Average their firing vectors to obtain the effective broadside aim direction.
5. Aim that broadside at the center of the target ship's world-space AABB.

This avoids inferring the firing angle from hull dimensions, helm direction, or the target blueprint.

While angular error exceeds `naval-combat-broadside-tolerance-degrees`, the ship enters `ALIGN_BROADSIDE`. Eureka control rotates in place with zero forward input. Valkyrien Sails control adjusts only the Sails helm wheel while wind may continue moving or drifting the ship.

Once aligned, it enters `HOLD_BROADSIDE`. Eureka control sets both forward and turning input to zero. Valkyrien Sails control returns the helm wheel to center without modifying propulsion, so wind may continue moving or drifting the ship.

The combat controller never commands cannons to fire. Each cannon continues using its existing autonomous reload, targeting, raycast, faction, and `canFire` behavior.

## Valkyrien Sails steering

When the Motion Invoking Block has Sails compatibility (`COMPAT == 1`), the AI never writes `forwardImpulse`. Valkyrien Sails wind and sail physics remain solely responsible for propulsion.

The Sails helm reads `SeatedControllingPlayer.leftImpulse` as an instruction to rotate its persistent wheel, not as a direct rudder amount. The AI therefore:

1. Converts its desired rudder amount into a target wheel angle.
2. Reads the actual `BaseHelmBlockEntity` wheel angle.
3. Pulses left or right until the target angle is reached.
4. Targets the centered wheel angle while holding a broadside.
5. Updates Sails steering every server tick so the wheel does not run to full lock or oscillate around a coarse target.

Eureka ships retain the existing direct forward-and-rudder input behavior.
## Range hysteresis

The normal engagement distance is `cannon-can-fire-range`. Once aligning or holding a broadside, the allowed distance becomes:

```text
cannon-can-fire-range + naval-combat-range-hysteresis
```

This prevents rapid chase/hold switching near the range boundary. Broadside range is measured from the effective cannon-row center to the target direction when a firing solution exists; otherwise ship-center chase distance is used.

## Fleeing behavior

When both broadsides are below half strength, or when no operational cannon remains anywhere on the ship:

1. Calculate the direction directly away from the current enemy.
2. Randomize it within plus or minus 60 degrees while keeping it in the away-facing hemisphere.
3. Store that heading once.
4. Eureka control applies full forward input (`1.0`) indefinitely. Valkyrien Sails control changes only the helm wheel and leaves propulsion to the wind.

Fleeing does not increase maximum speed, acceleration, sail power, or physics force. Eureka uses the same full forward input as chasing; Sails propulsion remains wind-driven.

The flee heading is not continuously recalculated, so the ship proceeds in a straight escape direction instead of oscillating around the enemy.

## Circular idle patrol

When no valid target exists, the ship uses a bounded holding circle instead of random destinations:

1. Establish a circle center from the current position, heading, and configured patrol radius.
2. Randomly choose clockwise or counterclockwise travel.
3. Steer along the circle tangent. Eureka control uses 65% forward input; Valkyrien Sails control changes only the helm wheel.
4. Apply a limited radial correction when physics pushes the ship inside or outside the desired radius.
5. Create a new patrol circle after combat ends and the ship returns to patrol.

This prevents unlimited random wandering. It does not detect coastlines or obstacles, so a circle established near land can still intersect terrain.

## Configuration

```properties
pursuit-distance=10000
cannon-can-fire-range=80

naval-combat-target-update-ticks=40
naval-combat-steering-update-ticks=5
naval-combat-range-hysteresis=8
naval-combat-broadside-tolerance-degrees=10
naval-combat-steering-deadzone-degrees=8
naval-combat-max-rudder-impulse=0.7
naval-combat-patrol-radius=200
naval-combat-patrol-arrival-distance=15
```

`naval-combat-patrol-arrival-distance` remains in configuration for compatibility but is not used by the continuous circular patrol implementation.

## Persistence

Persisted ship-level data:

- Repair blueprint ID keyed by dimension and Valkyrien Skies ship ID.
- Blueprint rotation.
- Blueprint/controller anchor.

The combat controller uses this data to recover original broadside totals when available.

Transient combat data is not persisted:

- Current target.
- State (`PATROL`, `CHASE`, alignment, hold, or fleeing).
- Selected broadside.
- Flee heading.
- Patrol center and direction.
- Cached cannon positions and current operational counts.

Transient state is recreated after loading the world.

## Performance characteristics

- Combat decisions are server-authoritative.
- Cannon discovery traverses ship blocks only when the cache is empty.
- Normal cannon recounting reads only cached activator and dispenser block states.
- No entity-AABB search is used for operational cannon counting.
- Original blueprint cannon totals are calculated once per loaded controller state.
- Steering is throttled to its configured interval.
- Target and cannon refreshes are throttled separately.

Known scaling limitations:

- Target recovery and closest-target selection iterate the loaded VS ship collection.
- Deleted ships are not currently removed from the static combat-state map.
- A genuinely cannonless ship has an empty cache and may repeat its block traversal on later refreshes.

These limitations are acceptable for ordinary fleets but should be addressed before targeting hundreds of simultaneously active ships.

## Acceptance criteria

- A ship without a hostile target follows a bounded circular patrol.
- A ship retains its valid target instead of switching whenever another enemy becomes closer.
- Outside firing range, the ship pursues with its bow; Eureka uses full forward input while Sails remains wind-driven.
- Operational cannon counts come from armed activator state, not entity scanning.
- Inside firing range, the strongest broadside turns until its real cannon-row aim faces the enemy.
- An aligned ship stops movement and rotation while its target remains in range.
- Cannons fire autonomously; the ship controller only navigates.
- A weakened selected broadside changes to the opposite healthy broadside.
- Exactly half of the original broadside remains combat-capable.
- The ship flees only when both original broadsides fall below half, or no operational cannon remains anywhere.
- Fleeing applies normal full-forward speed along one persistent escape heading.
- Same-team ships are not selected as enemies.