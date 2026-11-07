# DoggyTalentsNext Migration Plan: Minecraft 1.21 → 26.1.2

**Status**: Community port (PR #15) merged — not yet built or play-tested
**Date**: 2026-04-25 (status updated 2026-11-07)
**Target**: NeoForge 26.1.2.101+ for Minecraft 26.1.2

## Executive Summary

This document outlines the complete migration strategy for DoggyTalentsNext from Minecraft 1.21.1 to Minecraft 26.1.2. The migration addresses breaking API changes across 8 major systems.

### Current Migration Status

✅ **Phase 1 - Build System** (COMPLETED)
- ✅ Gradle upgraded to 9.1.0
- ✅ ModDevGradle 2.0.141 configured
- ✅ NeoForge 26.1.2.101 — `build.gradle` reads `neoforge_version` from `gradle.properties` (it was previously hardcoded to 26.1.2.70-beta)
- ✅ `neoforge.mods.toml` ranges: Minecraft `[26.1.2]`, NeoForge `[26.1.2.101,26.1.3)` — players on older NeoForge or MC 26.1/26.1.1 cannot load the mod
- ✅ Java 25 — `org.gradle.java.home` was removed, so `JAVA_HOME` (or `JDK_25_HOME`) must point at JDK 25
- ✅ Gradle daemon heap raised to `-Xmx8G`
- ⚠️ Always use `./gradlew`; the system `gradle` (8.4) cannot read Java 25 class files
- ✅ pack_format updated to 84 with min/max fields
- ✅ Block/item registration migrated to `DeferredRegister.createBlocks/createItems()`

✅ **Phase 3 - GUI Rendering** (COMPLETED)
- ✅ All RenderLayer subclasses: `render()` → `submit()`, typed on `DogRenderState`
- ✅ Entity renderers updated: `cameraOrientation()` → `camera.rotation()`, removed `getTextureLocation` overrides
- ✅ Model files: `copyFrom()` → `loadPose/storePose()`, removed illegal `renderToBuffer` overrides
- ✅ `DTNModelLoader`: `SimpleJsonResourceReloadListener` → `SimplePreparableReloadListener`
- ✅ `DTNAnimationCodec`: `Keyframe.target()` → `preTarget()`
- ✅ Screen widgets: `renderWidget` → `extractWidgetRenderState/extractContents`, `onPress()` → `onPress(InputWithModifiers)`
- ✅ Blit calls: old 7-arg → new 10-arg with `RenderPipelines.GUI_TEXTURED`
- ✅ `DogMouthItemRenderer`: `ItemInHandRenderer` → `ItemModelResolver/ItemStackRenderState`
- ✅ `FoodBowlScreen`: `imageHeight` final, `extractContents/extractLabels` signatures fixed
- ✅ `DoggyArmorMapping` caches armor textures per (item, equipment asset); cache cleared on resource reload from `DTNModelLoader.apply`

✅ **Phase 2 - Inventory** (MIGRATED via PR #15)
- ✅ `DTNItemStackHandler` (api) extends NeoForge `ItemStacksResourceHandler`; all five handlers now build on it (`DogArmorItemHandler` and its `DogArmorItemHandlerImpl`, `PackPuppyItemHandler`, `TreatBagItemHandler`, `DoggyToolsItemHandler`)
- ✅ Menus use `ResourceHandlerSlot` instead of `SlotItemHandler`
- Legacy stack-style methods (`getStackInSlot`, `setStackInSlot`, `insertItem`, `extractItem`) are kept as wrappers over transactions. Note `setStackInSlot` stores a **copy** (old `ItemStackHandler` stored the reference); Doggy Tools held-item logic relies on reference identity — reviewed, no dupe found, but watch for held-item flicker
- Save format unchanged (`Items`/`Size`)

⚠️ **Phase 4 - Networking** (PARTIAL)
- `PacketHandler`/`DTNNetworkHandler` compatibility wrapper still in place
- ✅ Every packet registered as `registerServerbound`/`registerClientbound`; wrong-direction packets disconnect the sender (`PayloadDirection`). Packet IDs/order unchanged
- ✅ `DogPacket.handle` ignores anything not received on the server
- ✅ `NetworkDecodeUtil` caps decoded collection sizes and name lengths
- ✅ Canine Tracker `StartLocating` now checks owner, dimension and visibility (previously any player could locate any dog by UUID)

✅ **Phase 6 - Attribute Modifiers** (ALREADY COMPLIANT — no changes needed)
- `LegacyDogAttributes` renames old `minecraft:generic.*` attribute IDs on load

✅ **Phase 7 - Codecs** (DONE) — only non-deprecated `ExtraCodecs` constants remain (`RGB_COLOR_CODEC`, `VECTOR3F`)

✅ **`@OnlyIn` cleanup** — no `@OnlyIn` annotations remain in `src/`

✅ **Gameplay fixes (26.1.2.25)** — dogs follow owner across dimensions (chunk ticket flags fixed), Creeper Sweeper level 5 hunts creepers in Aggressive mode. Not yet verified in game

⚠️ **Phase 5 - Data Components** (NOT DONE) — item data still uses `DataComponents.CUSTOM_DATA`; no `DataComponentType` registered

⚠️ **Phase 8 - Testing** (WRITTEN, NOT YET RUN BY OWNER)
- 59 JUnit tests in `src/test` — `./gradlew build` now runs them (`neoForge.unitTest` enabled); a failing test fails the build
- 76 GameTests in `src/main/java/doggytalents/gametest` — run with `./gradlew runGameTestServer`
- CI: `.github/workflows/build.yml` runs build + GameTests on push/PR (read-only permissions)

---

## Remaining Work

---

## Phase 2: Inventory System Migration (DONE — see Current Migration Status; plan kept for reference)

### 2.1 Replace ItemStackHandler with ResourceHandler

**Impact**: 5 core inventory files, ~500 lines of code

#### Files to Migrate:

1. `/src/api/java/doggytalents/api/inferface/DogArmorItemHandler.java`
2. `/src/main/java/doggytalents/common/inventory/DogArmorItemHandlerImpl.java`
3. `/src/main/java/doggytalents/common/inventory/PackPuppyItemHandler.java`
4. `/src/main/java/doggytalents/common/inventory/TreatBagItemHandler.java`
5. `/src/main/java/doggytalents/common/inventory/DoggyToolsItemHandler.java`

#### Migration Pattern:

**OLD CODE** (1.21):
```java
package doggytalents.api.inferface;

import net.neoforged.neoforge.items.ItemStackHandler;

public abstract class DogArmorItemHandler extends ItemStackHandler {

    public DogArmorItemHandler(AbstractDog dog) {
        super(4); // 4 armor slots
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        // Logic here
    }
}
```

**NEW CODE** (26.1.2):
```java
package doggytalents.api.inferface;

import net.neoforged.neoforge.items.item.ItemResource;
import net.neoforged.neoforge.items.wrapper.ResourceHandler;
import net.neoforged.neoforge.common.transaction.Transaction;

public abstract class DogArmorItemHandler implements ResourceHandler<ItemResource> {

    protected final List<ItemStack> stacks;
    protected final AbstractDog dog;

    public DogArmorItemHandler(AbstractDog dog) {
        this.dog = dog;
        this.stacks = NonNullList.withSize(4, ItemStack.EMPTY);
    }

    @Override
    public int size() {
        return 4;
    }

    @Override
    public long getCapacityAsLong(int slot) {
        return 64; // Stack size
    }

    @Override
    public ItemResource getResource(int slot) {
        ItemStack stack = stacks.get(slot);
        return stack.isEmpty() ? null : ItemResource.of(stack);
    }

    @Override
    public long getAmountAsLong(int slot) {
        return stacks.get(slot).getCount();
    }

    @Override
    public long extract(int slot, ItemResource resource, long amount, Transaction tx) {
        if (slot < 0 || slot >= size()) return 0;

        ItemStack existing = stacks.get(slot);
        if (existing.isEmpty() || !resource.matches(existing)) return 0;

        long extracted = Math.min(amount, existing.getCount());

        tx.addOuterCloseCallback(result -> {
            if (result.wasCommitted()) {
                existing.shrink((int) extracted);
                if (existing.isEmpty()) {
                    stacks.set(slot, ItemStack.EMPTY);
                }
                onContentsChanged(slot);
            }
        });

        return extracted;
    }

    @Override
    public long insert(int slot, ItemResource resource, long amount, Transaction tx) {
        if (slot < 0 || slot >= size()) return 0;
        if (!isItemValid(slot, resource.toStack())) return 0;

        ItemStack existing = stacks.get(slot);

        if (existing.isEmpty()) {
            long toInsert = Math.min(amount, getCapacityAsLong(slot));

            tx.addOuterCloseCallback(result -> {
                if (result.wasCommitted()) {
                    stacks.set(slot, resource.toStack((int) toInsert));
                    onContentsChanged(slot);
                }
            });

            return toInsert;
        } else if (resource.matches(existing)) {
            long toInsert = Math.min(amount, getCapacityAsLong(slot) - existing.getCount());

            tx.addOuterCloseCallback(result -> {
                if (result.wasCommitted()) {
                    existing.grow((int) toInsert);
                    onContentsChanged(slot);
                }
            });

            return toInsert;
        }

        return 0;
    }

    protected abstract boolean isItemValid(int slot, ItemStack stack);
    protected abstract void onContentsChanged(int slot);
}
```

#### Transactional Logic Updates

**Every inventory operation must use transactions**:

```java
// OLD
ItemStack extracted = dogInventory.extractItem(slot, 1, false);
if (!extracted.isEmpty()) {
    dog.heal(4.0F);
}

// NEW
try (Transaction tx = Transaction.openRoot()) {
    ItemResource resource = dogInventory.getResource(slot);
    if (resource != null) {
        long extracted = dogInventory.extract(slot, resource, 1, tx);
        if (extracted > 0) {
            dog.heal(4.0F);
            tx.commit();
        }
    }
}
```

#### Legacy Compatibility Wrappers

For code that cannot be immediately refactored:

```java
import net.neoforged.neoforge.items.wrapper.IItemHandler;

// Wrap ResourceHandler as legacy IItemHandler
IItemHandler legacyHandler = IItemHandler.of(resourceHandler);
```

**⚠️ WARNING**: Wrappers will be removed in future NeoForge versions. Migrate ASAP.

---

## Phase 3: GUI Rendering System (HIGH PRIORITY)

### 3.1 Screen Method Refactoring

**Impact**: 28 screen files

#### Files Requiring Changes:

**Main Screens**:
- `DogInventoriesScreen.java`
- `DogArmorScreen.java`
- `DogNewInfoScreen/DogNewInfoScreen.java`
- `PackPuppyScreen.java`
- `TreatBagScreen.java`
- `WhistleScreen.java`
- `CanineTrackerScreen.java`
- `ConductingBoneScreen.java`
- ... (20 more files)

#### Method Rename Mapping:

| Old Method (1.21)                          | New Method (26.1.2)                                  |
|--------------------------------------------|-----------------------------------------------------|
| `render(GuiGraphics, int, int, float)`     | `extractRenderState(GuiGraphicsExtractor, ...)`     |
| `renderBackground(...)`                    | `extractBackground(...)`                            |
| `renderBg(...)`                           | `extractBackground(...)`                            |
| `renderLabels(...)`                       | `extractLabels(...)`                                |

#### Migration Example:

**OLD CODE**:
```java
public class DogInventoriesScreen extends AbstractContainerScreen<DogInventoriesContainer> {

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, 8, 6, 4210752, false);
        graphics.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 96 + 2, 4210752, false);
    }
}
```

**NEW CODE**:
```java
public class DogInventoriesScreen extends AbstractContainerScreen<DogInventoriesContainer> {

    @Override
    protected void extractBackground(GuiGraphicsExtractor extractor, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        extractor.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        extractor.drawString(this.font, this.title, 8, 6, 4210752, false);
        extractor.drawString(this.font, this.playerInventoryTitle, 8, this.imageHeight - 96 + 2, 4210752, false);
    }
}
```

### 3.2 Entity Render State Pattern

For screens that render dog models (e.g., Talent GUI):

**Create Render State Record**:
```java
public record DogRenderState(
    ResourceLocation texture,
    DogPose pose,
    float ageInTicks,
    int talentLevel,
    List<AccessoryRenderData> accessories
) {}
```

**Implement State Extraction**:
```java
public class DogRenderer extends EntityRenderer<Dog> {

    @Override
    public DogRenderState createRenderState() {
        return new DogRenderState(null, DogPose.STANDING, 0, 0, List.of());
    }

    @Override
    public void extractRenderState(Dog dog, DogRenderState state, float partialTick) {
        state = new DogRenderState(
            dog.getTexture(),
            dog.getPose(),
            dog.tickCount + partialTick,
            dog.getTalentLevel(),
            dog.getAccessories()
        );
    }
}
```

---

## Phase 4: Networking System Refactor (MEDIUM-HIGH PRIORITY)

### 4.1 Packet System Migration

**Impact**: 73+ packet classes

#### Current Architecture (Compatibility Wrapper):

```
PacketHandler (registers 73+ packets)
    ↓
DTNNetworkHandler (compatibility layer)
    ↓
CustomPacketPayload + StreamCodec (26.1.2 API)
```

#### Target Architecture (Direct):

```
RegisterPayloadHandlersEvent
    ↓
PayloadRegistrar
    ↓
CustomPacketPayload records with StreamCodec
```

#### Migration Steps:

**Step 1**: Convert packet classes to records

**OLD**:
```java
public class DogModePacket extends DogPacket<DogModeData> {
    @Override
    public void encode(DogModeData data, FriendlyByteBuf buf) {
        super.encode(data, buf);
        buf.writeInt(data.mode.getIndex());
    }

    @Override
    public DogModeData decode(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int modeIndex = buf.readInt();
        return new DogModeData(entityId, DogMode.byIndex(modeIndex));
    }

    @Override
    public void handleDog(Dog dog, DogModeData data, Supplier<Context> ctx) {
        if (!dog.canInteract(ctx.get().getSender())) return;
        dog.setMode(data.mode);
    }
}
```

**NEW**:
```java
public record DogModePayload(int dogId, int modeIndex) implements CustomPacketPayload {

    public static final Type<DogModePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("doggytalents", "dog_mode"));

    public static final StreamCodec<ByteBuf, DogModePayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DogModePayload::dogId,
            ByteBufCodecs.VAR_INT, DogModePayload::modeIndex,
            DogModePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DogModePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow() == PacketFlow.SERVERBOUND) {
                ServerPlayer sender = (ServerPlayer) ctx.player();
                Entity entity = sender.level().getEntity(payload.dogId());
                if (entity instanceof Dog dog && dog.canInteract(sender)) {
                    dog.setMode(DogMode.byIndex(payload.modeIndex()));
                }
            }
        });
    }
}
```

**Step 2**: Register payloads in event

**Delete**: `PacketHandler.java`, `DTNNetworkHandler.java` (compatibility wrappers)

**Create**: `DoggyTalentsNetworking.java`

```java
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DoggyTalentsNetworking {

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(Constants.PROTOCOL_VERSION);

        // Server-bound (client → server)
        registrar.playToServer(
            DogModePayload.TYPE,
            DogModePayload.STREAM_CODEC,
            DogModePayload::handle
        );

        registrar.playToServer(
            DogObeyPayload.TYPE,
            DogObeyPayload.STREAM_CODEC,
            DogObeyPayload::handle
        );

        // Client-bound (server → client)
        registrar.playToClient(
            DogSyncDataPayload.TYPE,
            DogSyncDataPayload.STREAM_CODEC,
            DogSyncDataPayload::handle
        );

        // Bidirectional
        registrar.playBidirectional(
            DogTexturePayload.TYPE,
            DogTexturePayload.STREAM_CODEC,
            DogTexturePayload::handle
        );

        // ... register remaining 70+ payloads
    }
}
```

**Step 3**: Use RegistryFriendlyByteBuf for ItemStack packets

```java
public record DogEquipmentPayload(int dogId, ItemStack equipment) implements CustomPacketPayload {

    // Use RegistryFriendlyByteBuf for ItemStack codec
    public static final StreamCodec<RegistryFriendlyByteBuf, DogEquipmentPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            DogEquipmentPayload::dogId,
            ItemStack.STREAM_CODEC,  // Requires RegistryFriendlyByteBuf
            DogEquipmentPayload::equipment,
            DogEquipmentPayload::new
        );

    // ... rest of implementation
}
```

### 4.2 Packet Migration Checklist

73 packets to migrate. Create subtasks for each:

- [ ] `DogModePayload` (replaces DogModePacket)
- [ ] `DogObeyPayload` (replaces DogObeyPacket)
- [ ] `DogTalentPayload` (replaces DogTalentPacket)
- [ ] `DogNamePayload` (replaces DogNamePacket)
- [ ] `DogTexturePayload` (replaces DogTexturePacket)
- [ ] ... (68 more packets)

---

## Phase 5: Data Components Migration (MEDIUM PRIORITY)

### 5.1 Replace NBT with DataComponentType

**Impact**: All custom ItemStack data (whistles, accessories, collars, artifacts)

#### Create Data Components Registry

**File**: `/src/main/java/doggytalents/common/register/DoggyDataComponents.java`

```java
@EventBusSubscriber(modid = Constants.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class DoggyDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Constants.MOD_ID);

    // Whistle state
    public static final Supplier<DataComponentType<WhistleState>> WHISTLE_STATE =
        DATA_COMPONENTS.register("whistle_state", () ->
            DataComponentType.<WhistleState>builder()
                .persistent(WhistleState.CODEC)
                .networkSynchronized(WhistleState.STREAM_CODEC)
                .build()
        );

    // Dog collar color
    public static final Supplier<DataComponentType<Integer>> COLLAR_COLOR =
        DATA_COMPONENTS.register("collar_color", () ->
            DataComponentType.<Integer>builder()
                .persistent(Codec.INT)
                .networkSynchronized(ByteBufCodecs.VAR_INT)
                .build()
        );

    // Accessory data
    public static final Supplier<DataComponentType<AccessoryData>> ACCESSORY_DATA =
        DATA_COMPONENTS.register("accessory_data", () ->
            DataComponentType.<AccessoryData>builder()
                .persistent(AccessoryData.CODEC)
                .networkSynchronized(AccessoryData.STREAM_CODEC)
                .build()
        );

    // Artifact data
    public static final Supplier<DataComponentType<ArtifactData>> ARTIFACT_DATA =
        DATA_COMPONENTS.register("artifact_data", () ->
            DataComponentType.<ArtifactData>builder()
                .persistent(ArtifactData.CODEC)
                .networkSynchronized(ArtifactData.STREAM_CODEC)
                .build()
        );

    // Dog owner UUID (for items that store owner)
    public static final Supplier<DataComponentType<UUID>> DOG_OWNER =
        DATA_COMPONENTS.register("dog_owner", () ->
            DataComponentType.<UUID>builder()
                .persistent(Codec.UUID)
                .networkSynchronized(ByteBufCodecs.UUID)
                .build()
        );
}
```

#### Migration Pattern

**OLD (NBT)**:
```java
// Writing
ItemStack whistle = new ItemStack(Items.WHISTLE);
CompoundTag tag = whistle.getOrCreateTag();
tag.putInt("mode", 1);
tag.putString("name", "Home");

// Reading
if (whistle.hasTag() && whistle.getTag().contains("mode")) {
    int mode = whistle.getTag().getInt("mode");
}
```

**NEW (Data Components)**:
```java
// Writing
ItemStack whistle = new ItemStack(Items.WHISTLE);
whistle.set(DoggyDataComponents.WHISTLE_STATE.get(),
    new WhistleState(1, "Home"));

// Reading
WhistleState state = whistle.getOrDefault(
    DoggyDataComponents.WHISTLE_STATE.get(),
    WhistleState.DEFAULT
);
int mode = state.mode();
```

### 5.2 Define Data Records

```java
public record WhistleState(int mode, String name) {
    public static final WhistleState DEFAULT = new WhistleState(0, "");

    public static final Codec<WhistleState> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.INT.fieldOf("mode").forGetter(WhistleState::mode),
            Codec.STRING.fieldOf("name").forGetter(WhistleState::name)
        ).apply(instance, WhistleState::new)
    );

    public static final StreamCodec<ByteBuf, WhistleState> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, WhistleState::mode,
            ByteBufCodecs.STRING_UTF8, WhistleState::name,
            WhistleState::new
        );
}
```

---

## Phase 6: Attribute Modifiers (LOW PRIORITY - ALREADY COMPLIANT)

### 6.1 Status Check

✅ **GOOD NEWS**: The codebase already uses `ResourceLocation` for attribute modifiers!

**Verified in**: `/src/api/java/doggytalents/api/inferface/AbstractDog.java:46-67`

```java
AttributeModifier currentModifier = attributeInst.getModifier(modifierLoc);  // ✅ ResourceLocation
attributeInst.removeModifier(modifierLoc);  // ✅ ResourceLocation
```

**No changes needed** for this phase.

---

## Phase 7: Codec Updates (LOW PRIORITY)

### 7.1 Replace Deprecated ExtraCodecs

**Find**: All uses of `ExtraCodecs`
**Replace with**: Standard `Codec` methods

```java
// OLD
ExtraCodecs.strictOptionalField(codec, "fieldName")

// NEW
codec.optionalFieldOf("fieldName")  // Now strict by default in 26.1.2
```

**Files likely affected**: Talent configuration, recipe serializers

---

## Phase 8: Testing & Validation

### 8.1 Unit Tests

Create tests for:
- [ ] Inventory transactions (commit/abort scenarios)
- [ ] Packet serialization/deserialization
- [ ] Data component persistence
- [ ] Screen rendering (mock tests)

### 8.2 Integration Tests

- [ ] Dog inventory operations (add/remove armor)
- [ ] Multiplayer synchronization (spawn dog, sync to clients)
- [ ] Save/load world with dogs
- [ ] GUI interactions (all 28 screens)
- [ ] Talent system functionality

### 8.3 Performance Tests

- [ ] Inventory transaction overhead vs. old system
- [ ] Rendering performance (extraction pattern)
- [ ] Network packet size comparison

---

## Critical Files Checklist

### Inventory System (5 files)
- [x] `DogArmorItemHandler.java` (API)
- [x] `DogArmorItemHandlerImpl.java`
- [x] `PackPuppyItemHandler.java`
- [x] `TreatBagItemHandler.java`
- [x] `DoggyToolsItemHandler.java`

### Screen/GUI (28 files)
- [x] All screens migrated (commit c848bd73)

### Networking (73+ files)
- [ ] Delete `PacketHandler.java`
- [ ] Delete `DTNNetworkHandler.java`
- [ ] Create `DoggyTalentsNetworking.java`
- [ ] Convert 73+ packet classes to payload records

### Data Components
- [ ] Create `DoggyDataComponents.java`
- [ ] Migrate whistle item data
- [ ] Migrate accessory item data
- [ ] Migrate artifact item data

---

## Known Issues & Blockers

### 1. Not yet built or play-tested (ACTIVE)

PR #15 (from an outside contributor, `dominicbytes`) was merged after a read-only security review that found no malware or exploits. Nothing has been compiled or run since. First steps: `./gradlew build`, `./gradlew runGameTestServer`, then play-test in `./gradlew runClient`.

### 2. Behaviour and compatibility changes from PR #15

- **Removed content** (matches upstream DashieDev): dragon costume head/suit/wings, plague doctor mask, sussy sickle — copies in existing worlds disappear. French translation (`fr_fr.json`) removed; `UpstreamCleanupParityTest` asserts it stays absent. Dog models `arcanine`, `borzoi_long`, `otter` removed
- **Legacy save import**: `LegacyDogSavedData` imports `data/doggytalentsDogLocations.dat` and `data/doggytalentsDeadDogs.dat` once (originals kept). If an old file is unreadable it throws and **the world will not load**, instead of starting with empty dog data
- **Respawn data format**: owner UUID now an int array, names as JSON components (upstream format). Old worlds load fine; opening a world with this version and then downgrading loses respawn owner/name data
- **Dog swimming** starts on `isUnderWater` instead of `isInWater`

### 3. Code hygiene (LOW)

- **GameTests ship in the jar**: `DTNGameTestRegistry` lives in `src/main` and is registered unconditionally from `DoggyTalentsNext`. They are inert in production (`GameTestHooks.isGametestEnabled()` is false, so `RegisterGameTestsEvent` never fires), but should move to a separate source set
- **Hand-edited generated files**: 25 `src/generated/.../items/*.json` have hand-added `tints`; `DTItemModelProvider` is a stub, so a future real datagen run would overwrite them
- `DogAnimDebugScreen` shows the wrong label for the rotation-lock mode (cosmetic)
- A deeply nested dog model in a server-forced resource pack can crash the client on reload (`Codec.recursive` → `StackOverflowError`; pre-existing)
- `scripts/` holds the contributor's manual QA tooling (Node `.cjs` scripts, `world-upgrade.init.gradle`) and `build.gradle` reads `MINECRAFT_MCP_*` env vars for their tooling. Not needed for normal development

### 4. API Coverage Gaps

The following NeoForge 26.1.2 APIs are mentioned in the migration plan but not yet confirmed:
- `MutableQuad` usage for BakedQuad manipulation
- `SubmitNodeCollector` for render state submission

**Mitigation**: Consult NeoForge docs when stable release is available.

---

## Timeline Estimate


| Phase | Complexity | Status |
|-------|------------|--------|
| Phase 1: Build System | Low | ✅ DONE |
| Phase 2: Inventory | High | ✅ DONE (PR #15) — untested in game |
| Phase 3: GUI Rendering | Medium-High | ✅ DONE |
| Phase 4: Networking | High | Wrapper retained; direction checks + size caps added — record conversion pending |
| Phase 5: Data Components | Medium | Pending (still `CUSTOM_DATA`) |
| Phase 6: Attribute Modifiers | None | ✅ DONE |
| Phase 7: Codec Updates | Low | ✅ DONE |
| Phase 8: Testing | Ongoing | 59 unit tests + 76 GameTests written — not yet run |

---

## Next Steps

1. **Build and test the merged PR #15** — `./gradlew build`, `./gradlew runGameTestServer`, then play-test dimension travel, Creeper Sweeper, dog inventories/armor, and loading an existing world.
2. **Phase 4 Networking** — remove `PacketHandler`/`DTNNetworkHandler` wrappers; convert packets to `CustomPacketPayload` records.
3. **Phase 5 Data Components** — replace `CUSTOM_DATA` usage in whistle, accessory, and artifact items with typed components.
4. **Move GameTests out of `src/main`** into a dedicated source set.


---

## Support Resources

- **NeoForge Documentation**: https://docs.neoforged.net/
- **NeoForge Discord**: https://discord.neoforged.net/
- **Migration Guide**: https://neoforged.net/news/26.1release/
- **Transfer Rework Guide**: https://neoforged.net/news/21.9-transfer-rework/

---

**Document Version**: 1.3
**Last Updated**: 2026-11-07
**Status**: PR #15 merged on NeoForge 26.1.2.101; not yet built or play-tested
