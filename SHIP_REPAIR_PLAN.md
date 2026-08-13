# Boatswain Ship Repair System

## Goal

Add a friendly pirate job named **boatswain** that restores an assembled Valkyrien Skies ship directly from its original structure blueprint. The first implementation deliberately avoids damage tracking, reports, progress queues, and same-ship player checks.

## Boatswain job

- Recruit a boatswain contract from a jobless villager for 10 emeralds by default.
- Spawn the boatswain by using the contract on a smithing table.
- Equip a pistol in the main hand and an iron axe in the offhand.
- Configure recruitment with `boatswain-recruit-cost=minecraft:emerald,10`.

## Automatic blueprint quote and repair UI

The player no longer chooses a blueprint.

1. Right-click a boatswain.
2. On the logical server, resolve the Valkyrien ship supporting the boatswain by its stable Valkyrien Skies ship ID.
3. Try to load the blueprint ID, rotation, and ship-local anchor from Pirates-owned world save data keyed by dimension and Valkyrien Skies ship ID.
4. If the saved blueprint is valid, traverse only that blueprint to calculate eligible differing blocks and report `Exist blueprint found` in the UI.
5. If no valid saved blueprint exists, load every bundled `pirates_eureka:ship/*.nbt`, test every blueprint and rotation, select the highest-similarity candidate, persist its ID and rotation, and report `Traverse to find blueprint` in the UI.
6. Send the matched blueprint name, repairable block count, configured gold price, and cache-status flag to the client.
8. Display a custom quote screen with a **Pay Gold and Repair** button. Rendering or hovering does not rescan the ship.
9. When clicked, the server verifies the boatswain is still nearby and recalculates repairable differences using the cached blueprint only, so stale client data is never authoritative.
10. Count gold ingots across the player's entire inventory; the player does not need to hold gold.
11. If the inventory contains less than the configured price, report `Insufficient gold` and do nothing.
12. Otherwise, immediately repair all eligible differing positions and remove the price across inventory stacks.

A ship with no eligible differences disables the repair button and is not charged.

The first version performs each quote and repair traversal synchronously. If very large blueprints cause a noticeable server pause, move these explicitly triggered traversals to a lazy/budgeted task later. Do not scan continuously while the UI is open.
## Repair exclusions

Never traverse or restore:

- Blueprint air.
- Structure void.
- Container-like blocks.
- Any block state with a block entity in the first version.
- The Motion Invoking Block controller.
- Blocks listed in `ship-repair-excluded-blocks`.

Door and trapdoor open/closed state changes are treated as interaction state rather than damage. A missing or replaced door or trapdoor is still repaired.

The configurable exclusion value is a comma-separated list of block IDs:

```properties
ship-repair-excluded-blocks=example:unsafe_block,example:custom_machine
```

The implementation intentionally restores every other differing blueprint position. This means blocks manually removed, destroyed by creepers, destroyed by cannons, or replaced with a different ordinary block are all restored to the original blueprint state.

## Pricing

One repair costs 64 gold ingots by default:

```properties
ship-repair-gold-cost=64
```

The full cost may be distributed across any inventory slots. A pristine ship is not charged.

## Coordinate rules

- Compare and place blocks in shipyard coordinates.
- Use controller-relative blueprint offsets.
- Do not transform repair targets into visible world coordinates.
- Do not rotate offsets using the ship's current physics rotation.
- Let Valkyrien Skies display updated shipyard blocks through the ship transform.

## Persistence

Persist in `world/data/pirates_ship_blueprints.dat`, keyed by dimension and Valkyrien Skies ship ID:

- Blueprint resource ID.
- Blueprint rotation.
- Associated Valkyrien Skies ship ID.
- Shipyard-coordinate blueprint anchor.

The Motion Invoking Block does not own this association. Destroying it does not discard an already matched blueprint or prevent repair through the saved anchor. Legacy controller NBT is read once and migrated for existing worlds.

There is no missing-block list, active repair queue, progress state, or cannon-impact ledger to save.

## Current technical risks

- Verify Valkyrien Skies recalculates rendering, collision, mass, inertia, and expanded bounds after bulk `setBlockState` calls.
- Very large synchronous blueprint traversals may need a future lazy tick budget.
- Unloaded shipyard chunks are skipped during the current traversal.
- First-time matching derives the blueprint and shipyard anchor from the assembled ship's remaining blocks. The Motion Invoking Block may already be missing.