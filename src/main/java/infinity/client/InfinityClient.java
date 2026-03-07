package infinity.client;

import infinity.client.gui.GuiInfinityClick;
import infinity.client.module.Category;
import infinity.client.module.Module;
import infinity.client.setting.ModeSetting;
import infinity.client.setting.SliderSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemElytra;
import net.minecraft.item.ItemEndCrystal;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketConfirmTeleport;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.network.play.server.SPacketSpawnObject;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

public class InfinityClient {
    private static final InfinityClient INSTANCE = new InfinityClient();
    public static InfinityClient get() { return INSTANCE; }

    private final List<Module> modules = new ArrayList<>();
    private final Set<BlockPos> oldChunks = new HashSet<>();
    private GuiInfinityClick clickGui;

    private InfinityClient() {
        modules.add(new KillAura()); modules.add(new CrystalMacro()); modules.add(new CrystalAura()); modules.add(new Surround());
        modules.add(new AutoTotem()); modules.add(new FastMine()); modules.add(new FastPlace()); modules.add(new PacketFly());
        modules.add(new ElytraFly()); modules.add(new Velocity()); modules.add(new PlayerESP()); modules.add(new StorageESP());
        modules.add(new Blink()); modules.add(new AirWalk()); modules.add(new FastBow()); modules.add(new ChunkTrails());
        modules.add(new NoDamage()); modules.add(new AirPlace());
    }

    public List<Module> getModules() { return modules; }

    public void onTick() {
        if (clickGui == null) clickGui = new GuiInfinityClick(this);
        if (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) && !(mc().currentScreen instanceof GuiChat) && !(mc().currentScreen instanceof GuiInfinityClick)) {
            mc().displayGuiScreen(clickGui);
        }
        for (Module m : modules) if (m.isEnabled()) m.onTick();
        if (mc().world != null) {
            int cx = MathHelper.floor(mc().player.posX) >> 4;
            int cz = MathHelper.floor(mc().player.posZ) >> 4;
            oldChunks.add(new BlockPos(cx, 0, cz));
        }
    }

    public void onRender(float partialTicks) {
        for (Module m : modules) if (m.isEnabled()) m.onRender3D(partialTicks);
    }

    public boolean onPacketSend(Packet<?> packet) {
        for (Module m : modules) if (m.isEnabled() && m.onPacketSend(packet)) return true;
        return false;
    }

    public boolean onPacketReceive(Packet<?> packet) {
        for (Module m : modules) if (m.isEnabled() && m.onPacketReceive(packet)) return true;
        if (packet instanceof SPacketSpawnObject) {
            SPacketSpawnObject s = (SPacketSpawnObject) packet;
            if (s.getType() == 51) oldChunks.add(new BlockPos((int)s.getX() >> 4, 0, (int)s.getZ() >> 4));
        }
        return false;
    }

    public void onKeyEvent(int key) {
        if (key == Keyboard.KEY_RSHIFT && !(mc().currentScreen instanceof GuiChat)) {
            if (clickGui == null) clickGui = new GuiInfinityClick(this);
            mc().displayGuiScreen(clickGui);
        }
    }

    public List<Module> byCategory(Category c) {
        List<Module> ret = new ArrayList<>();
        for (Module m : modules) if (m.getCategory() == c) ret.add(m);
        ret.sort(Comparator.comparing(Module::getName));
        return ret;
    }

    public Set<BlockPos> getOldChunks() { return oldChunks; }

    private net.minecraft.client.Minecraft mc() { return net.minecraft.client.Minecraft.getMinecraft(); }

    class KillAura extends Module {
        SliderSetting min = addSetting(new SliderSetting("Min CPS", 1, 20, 1, 8));
        SliderSetting max = addSetting(new SliderSetting("Max CPS", 1, 20, 1, 12));
        SliderSetting range = addSetting(new SliderSetting("Range", 1, 10, 0.1, 4));
        ModeSetting rotate = addSetting(new ModeSetting("Rotate", "Visible", "Visible", "Silent"));
        ModeSetting mobs = addSetting(new ModeSetting("Mobs", "Off", "Off", "On"));
        long nextHit;
        KillAura() { super("KillAura", Category.COMBAT); }
        public void onTick() {
            if (mc().player == null || mc().world == null) return;
            EntityLivingBase target = mc().world.loadedEntityList.stream().filter(e -> e instanceof EntityLivingBase)
                    .map(e -> (EntityLivingBase)e)
                    .filter(e -> e != mc().player && e.isEntityAlive() && distanceToPlayer(e) <= range.getValue())
                    .filter(e -> e instanceof EntityPlayer || (mobs.getMode().equals("On") && e instanceof IMob))
                    .min(Comparator.comparingDouble(InfinityClient.this::distanceToPlayer)).orElse(null);
            if (target == null || System.currentTimeMillis() < nextHit) return;
            Vec3d vec = target.getPositionEyes(1f).subtract(mc().player.getPositionEyes(1f));
            float yaw = (float)(MathHelper.atan2(vec.z, vec.x) * (180D / Math.PI)) - 90f;
            float pitch = (float)(-(MathHelper.atan2(vec.y, Math.sqrt(vec.x * vec.x + vec.z * vec.z)) * (180D / Math.PI)));
            if (rotate.getMode().equals("Visible")) { mc().player.rotationYaw = yaw; mc().player.rotationPitch = pitch; }
            mc().player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, mc().player.onGround));
            mc().playerController.attackEntity(mc().player, target);
            mc().player.swingArm(EnumHand.MAIN_HAND);
            int cps = Math.max(min.getInt(), Math.min(max.getInt(), (min.getInt() + max.getInt()) / 2));
            nextHit = System.currentTimeMillis() + (1000L / Math.max(1, cps));
        }
    }

    class CrystalMacro extends Module {
        SliderSetting placeDelay = addSetting(new SliderSetting("Place Delay", 0, 3, 0.05, 0));
        SliderSetting breakDelay = addSetting(new SliderSetting("Break Delay", 0, 3, 0.05, 0));
        SliderSetting range = addSetting(new SliderSetting("Range", 1, 10, 0.1, 4.5));
        long lastPlace, lastBreak;
        CrystalMacro() { super("CrystalMacro", Category.COMBAT); }
        public void onTick() {
            if (mc().objectMouseOver != null && mc().objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos pos = mc().objectMouseOver.getBlockPos();
                if (System.currentTimeMillis() - lastPlace >= (long)(placeDelay.getValue() * 1000L) && has(ItemEndCrystal.class)) {
                    mc().player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(pos, EnumFacing.UP, EnumHand.MAIN_HAND, 0.5f, 1f, 0.5f));
                    lastPlace = System.currentTimeMillis();
                }
            }
            EntityEnderCrystal crystal = nearestCrystal(range.getValue());
            if (crystal != null && System.currentTimeMillis() - lastBreak >= (long)(breakDelay.getValue() * 1000L)) {
                mc().playerController.attackEntity(mc().player, crystal);
                mc().player.swingArm(EnumHand.MAIN_HAND);
                lastBreak = System.currentTimeMillis();
            }
        }
    }

    class CrystalAura extends Module {
        ModeSetting mode = addSetting(new ModeSetting("Mode", "Single", "Single", "Multi"));
        SliderSetting range = addSetting(new SliderSetting("Range", 1, 10, 0.1, 5));
        SliderSetting breakDelay = addSetting(new SliderSetting("Break Delay", 0, 3, 0.05, 0.1));
        SliderSetting placeDelay = addSetting(new SliderSetting("Place Delay", 0, 3, 0.05, 0.1));
        long lastPlace,lastBreak;
        CrystalAura() { super("CrystalAura", Category.COMBAT); }
        public void onTick() {
            EntityPlayer target = mc().world.playerEntities.stream().filter(p -> p != mc().player && p.isEntityAlive())
                    .filter(p -> distanceToPlayer(p) <= range.getValue()).min(Comparator.comparingDouble(InfinityClient.this::distanceToPlayer)).orElse(null);
            if (target == null) return;
            if (mode.getMode().equals("Single")) {
                placeObsidian(target.getPosition().add(target.getHorizontalFacing().getOpposite().getDirectionVec()));
            } else {
                centerPlayer();
                BlockPos b = mc().player.getPosition();
                placeObsidian(b.north()); placeObsidian(b.south()); placeObsidian(b.east()); placeObsidian(b.west());
            }
            if (System.currentTimeMillis() - lastPlace >= (long)(placeDelay.getValue() * 1000L)) {
                placeCrystalNearby();
                lastPlace = System.currentTimeMillis();
            }
            EntityEnderCrystal crystal = nearestCrystal(range.getValue());
            if (crystal != null && System.currentTimeMillis() - lastBreak >= (long)(breakDelay.getValue() * 1000L)) {
                mc().playerController.attackEntity(mc().player, crystal);
                lastBreak = System.currentTimeMillis();
            }
        }
    }

    class Surround extends Module {
        SliderSetting placeDelay = addSetting(new SliderSetting("Place Delay", 0, 3, 0.05, 0.1));
        SliderSetting detectionRange = addSetting(new SliderSetting("Detection Range", 1, 5, 0.1, 3));
        long last;
        Surround() { super("Surround", Category.COMBAT); }
        public void onTick() {
            if (nearestCrystal(detectionRange.getValue()) == null) return;
            centerPlayer();
            if (System.currentTimeMillis() - last < (long)(placeDelay.getValue() * 1000L)) return;
            BlockPos p = mc().player.getPosition();
            placeProtect(p.north()); placeProtect(p.south()); placeProtect(p.east()); placeProtect(p.west()); placeProtect(p.up());
            last = System.currentTimeMillis();
        }
    }

    class AutoTotem extends Module { AutoTotem() { super("AutoTotem", Category.COMBAT); }
        public void onTick() {
            ItemStack off = mc().player.getHeldItemOffhand();
            if (off.getItem() != Items.TOTEM_OF_UNDYING) {
                for (int i = 0; i < 36; i++) {
                    ItemStack stack = mc().player.inventory.getStackInSlot(i);
                    if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
                        mc().playerController.windowClick(0, i < 9 ? i + 36 : i, 40, net.minecraft.inventory.ClickType.SWAP, mc().player);
                        break;
                    }
                }
            }
        }
    }

    class FastMine extends Module {
        ModeSetting speed = addSetting(new ModeSetting("Speed", "3x", "2x", "3x", "4x", "5x", "Instant"));
        FastMine() { super("FastMine", Category.PLAYER); }
        public void onTick() {
            if (mc().gameSettings.keyBindAttack.isKeyDown() && mc().objectMouseOver != null && mc().objectMouseOver.getBlockPos() != null) {
                BlockPos p = mc().objectMouseOver.getBlockPos();
                if (speed.getMode().equals("Instant")) {
                    mc().player.connection.sendPacket(new CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, p, EnumFacing.UP));
                } else {
                    int mult = Integer.parseInt(speed.getMode().substring(0, 1));
                    for (int i = 0; i < mult - 1; i++) {
                        mc().playerController.onPlayerDamageBlock(p, EnumFacing.UP);
                    }
                }
            }
        }
    }

    class FastPlace extends Module {
        ModeSetting speed = addSetting(new ModeSetting("Speed", "3x", "2x", "3x", "4x", "5x", "Instant"));
        FastPlace() { super("FastPlace", Category.PLAYER); }
        public void onTick() {
            if (mc().gameSettings.keyBindUseItem.isKeyDown()) {
                int loops = speed.getMode().equals("Instant") ? 5 : Integer.parseInt(speed.getMode().substring(0, 1));
                for (int i = 0; i < loops; i++) {
                    mc().player.connection.sendPacket(new CPacketPlayerTryUseItem(EnumHand.MAIN_HAND));
                }
            }
        }
    }

    class PacketFly extends Module {
        SliderSetting speed = addSetting(new SliderSetting("Speed", 0.1, 5, 0.1, 1.2));
        PacketFly() { super("PacketFly", Category.MOVEMENT); }
        public void onTick() {
            mc().player.capabilities.isFlying = false;
            mc().player.motionY = 0;
            if (mc().gameSettings.keyBindJump.isKeyDown()) mc().player.motionY = speed.getValue() * 0.1;
            if (mc().gameSettings.keyBindSneak.isKeyDown()) mc().player.motionY = -speed.getValue() * 0.1;
            float yaw = mc().player.rotationYaw;
            double forward = mc().player.movementInput.moveForward;
            double strafe = mc().player.movementInput.moveStrafe;
            double vel = speed.getValue() * 0.3;
            double mx = (forward * Math.cos(Math.toRadians(yaw + 90f)) + strafe * Math.sin(Math.toRadians(yaw + 90f))) * vel;
            double mz = (forward * Math.sin(Math.toRadians(yaw + 90f)) - strafe * Math.cos(Math.toRadians(yaw + 90f))) * vel;
            mc().player.connection.sendPacket(new CPacketPlayer.Position(mc().player.posX + mx, mc().player.posY + mc().player.motionY, mc().player.posZ + mz, false));
            mc().player.setPosition(mc().player.posX + mx, mc().player.posY + mc().player.motionY, mc().player.posZ + mz);
        }
        public boolean onPacketSend(Packet<?> packet) { return packet instanceof CPacketConfirmTeleport; }
    }

    class ElytraFly extends Module {
        SliderSetting speed = addSetting(new SliderSetting("Speed", 0.1, 5, 0.1, 1));
        ElytraFly() { super("ElytraFly", Category.MOVEMENT); }
        public void onTick() {
            ItemStack chest = mc().player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
            if (chest.getItem() instanceof ItemElytra && mc().player.isElytraFlying()) {
                mc().player.motionX = mc().player.motionY = mc().player.motionZ = 0;
                float s = (float) (speed.getValue() * 0.08f);
                if (mc().gameSettings.keyBindForward.isKeyDown()) mc().player.moveRelative(0f, 0f, s, 0f);
                if (mc().gameSettings.keyBindBack.isKeyDown()) mc().player.moveRelative(0f, 0f, -s, 0f);
                if (mc().gameSettings.keyBindJump.isKeyDown()) mc().player.motionY += s;
                if (mc().gameSettings.keyBindSneak.isKeyDown()) mc().player.motionY -= s;
            }
        }
    }

    class Velocity extends Module { Velocity() { super("Velocity", Category.COMBAT); }
        public boolean onPacketReceive(Packet<?> packet) {
            return packet instanceof SPacketEntityVelocity || packet instanceof SPacketExplosion;
        }
    }

    class PlayerESP extends Module { PlayerESP() { super("PlayerESP", Category.RENDER); }
        public void onRender3D(float partialTicks) { drawBoxes(true); }
    }
    class StorageESP extends Module { StorageESP() { super("StorageESP", Category.RENDER); }
        public void onRender3D(float partialTicks) { drawBoxes(false); }
    }

    class Blink extends Module {
        final List<Packet<?>> queue = new ArrayList<>();
        Blink() { super("Blink", Category.MOVEMENT); }
        public boolean onPacketSend(Packet<?> packet) {
            if (packet instanceof CPacketPlayer || packet instanceof CPacketEntityAction || packet instanceof CPacketPlayerDigging || packet instanceof CPacketPlayerTryUseItem || packet instanceof CPacketPlayerTryUseItemOnBlock) {
                queue.add(packet); return true;
            }
            return false;
        }
        public void onDisable() { queue.forEach(p -> mc().player.connection.sendPacket(p)); queue.clear(); }
    }

    class AirWalk extends Module { AirWalk() { super("AirWalk", Category.MOVEMENT); }
        public void onTick() { mc().player.onGround = true; }
    }

    class FastBow extends Module {
        SliderSetting drawTime = addSetting(new SliderSetting("Draw Time", 0, 2, 0.05, 0.2));
        FastBow() { super("FastBow", Category.COMBAT); }
        public void onTick() {
            ItemStack held = mc().player.getHeldItemMainhand();
            if (held.getItem() instanceof ItemBow && mc().gameSettings.keyBindUseItem.isKeyDown()) {
                int use = mc().player.getItemInUseMaxCount();
                if (use >= (int)(drawTime.getValue() * 20f)) {
                    mc().playerController.onStoppedUsingItem(mc().player);
                }
            }
        }
    }

    class ChunkTrails extends Module { ChunkTrails() { super("ChunkTrails", Category.RENDER); }
        public void onRender3D(float partialTicks) {
            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableDepth();
            for (BlockPos c : oldChunks) {
                AxisAlignedBB bb = new AxisAlignedBB(c.getX() * 16, mc().player.posY - 2, c.getZ() * 16, c.getX() * 16 + 16, mc().player.posY - 1.8, c.getZ() * 16 + 16);
                net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(bb.offset(-mc().getRenderManager().viewerPosX, -mc().getRenderManager().viewerPosY, -mc().getRenderManager().viewerPosZ), 0.3f, 0.7f, 1f, 0.8f);
            }
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }
    }

    class NoDamage extends Module { NoDamage() { super("NoDamage", Category.COMBAT); }
        public boolean onPacketReceive(Packet<?> packet) {
            return packet instanceof net.minecraft.network.play.server.SPacketEntityStatus || packet instanceof net.minecraft.network.play.server.SPacketCombatEvent;
        }
    }

    class AirPlace extends Module { AirPlace() { super("AirPlace", Category.PLAYER); }
        public void onTick() {
            if (mc().gameSettings.keyBindUseItem.isKeyDown() && mc().objectMouseOver != null && mc().objectMouseOver.typeOfHit == RayTraceResult.Type.MISS) {
                ItemStack held = mc().player.getHeldItemMainhand();
                if (held.getItem() instanceof ItemBlock) {
                    BlockPos pos = mc().player.getPosition().offset(mc().player.getHorizontalFacing());
                    mc().player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(pos, EnumFacing.UP, EnumHand.MAIN_HAND, 0.5f, 0.5f, 0.5f));
                }
            }
        }
    }

    private void placeProtect(BlockPos pos) {
        Block block = mc().world.getBlockState(pos).getBlock();
        if (block instanceof BlockAir) placeObsidian(pos);
    }

    private void placeObsidian(BlockPos pos) {
        if (mc().world.getBlockState(pos).getBlock() instanceof BlockAir && hasBlock(Blocks.OBSIDIAN, Blocks.ENDER_CHEST)) {
            mc().player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(pos.down(), EnumFacing.UP, EnumHand.MAIN_HAND, 0.5f, 1f, 0.5f));
        }
    }

    private void placeCrystalNearby() {
        BlockPos pos = mc().player.getPosition().down();
        mc().player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(pos, EnumFacing.UP, EnumHand.MAIN_HAND, 0.5f, 1f, 0.5f));
    }

    private EntityEnderCrystal nearestCrystal(double range) {
        return mc().world.loadedEntityList.stream().filter(e -> e instanceof EntityEnderCrystal).map(e -> (EntityEnderCrystal)e)
                .filter(e -> distanceToPlayer(e) <= range).min(Comparator.comparingDouble(InfinityClient.this::distanceToPlayer)).orElse(null);
    }

    private boolean has(Class<? extends Item> itemClass) {
        for (int i = 0; i < 9; i++) if (itemClass.isInstance(mc().player.inventory.getStackInSlot(i).getItem())) return true;
        return false;
    }

    private boolean hasBlock(Block... blocks) {
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc().player.inventory.getStackInSlot(i);
            if (st.getItem() instanceof ItemBlock) {
                Block b = ((ItemBlock) st.getItem()).getBlock();
                for (Block c : blocks) if (b == c) return true;
            }
        }
        return false;
    }

    private void drawBoxes(boolean players) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        if (players) {
            for (Entity e : mc().world.loadedEntityList) {
                if (e instanceof EntityPlayer && e != mc().player) {
                    AxisAlignedBB bb = e.getEntityBoundingBox().offset(-mc().getRenderManager().viewerPosX, -mc().getRenderManager().viewerPosY, -mc().getRenderManager().viewerPosZ);
                    net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(bb, 1f, 0f, 0f, 1f);
                }
            }
        } else {
            for (TileEntity te : mc().world.loadedTileEntityList) {
                if (te instanceof TileEntityChest || te instanceof TileEntityEnderChest || te instanceof TileEntityShulkerBox) {
                    BlockPos p = te.getPos();
                    AxisAlignedBB bb = new AxisAlignedBB(p).offset(-mc().getRenderManager().viewerPosX, -mc().getRenderManager().viewerPosY, -mc().getRenderManager().viewerPosZ);
                    net.minecraft.client.renderer.RenderGlobal.drawSelectionBoundingBox(bb, 0f, 0.8f, 1f, 1f);
                }
            }
        }
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private double distanceToPlayer(Entity e) {
        return mc().player.getDistance(e.posX, e.posY, e.posZ);
    }

    private void centerPlayer() {
        BlockPos p = mc().player.getPosition();
        mc().player.setPosition(p.getX() + 0.5, mc().player.posY, p.getZ() + 0.5);
    }
}
