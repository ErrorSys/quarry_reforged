package com.errorsys.quarry_reforged.client.screen;

import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.machine.config.ItemIoGroup;
import com.errorsys.quarry_reforged.machine.config.MachineIoMode;
import com.errorsys.quarry_reforged.machine.config.MachineRedstoneMode;
import com.errorsys.quarry_reforged.machine.config.MachineRelativeSide;
import com.errorsys.quarry_reforged.net.ModNetworking;
import com.errorsys.quarry_reforged.screen.QuarryScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class QuarryScreen extends HandledScreen<QuarryScreenHandler> {
    // Base template layout. Adjust these constants first when rebuilding the UI.
    private static final int OUTER_BG = 0xFF1A1F2A;
    private static final int PANEL_BG = 0xFF232B39;
    private static final int PANEL_INSET = 0xFF0C111A;
    private static final int BORDER = 0xFF697895;
    private static final int BUTTON_BG = 0xFF2C3444;
    private static final int BUTTON_BG_HOVER = 0xFF364055;
    private static final int BUTTON_INSET = 0xFF1A2230;
    private static final int BUTTON_BORDER = 0xFF7B8CA9;
    private static final int TEXT = 0xFFE8EEF7;
    private static final float BUTTON_TEXT_SCALE = 0.85f;

    private static final int MACHINE_X = 12;
    private static final int MACHINE_Y = 0;
    private static final int MACHINE_W = 205;
    private static final int MACHINE_H = 139;
    private static final int PLAYER_PANEL_W = 180;
    private static final int PLAYER_PANEL_H = 76;
    private static final int HOTBAR_PANEL_W = 166;
    private static final int HOTBAR_PANEL_H = 22;
    private static final int PLAYER_PANEL_X = (QuarryScreenHandler.GUI_WIDTH - PLAYER_PANEL_W) / 2;
    private static final int PLAYER_PANEL_Y = 142;
    private static final int HOTBAR_PANEL_X = (QuarryScreenHandler.GUI_WIDTH - HOTBAR_PANEL_W) / 2;
    private static final int HOTBAR_PANEL_Y = 222;
    private static final int MAIN_PANEL_LEFT_OFFSET = 7;
    private static final int CONTROLS_W = 72;
    private static final int CONTROLS_H = 36;
    private static final int CONTROLS_X = MACHINE_X + MAIN_PANEL_LEFT_OFFSET;
    private static final int CONTROLS_Y = 15;
    private static final int STATUS_GAP_FROM_CONTROLS = 3;
    private static final int STATUS_Y = 15;
    private static final int STATUS_W = 91;
    private static final int STATUS_H = 36;
    private static final int STATUS_X = CONTROLS_X + CONTROLS_W + STATUS_GAP_FROM_CONTROLS;
    private static final int STATUS_TEXT_Y_OFFSET = 6;
    private static final int STATUS_MESSAGE_Y_OFFSET = 22;
    private static final int ENERGY_W = 22;
    private static final int ENERGY_H = 117;
    private static final int ENERGY_RIGHT_OFFSET = 7;
    private static final int ENERGY_X = MACHINE_X + MACHINE_W - ENERGY_W - ENERGY_RIGHT_OFFSET;
    private static final int ENERGY_Y = 15;
    private static final int ENERGY_BAR_W = 16;
    private static final int ENERGY_BAR_X = ENERGY_X + (ENERGY_W - ENERGY_BAR_W) / 2;
    private static final int ENERGY_BAR_Y = ENERGY_Y + 3;
    private static final int ENERGY_BAR_H = 93;
    private static final int ENERGY_BAR_COLOR = 0xFF6DBB78;
    private static final int ENERGY_BAR_STRIPE_COLOR = 0xFF5EA267;
    private static final int UPGRADE_PANEL_X = MACHINE_X + MAIN_PANEL_LEFT_OFFSET;
    private static final int UPGRADE_PANEL_Y = 54;
    private static final int UPGRADE_PANEL_W = 148;
    private static final int UPGRADE_PANEL_H = 22;
    private static final int PROGRESS_PANEL_X = MACHINE_X + MAIN_PANEL_LEFT_OFFSET;
    private static final int PROGRESS_PANEL_Y = 79;
    private static final int PROGRESS_PANEL_W = 166;
    private static final int PROGRESS_PANEL_H = 10;
    private static final int OUTPUT_PANEL_X = MACHINE_X + MAIN_PANEL_LEFT_OFFSET;
    private static final int OUTPUT_PANEL_Y = 92;
    private static final int OUTPUT_PANEL_W = 166;
    private static final int OUTPUT_PANEL_H = 40;
    // Preview-only slot outlines for fast layout iteration. Copy final values into QuarryScreenHandler.
    private static final int PREVIEW_UPGRADE_X = UPGRADE_PANEL_X + 3;
    private static final int PREVIEW_UPGRADE_Y = 57;
    private static final int PREVIEW_OUTPUT_X = OUTPUT_PANEL_X + 3;
    private static final int PREVIEW_OUTPUT_Y = 95;
    private static final int PREVIEW_ENERGY_ITEM_X = ENERGY_X + 3;
    private static final int PREVIEW_ENERGY_ITEM_Y = ENERGY_Y + 98;
    private static final int PREVIEW_PLAYER_INV_X = PLAYER_PANEL_X + (PLAYER_PANEL_W - (9 * 18)) / 2;
    private static final int PREVIEW_PLAYER_INV_Y = 158;
    private static final int PREVIEW_HOTBAR_X = HOTBAR_PANEL_X + 3;
    private static final int PREVIEW_HOTBAR_Y = 225;

    private static final int TITLE_X = 19;
    private static final int TITLE_Y = 4;
    private static final int INVENTORY_TITLE_X = PLAYER_PANEL_X + 8;
    private static final int INVENTORY_TITLE_Y = PLAYER_PANEL_Y + 4;

    private static final int START_BTN_W = 66;
    private static final int START_BTN_H = 14;
    private static final int START_BTN_X = CONTROLS_X + 3;
    private static final int START_BTN_Y = CONTROLS_Y + 3;
    private static final int REMOVE_BTN_W = 66;
    private static final int REMOVE_BTN_H = 14;
    private static final int REMOVE_BTN_X = CONTROLS_X + 3;
    private static final int REMOVE_BTN_Y = CONTROLS_Y + 19;
    private static final int TAB_RAIL_X = -16;
    private static final int TAB_RAIL_Y = -3;
    private static final int TAB_BUTTON_W = 24;
    private static final int TAB_BUTTON_H = 24;
    private static final int TAB_ICON_PADDING = 4;
    private static final int TAB_INACTIVE_DARKEN = 0x66000000;
    private static final int TAB_GAP = -1;
    private static final int CONFIG_PANEL_X = 18;
    private static final int CONFIG_PANEL_Y = 13;
    private static final int CONFIG_PANEL_W = 193;
    private static final int CONFIG_PANEL_H = 121;
    private static final int CONFIG_IO_BOX_X = CONFIG_PANEL_X + 6;
    private static final int CONFIG_IO_BOX_Y = CONFIG_PANEL_Y + 2;
    private static final int CONFIG_IO_BOX_W = 72;
    private static final int CONFIG_IO_BOX_H = CONFIG_PANEL_H - 8;
    private static final int CONFIG_REDSTONE_BOX_X = CONFIG_IO_BOX_X + CONFIG_IO_BOX_W + 3;
    private static final int CONFIG_REDSTONE_BOX_Y = CONFIG_IO_BOX_Y;
    private static final int CONFIG_REDSTONE_BOX_W = 94;
    private static final int CONFIG_REDSTONE_BOX_H = CONFIG_IO_BOX_H;
    private static final int REDSTONE_BTN_X = CONFIG_REDSTONE_BOX_X + 3;
    private static final int REDSTONE_BTN_Y = CONFIG_REDSTONE_BOX_Y + 16;
    private static final int REDSTONE_BTN_W = CONFIG_REDSTONE_BOX_W - 6;
    private static final int REDSTONE_BTN_H = 14;
    private static final int IO_GROUP_BTN_X = CONFIG_IO_BOX_X + 3;
    private static final int IO_GROUP_BTN_Y = CONFIG_IO_BOX_Y + 86;
    private static final int IO_GROUP_BTN_W = CONFIG_IO_BOX_W - 6;
    private static final int IO_GROUP_BTN_H = 14;
    private static final int AUTO_EXPORT_BTN_X = CONFIG_IO_BOX_X + 3;
    private static final int AUTO_EXPORT_BTN_Y = CONFIG_IO_BOX_Y + 102;
    private static final int AUTO_EXPORT_BTN_W = CONFIG_IO_BOX_W - 6;
    private static final int AUTO_EXPORT_BTN_H = 14;
    private static final int SIDE_MAP_COLS = 3;
    private static final int SIDE_MAP_ROWS = 3;
    private static final int SIDE_CELL = 20;
    private static final int SIDE_GAP = 1;
    private static final int SIDE_MAP_PADDING = 3;
    private static final int SIDE_FACE_SIZE = 16;
    private static final int SIDE_FACE_INSET = (SIDE_CELL - SIDE_FACE_SIZE) / 2;
    private static final Identifier TAB_MAIN_ICON = new Identifier("quarry_reforged", "textures/block/quarry_front.png");
    private static final Identifier TAB_MAIN_ICON_ACTIVE = new Identifier("quarry_reforged", "textures/block/quarry_front_active.png");
    private static final Identifier TAB_CONFIG_ICON = new Identifier("techreborn", "textures/item/tool/wrench.png");
    private static final Identifier IO_MAP_BG = new Identifier("quarry_reforged", "textures/gui/io_map.png");
    private static final Identifier QUARRY_FRONT_TEXTURE = new Identifier("quarry_reforged", "textures/block/quarry_front.png");
    private static final Identifier QUARRY_BACK_TEXTURE = new Identifier("quarry_reforged", "textures/block/quarry_back.png");
    private static final Identifier MACHINE_SIDE_TEXTURE = new Identifier("quarry_reforged", "textures/block/machine_side.png");
    private static final Identifier MACHINE_BOTTOM_TEXTURE = new Identifier("quarry_reforged", "textures/block/machine_bottom.png");

    private QuarryButton startStopBtn;
    private QuarryButton removeFrameBtn;
    private QuarryButton redstoneModeBtn;
    private QuarryButton ioGroupBtn;
    private QuarryButton autoExportBtn;
    private ScreenTab activeTab = ScreenTab.MAIN;
    private ItemIoGroup selectedIoGroup = ItemIoGroup.OUTPUT;

    public QuarryScreen(QuarryScreenHandler handler, net.minecraft.entity.player.PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = QuarryScreenHandler.GUI_WIDTH;
        this.backgroundHeight = QuarryScreenHandler.GUI_HEIGHT;
        this.titleX = TITLE_X;
        this.titleY = TITLE_Y;
        this.playerInventoryTitleY = INVENTORY_TITLE_Y;
    }

    @Override
    protected void init() {
        super.init();
        clearChildren();
        applyMachineSlotVisibility();

        int x = this.x;
        int y = this.y;

        startStopBtn = new QuarryButton(x + START_BTN_X, y + START_BTN_Y, START_BTN_W, START_BTN_H, Text.translatable("gui.quarry_reforged.button.start"), () -> send(ModNetworking.TOGGLE_ACTIVE));
        removeFrameBtn = new QuarryButton(x + REMOVE_BTN_X, y + REMOVE_BTN_Y, REMOVE_BTN_W, REMOVE_BTN_H, Text.translatable("gui.quarry_reforged.button.remove_frame"), () -> send(ModNetworking.REMOVE_FRAME));
        redstoneModeBtn = new QuarryButton(x + REDSTONE_BTN_X, y + REDSTONE_BTN_Y, REDSTONE_BTN_W, REDSTONE_BTN_H, Text.empty(), () -> send(ModNetworking.CYCLE_REDSTONE_MODE));
        ioGroupBtn = new QuarryButton(x + IO_GROUP_BTN_X, y + IO_GROUP_BTN_Y, IO_GROUP_BTN_W, IO_GROUP_BTN_H, Text.empty(), () -> {
            selectedIoGroup = selectedIoGroup == ItemIoGroup.UPGRADES ? ItemIoGroup.OUTPUT : ItemIoGroup.UPGRADES;
        });
        autoExportBtn = new QuarryButton(x + AUTO_EXPORT_BTN_X, y + AUTO_EXPORT_BTN_Y, AUTO_EXPORT_BTN_W, AUTO_EXPORT_BTN_H, Text.empty(), () -> send(ModNetworking.TOGGLE_AUTO_EXPORT));

        addDrawableChild(startStopBtn);
        addDrawableChild(removeFrameBtn);
        addDrawableChild(redstoneModeBtn);
        addDrawableChild(ioGroupBtn);
        addDrawableChild(autoExportBtn);
    }

    private void send(Identifier channel) {
        PacketByteBuf buf = PacketByteBufs.create();
        BlockPos pos = handler.pos;
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(channel, buf);
    }

    private void sendCycleSideMode(ItemIoGroup group, MachineRelativeSide side) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.pos);
        buf.writeVarInt(group.ordinal());
        buf.writeVarInt(side.ordinal());
        ClientPlayNetworking.send(ModNetworking.CYCLE_SIDE_MODE, buf);
    }

    @Nullable
    private MachineRelativeSide getSideAt(int mouseX, int mouseY) {
        int originX = sideMapOriginX();
        int originY = sideMapOriginY();

        if (inRect(mouseX, mouseY, sideCellX(originX, 1), sideCellY(originY, 0), SIDE_CELL, SIDE_CELL)) return MachineRelativeSide.UP;
        if (inRect(mouseX, mouseY, sideCellX(originX, 0), sideCellY(originY, 1), SIDE_CELL, SIDE_CELL)) return MachineRelativeSide.LEFT;
        if (inRect(mouseX, mouseY, sideCellX(originX, 1), sideCellY(originY, 1), SIDE_CELL, SIDE_CELL)) return MachineRelativeSide.FRONT;
        if (inRect(mouseX, mouseY, sideCellX(originX, 2), sideCellY(originY, 1), SIDE_CELL, SIDE_CELL)) return MachineRelativeSide.RIGHT;
        if (inRect(mouseX, mouseY, sideCellX(originX, 1), sideCellY(originY, 2), SIDE_CELL, SIDE_CELL)) return MachineRelativeSide.DOWN;
        if (inRect(mouseX, mouseY, sideCellX(originX, 0), sideCellY(originY, 2), SIDE_CELL, SIDE_CELL)) return MachineRelativeSide.BACK;
        return null;
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawWindow(context, this.x + MACHINE_X, this.y + MACHINE_Y, MACHINE_W, MACHINE_H);
        drawTabRail(context);
        drawWindow(context, this.x + PLAYER_PANEL_X, this.y + PLAYER_PANEL_Y, PLAYER_PANEL_W, PLAYER_PANEL_H);
        drawWindow(context, this.x + HOTBAR_PANEL_X, this.y + HOTBAR_PANEL_Y, HOTBAR_PANEL_W, HOTBAR_PANEL_H);
        drawSlotFrames(context, this.x + PREVIEW_PLAYER_INV_X, this.y + PREVIEW_PLAYER_INV_Y, 9, 3);
        drawSlotFrames(context, this.x + PREVIEW_HOTBAR_X, this.y + PREVIEW_HOTBAR_Y, 9, 1);

        if (activeTab == ScreenTab.MAIN) {
            drawPanel(context, this.x + CONTROLS_X, this.y + CONTROLS_Y, CONTROLS_W, CONTROLS_H);
            drawPanel(context, this.x + STATUS_X, this.y + STATUS_Y, STATUS_W, STATUS_H);
            drawPanel(context, this.x + ENERGY_X, this.y + ENERGY_Y, ENERGY_W, ENERGY_H);
            drawPanel(context, this.x + UPGRADE_PANEL_X, this.y + UPGRADE_PANEL_Y, UPGRADE_PANEL_W, UPGRADE_PANEL_H);
            drawPanel(context, this.x + PROGRESS_PANEL_X, this.y + PROGRESS_PANEL_Y, PROGRESS_PANEL_W, PROGRESS_PANEL_H);
            drawPanel(context, this.x + OUTPUT_PANEL_X, this.y + OUTPUT_PANEL_Y, OUTPUT_PANEL_W, OUTPUT_PANEL_H);

            drawSlotFrames(context, this.x + PREVIEW_UPGRADE_X, this.y + PREVIEW_UPGRADE_Y, 8, 1);
            drawSlotFrames(context, this.x + PREVIEW_OUTPUT_X, this.y + PREVIEW_OUTPUT_Y, 9, 2);
            drawSlotFrames(context, this.x + PREVIEW_ENERGY_ITEM_X, this.y + PREVIEW_ENERGY_ITEM_Y, 1, 1);

            long energy = ((long) handler.props.get(0) << 32) | (handler.props.get(1) & 0xffffffffL);
            long cap = ((long) handler.props.get(2) << 32) | (handler.props.get(3) & 0xffffffffL);
            int fillHeight = cap <= 0 ? 0 : (int) MathHelper.clamp((float) (energy * ENERGY_BAR_H) / cap, 0, ENERGY_BAR_H);
            int barLeft = this.x + ENERGY_BAR_X;
            int barBottom = this.y + ENERGY_BAR_Y + ENERGY_BAR_H;
            int fillTop = barBottom - fillHeight;
            context.fill(barLeft, fillTop, barLeft + ENERGY_BAR_W, barBottom, ENERGY_BAR_COLOR);
            for (int sy = 0; sy < fillHeight; sy += 2) {
                int stripeY = barBottom - sy - 1;
                context.fill(barLeft, stripeY, barLeft + ENERGY_BAR_W, stripeY + 1, ENERGY_BAR_STRIPE_COLOR);
            }

            int progress = handler.props.get(6);
            int progressWidth = MathHelper.clamp((progress * (PROGRESS_PANEL_W - 4)) / 1000, 0, PROGRESS_PANEL_W - 4);
            context.fill(this.x + PROGRESS_PANEL_X + 2, this.y + PROGRESS_PANEL_Y + 2, this.x + PROGRESS_PANEL_X + 2 + progressWidth, this.y + PROGRESS_PANEL_Y + PROGRESS_PANEL_H - 2, 0xFF7A9C54);
        } else if (activeTab == ScreenTab.CONFIG) {
            drawPanel(context, this.x + CONFIG_PANEL_X, this.y + CONFIG_PANEL_Y, CONFIG_PANEL_W, CONFIG_PANEL_H);
            context.fill(this.x + CONFIG_IO_BOX_X, this.y + CONFIG_IO_BOX_Y, this.x + CONFIG_IO_BOX_X + CONFIG_IO_BOX_W, this.y + CONFIG_IO_BOX_Y + CONFIG_IO_BOX_H, PANEL_INSET);
            context.fill(this.x + CONFIG_REDSTONE_BOX_X, this.y + CONFIG_REDSTONE_BOX_Y, this.x + CONFIG_REDSTONE_BOX_X + CONFIG_REDSTONE_BOX_W, this.y + CONFIG_REDSTONE_BOX_Y + CONFIG_REDSTONE_BOX_H, PANEL_INSET);
            drawIoMap(context, mouseX, mouseY);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        applyMachineSlotVisibility();
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        QuarryBlockEntity quarry = handler.quarry;
        boolean running = quarry.isActiveClient() || quarry.isFrameRemovalActiveClient();
        startStopBtn.setMessage(Text.translatable(running ? "gui.quarry_reforged.button.stop" : "gui.quarry_reforged.button.start"));
        startStopBtn.active = quarry.canToggleActiveClient();
        removeFrameBtn.active = quarry.canRemoveFrameClient();
        redstoneModeBtn.setMessage(Text.translatable(getRedstoneLabel(quarry.getRedstoneMode())));
        ioGroupBtn.setMessage(Text.translatable(selectedIoGroup.getTranslationKey()));
        autoExportBtn.setMessage(Text.translatable(quarry.isAutoExportEnabled() ? "gui.quarry_reforged.io.auto_export.on" : "gui.quarry_reforged.io.auto_export.off"));

        startStopBtn.visible = activeTab == ScreenTab.MAIN;
        removeFrameBtn.visible = activeTab == ScreenTab.MAIN;
        redstoneModeBtn.visible = activeTab == ScreenTab.CONFIG;
        ioGroupBtn.visible = activeTab == ScreenTab.CONFIG;
        autoExportBtn.visible = activeTab == ScreenTab.CONFIG;
        autoExportBtn.active = selectedIoGroup == ItemIoGroup.OUTPUT;

        if (activeTab == ScreenTab.MAIN) {
            drawCenteredString(context, quarry.getDisplayStatus(), this.x + STATUS_X + STATUS_W / 2, this.y + STATUS_Y + STATUS_TEXT_Y_OFFSET, TEXT);
            drawCenteredScaledString(
                    context,
                    trimMultiline(quarry.getDisplayStatusMessage(), (STATUS_W - 8) * 2),
                    this.x + STATUS_X + STATUS_W / 2,
                    this.y + STATUS_Y + STATUS_MESSAGE_Y_OFFSET,
                    quarry.getDisplayStatusColor(),
                    0.5f
            );
        }

        if (activeTab == ScreenTab.CONFIG) {
            drawLeftMultilineString(context, Text.translatable("gui.quarry_reforged.tab.io").getString(), this.x + CONFIG_IO_BOX_X + 4, this.y + CONFIG_IO_BOX_Y + 3, TEXT);
            drawLeftMultilineString(context, Text.translatable("gui.quarry_reforged.tab.redstone").getString(), this.x + CONFIG_REDSTONE_BOX_X + 4, this.y + CONFIG_REDSTONE_BOX_Y + 3, TEXT);
            drawCenteredScaledString(
                    context,
                    Text.translatable(getRedstoneDescription(quarry.getRedstoneMode())).getString(),
                    this.x + CONFIG_REDSTONE_BOX_X + CONFIG_REDSTONE_BOX_W / 2,
                    this.y + CONFIG_REDSTONE_BOX_Y + 46,
                    TEXT,
                    0.58f
            );
            if (inRect(mouseX, mouseY, this.x + AUTO_EXPORT_BTN_X, this.y + AUTO_EXPORT_BTN_Y, AUTO_EXPORT_BTN_W, AUTO_EXPORT_BTN_H)) {
                drawTooltipMultiline(context, Text.translatable("gui.quarry_reforged.io.auto_export.tooltip"), mouseX, mouseY);
            }
            MachineRelativeSide hovered = getSideAt(mouseX, mouseY);
            if (hovered != null) {
                MachineIoMode mode = quarry.getSideMode(selectedIoGroup, hovered);
                drawTooltipMultiline(context, Text.translatable("gui.quarry_reforged.io.side_tooltip",
                        Text.translatable(hovered.getTranslationKey()),
                        Text.translatable(getIoModeLabel(mode))), mouseX, mouseY);
            }
        }

        boolean hoveringEnergyItemSlot = inRect(mouseX, mouseY, this.x + PREVIEW_ENERGY_ITEM_X, this.y + PREVIEW_ENERGY_ITEM_Y, 18, 18);
        if (activeTab == ScreenTab.MAIN
            && mouseX >= this.x + ENERGY_X && mouseX <= this.x + ENERGY_X + ENERGY_W
            && mouseY >= this.y + ENERGY_Y && mouseY <= this.y + ENERGY_Y + ENERGY_H
            && !hoveringEnergyItemSlot) {
            long displayEnergy = ((long) handler.props.get(0) << 32) | (handler.props.get(1) & 0xffffffffL);
            long displayCap = ((long) handler.props.get(2) << 32) | (handler.props.get(3) & 0xffffffffL);
            drawTooltipMultiline(context, Text.literal(formatEnergyTier(displayEnergy) + " / " + formatEnergyTier(displayCap)), mouseX, mouseY);
        }

        if (activeTab == ScreenTab.MAIN
            && mouseX >= this.x + PROGRESS_PANEL_X && mouseX <= this.x + PROGRESS_PANEL_X + PROGRESS_PANEL_W
            && mouseY >= this.y + PROGRESS_PANEL_Y && mouseY <= this.y + PROGRESS_PANEL_Y + PROGRESS_PANEL_H) {
            int current = quarry.getProgressLayerCurrent();
            int total = quarry.getProgressLayerTotal();
            int percent = quarry.getProgressPercentDisplay();
            drawTooltipMultiline(context, Text.literal(current + "/" + total + " - " + percent + "%"), mouseX, mouseY);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        drawLeftMultilineString(context, this.title.getString(), TITLE_X, TITLE_Y, TEXT);
        drawLeftMultilineString(context, this.playerInventoryTitle.getString(), INVENTORY_TITLE_X, INVENTORY_TITLE_Y, TEXT);
    }

    private void drawTabRail(DrawContext context) {
        int railX = this.x + TAB_RAIL_X;
        int railY = this.y + TAB_RAIL_Y;
        int railInnerX = railX + 3;
        int railInnerY = railY + 3;
        int railW = (this.x + MACHINE_X) - railInnerX;
        int railH = TAB_BUTTON_H * 2 + TAB_GAP;

        // Attached strip uses the same background as the main machine window.
        context.fill(railInnerX, railInnerY, railInnerX + railW, railInnerY + railH, OUTER_BG);

        boolean running = handler.quarry.isActiveClient() || handler.quarry.isFrameRemovalActiveClient();
        drawTabButton(context, railInnerX + 1, railInnerY, ScreenTab.MAIN, running ? TAB_MAIN_ICON_ACTIVE : TAB_MAIN_ICON);
        drawTabButton(context, railInnerX + 1, railInnerY + TAB_BUTTON_H + TAB_GAP, ScreenTab.CONFIG, TAB_CONFIG_ICON);

        // Active tab merges through the main window border.
        int windowLeft = this.x + MACHINE_X;
        int activeY = getTabY(activeTab);
        context.fill(windowLeft, activeY + 1, windowLeft + 1, activeY + TAB_BUTTON_H - 1, OUTER_BG);

        // Separate active icon from inactive icons without moving icon positions.
        int activeX = getTabX(activeTab);
        context.fill(activeX, activeY, activeX + TAB_BUTTON_W, activeY + 1, BORDER);
        context.fill(activeX, activeY + TAB_BUTTON_H - 1, activeX + TAB_BUTTON_W, activeY + TAB_BUTTON_H, BORDER);

        // Draw rail border last so it remains persistent across tab switches.
        context.fill(railInnerX, railInnerY, railInnerX + 1, railInnerY + railH, BORDER);
        context.fill(railInnerX, railInnerY, railInnerX + railW, railInnerY + 1, BORDER);
        context.fill(railInnerX, railInnerY + railH - 1, railInnerX + railW, railInnerY + railH, BORDER);

    }

    private void drawTabButton(DrawContext context, int x, int y, ScreenTab tab, Identifier icon) {
        boolean active = tab == activeTab;
        int bg = OUTER_BG;
        context.fill(x, y, x + TAB_BUTTON_W, y + TAB_BUTTON_H, bg);
        // No per-tab border box: icon tiles are integrated into the strip.
        int iconPadding = active ? TAB_ICON_PADDING : TAB_ICON_PADDING + 1;
        int iconW = TAB_BUTTON_W - iconPadding * 2;
        int iconH = TAB_BUTTON_H - iconPadding * 2;
        context.drawTexture(icon, x + iconPadding, y + iconPadding, 0, 0, iconW, iconH, iconW, iconH);
        if (!active) {
            context.fill(x, y, x + TAB_BUTTON_W, y + TAB_BUTTON_H, TAB_INACTIVE_DARKEN);
        }
    }

    private int getTabX(ScreenTab tab) {
        int railInnerX = this.x + TAB_RAIL_X + 3;
        return railInnerX + 1;
    }

    private int getTabY(ScreenTab tab) {
        int railInnerY = this.y + TAB_RAIL_Y + 3;
        return switch (tab) {
            case MAIN -> railInnerY;
            case CONFIG -> railInnerY + TAB_BUTTON_H + TAB_GAP;
        };
    }

    private void drawIoMap(DrawContext context, int mouseX, int mouseY) {
        QuarryBlockEntity quarry = handler.quarry;
        int originX = sideMapOriginX();
        int originY = sideMapOriginY();
        int innerX = originX + SIDE_MAP_PADDING;
        int innerY = originY + SIDE_MAP_PADDING;

        context.fill(originX, originY, originX + sideMapWidth(), originY + sideMapHeight(), BUTTON_INSET);
        context.drawBorder(originX, originY, sideMapWidth(), sideMapHeight(), BUTTON_BORDER);
        context.drawTexture(IO_MAP_BG, innerX, innerY, 0, 0, sideMapGridWidth(), sideMapGridHeight(), sideMapGridWidth(), sideMapGridHeight());
        drawSideCell(context, quarry, MachineRelativeSide.UP, sideCellX(originX, 1), sideCellY(originY, 0));
        drawSideCell(context, quarry, MachineRelativeSide.LEFT, sideCellX(originX, 0), sideCellY(originY, 1));
        drawSideCell(context, quarry, MachineRelativeSide.FRONT, sideCellX(originX, 1), sideCellY(originY, 1));
        drawSideCell(context, quarry, MachineRelativeSide.RIGHT, sideCellX(originX, 2), sideCellY(originY, 1));
        drawSideCell(context, quarry, MachineRelativeSide.DOWN, sideCellX(originX, 1), sideCellY(originY, 2));
        drawSideCell(context, quarry, MachineRelativeSide.BACK, sideCellX(originX, 0), sideCellY(originY, 2));

        MachineRelativeSide hovered = getSideAt(mouseX, mouseY);
        if (hovered != null) {
            int[] pos = sideCellPos(hovered, originX, originY);
            context.drawBorder(pos[0], pos[1], SIDE_CELL, SIDE_CELL, 0xFFE8EEF7);
        }
    }

    private void drawSideCell(DrawContext context, QuarryBlockEntity quarry, MachineRelativeSide side, int x, int y) {
        MachineIoMode mode = quarry.getSideMode(selectedIoGroup, side);
        Identifier faceTexture = getFaceTexture(side);
        context.drawTexture(
                faceTexture,
                x + SIDE_FACE_INSET,
                y + SIDE_FACE_INSET,
                0,
                0,
                SIDE_FACE_SIZE,
                SIDE_FACE_SIZE,
                16,
                16
        );
        context.fill(
                x + SIDE_FACE_INSET,
                y + SIDE_FACE_INSET,
                x + SIDE_FACE_INSET + SIDE_FACE_SIZE,
                y + SIDE_FACE_INSET + SIDE_FACE_SIZE,
                getIoModeColor(mode)
        );
        context.drawBorder(x, y, SIDE_CELL, SIDE_CELL, BUTTON_BORDER);
    }

    private Identifier getFaceTexture(MachineRelativeSide side) {
        return switch (side) {
            case FRONT -> QUARRY_FRONT_TEXTURE;
            case BACK -> QUARRY_BACK_TEXTURE;
            case LEFT, RIGHT, UP -> MACHINE_SIDE_TEXTURE;
            case DOWN -> MACHINE_BOTTOM_TEXTURE;
        };
    }

    private int[] sideCellPos(MachineRelativeSide side, int originX, int originY) {
        return switch (side) {
            case UP -> new int[]{sideCellX(originX, 1), sideCellY(originY, 0)};
            case LEFT -> new int[]{sideCellX(originX, 0), sideCellY(originY, 1)};
            case FRONT -> new int[]{sideCellX(originX, 1), sideCellY(originY, 1)};
            case RIGHT -> new int[]{sideCellX(originX, 2), sideCellY(originY, 1)};
            case DOWN -> new int[]{sideCellX(originX, 1), sideCellY(originY, 2)};
            case BACK -> new int[]{sideCellX(originX, 0), sideCellY(originY, 2)};
        };
    }

    private int sideCellX(int originX, int col) {
        return originX + SIDE_MAP_PADDING + col * (SIDE_CELL + SIDE_GAP);
    }

    private int sideCellY(int originY, int row) {
        return originY + SIDE_MAP_PADDING + row * (SIDE_CELL + SIDE_GAP);
    }

    private int sideMapGridWidth() {
        return SIDE_MAP_COLS * SIDE_CELL + (SIDE_MAP_COLS - 1) * SIDE_GAP;
    }

    private int sideMapGridHeight() {
        return SIDE_MAP_ROWS * SIDE_CELL + (SIDE_MAP_ROWS - 1) * SIDE_GAP;
    }

    private int sideMapWidth() {
        return sideMapGridWidth() + SIDE_MAP_PADDING * 2;
    }

    private int sideMapHeight() {
        return sideMapGridHeight() + SIDE_MAP_PADDING * 2;
    }

    private int sideMapOriginX() {
        return this.x + CONFIG_IO_BOX_X + (CONFIG_IO_BOX_W - sideMapWidth()) / 2;
    }

    private int sideMapOriginY() {
        return this.y + CONFIG_IO_BOX_Y + 16;
    }

    private int getIoModeColor(MachineIoMode mode) {
        return switch (mode) {
            case NONE -> 0x553A404D;
            case INPUT -> 0x557EA8FF;
            case OUTPUT -> 0x55E0A14A;
            case BOTH -> 0x5574D48B;
        };
    }

    private String getRedstoneLabel(MachineRedstoneMode mode) {
        return switch (mode) {
            case IGNORED -> "gui.quarry_reforged.redstone.ignored";
            case HIGH -> "gui.quarry_reforged.redstone.high";
            case LOW -> "gui.quarry_reforged.redstone.low";
        };
    }

    private String getRedstoneDescription(MachineRedstoneMode mode) {
        return switch (mode) {
            case IGNORED -> "gui.quarry_reforged.redstone.desc.ignored";
            case HIGH -> "gui.quarry_reforged.redstone.desc.high";
            case LOW -> "gui.quarry_reforged.redstone.desc.low";
        };
    }

    private String getIoModeLabel(MachineIoMode mode) {
        return switch (mode) {
            case NONE -> "gui.quarry_reforged.io.mode.none";
            case INPUT -> "gui.quarry_reforged.io.mode.input";
            case OUTPUT -> "gui.quarry_reforged.io.mode.output";
            case BOTH -> "gui.quarry_reforged.io.mode.both";
        };
    }

    private void drawWindow(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, OUTER_BG);
        context.drawBorder(x, y, width, height, BORDER);
    }

    private void drawPanel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, PANEL_BG);
        context.drawBorder(x, y, width, height, BORDER);
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL_INSET);
    }

    private void drawSlotFrames(DrawContext context, int startX, int startY, int cols, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                context.drawBorder(startX + col * 18 - 1, startY + row * 18 - 1, 18, 18, BORDER);
            }
        }
    }

    private void drawCenteredString(DrawContext context, String text, int centerX, int y, int color) {
        String normalized = normalizeNewlines(text);
        String[] lines = normalized.split("\\R", -1);
        int lineHeight = textRenderer.fontHeight + 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            context.drawText(textRenderer, line, centerX - textRenderer.getWidth(line) / 2, y + i * lineHeight, color, false);
        }
    }

    private void drawCenteredScaledString(DrawContext context, String text, int centerX, int y, int color, float scale) {
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        float inv = 1.0f / scale;
        String[] lines = normalizeNewlines(text).split("\\R", -1);
        int lineHeight = textRenderer.fontHeight + 1;
        int totalHeight = (lines.length - 1) * lineHeight;
        int drawY = Math.round((y - totalHeight * scale / 2.0f) * inv);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int drawX = Math.round(centerX * inv - textRenderer.getWidth(line) / 2.0f);
            context.drawText(textRenderer, line, drawX, drawY + i * lineHeight, color, false);
        }
        context.getMatrices().pop();
    }

    private String trim(String text, int width) {
        return textRenderer.trimToWidth(text, width);
    }

    private String trimMultiline(String text, int width) {
        String[] lines = normalizeNewlines(text).split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = trim(lines[i], width);
        }
        return String.join("\n", lines);
    }

    private void drawLeftMultilineString(DrawContext context, String text, int x, int y, int color) {
        String[] lines = normalizeNewlines(text).split("\\R", -1);
        int lineHeight = textRenderer.fontHeight + 1;
        for (int i = 0; i < lines.length; i++) {
            context.drawText(textRenderer, lines[i], x, y + i * lineHeight, color, false);
        }
    }

    private String normalizeNewlines(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("\\n", "\n").replace("\r\n", "\n").replace('\r', '\n');
    }

    private void applyMachineSlotVisibility() {
        handler.setMachineSlotsVisible(activeTab == ScreenTab.MAIN);
    }

    private void drawTooltipMultiline(DrawContext context, Text text, int mouseX, int mouseY) {
        String normalized = normalizeNewlines(text.getString());
        String[] lines = normalized.split("\\R", -1);
        if (lines.length <= 1) {
            context.drawTooltip(textRenderer, Text.literal(normalized), mouseX, mouseY);
            return;
        }
        List<Text> tooltipLines = new ArrayList<>();
        for (String line : lines) {
            tooltipLines.add(Text.literal(line));
        }
        context.drawTooltip(textRenderer, tooltipLines, mouseX, mouseY);
    }

    private String formatEnergyTier(long energy) {
        if (energy < 1_000L) {
            return energy + "E";
        }
        if (energy < 1_000_000L) {
            return (energy / 1_000L) + "KE";
        }
        return String.format(Locale.ROOT, "%.2fME", energy / 1_000_000.0);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        applyMachineSlotVisibility();
        int mx = (int) mouseX;
        int my = (int) mouseY;

        int mainTabY = getTabY(ScreenTab.MAIN);
        int configTabY = getTabY(ScreenTab.CONFIG);
        if (inRect(mx, my, getTabX(ScreenTab.MAIN), mainTabY, TAB_BUTTON_W, TAB_BUTTON_H)) {
            activeTab = ScreenTab.MAIN;
            applyMachineSlotVisibility();
            return true;
        }
        if (inRect(mx, my, getTabX(ScreenTab.CONFIG), configTabY, TAB_BUTTON_W, TAB_BUTTON_H)) {
            activeTab = ScreenTab.CONFIG;
            applyMachineSlotVisibility();
            return true;
        }

        if (activeTab == ScreenTab.CONFIG) {
            MachineRelativeSide side = getSideAt(mx, my);
            if (side != null) {
                sendCycleSideMode(selectedIoGroup, side);
                return true;
            }
        }

        if (activeTab == ScreenTab.MAIN) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Config tab: only allow explicit config widgets and player inventory/hotbar interaction.
        if (activeTab == ScreenTab.CONFIG) {
            if (redstoneModeBtn.mouseClicked(mouseX, mouseY, button)) return true;
            if (ioGroupBtn.mouseClicked(mouseX, mouseY, button)) return true;
            if (autoExportBtn.mouseClicked(mouseX, mouseY, button)) return true;
        }

        boolean inPlayerInventory = inRect(mx, my, this.x + PREVIEW_PLAYER_INV_X - 1, this.y + PREVIEW_PLAYER_INV_Y - 1, 9 * 18, 3 * 18);
        boolean inHotbar = inRect(mx, my, this.x + PREVIEW_HOTBAR_X - 1, this.y + PREVIEW_HOTBAR_Y - 1, 9 * 18, 18);
        if (inPlayerInventory || inHotbar) {
            if (activeTab == ScreenTab.CONFIG && button == 0 && hasShiftDown()) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    private enum ScreenTab {
        MAIN,
        CONFIG
    }

    private final class QuarryButton extends PressableWidget {
        private final Runnable onPressAction;

        private QuarryButton(int x, int y, int width, int height, Text message, Runnable onPressAction) {
            super(x, y, width, height, message);
            this.onPressAction = onPressAction;
        }

        @Override
        public void onPress() {
            onPressAction.run();
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }

        @Override
        public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
            int outerColor = this.active ? BUTTON_BORDER : 0xFF4E586B;
            int baseColor = this.active ? BUTTON_BG : 0xFF242A36;
            int innerColor = this.active ? (this.isHovered() ? BUTTON_BG_HOVER : BUTTON_INSET) : 0xFF161B24;
            context.fill(getX(), getY(), getX() + width, getY() + height, baseColor);
            context.drawBorder(getX(), getY(), width, height, outerColor);
            context.fill(getX() + 2, getY() + 2, getX() + width - 2, getY() + height - 2, innerColor);

            int maxLabelWidth = Math.max(1, Math.round((width - 6) / BUTTON_TEXT_SCALE));
            String[] rawLines = normalizeNewlines(getMessage().getString()).split("\\R", -1);
            int lineHeight = textRenderer.fontHeight + 1;
            int maxLines = Math.max(1, Math.round((height - 4) / (lineHeight * BUTTON_TEXT_SCALE)));
            int linesToDraw = Math.min(rawLines.length, maxLines);
            String[] lines = new String[linesToDraw];
            int maxRenderedWidth = 0;
            for (int i = 0; i < linesToDraw; i++) {
                lines[i] = textRenderer.trimToWidth(rawLines[i], maxLabelWidth);
                maxRenderedWidth = Math.max(maxRenderedWidth, textRenderer.getWidth(lines[i]));
            }
            float scaledLabelWidth = maxRenderedWidth * BUTTON_TEXT_SCALE;
            float scaledLabelHeight = ((linesToDraw * lineHeight) - 1) * BUTTON_TEXT_SCALE;
            int tx = Math.round((getX() + (width - scaledLabelWidth) / 2.0f) / BUTTON_TEXT_SCALE);
            int ty = Math.round((getY() + (height - scaledLabelHeight) / 2.0f) / BUTTON_TEXT_SCALE);

            context.getMatrices().push();
            context.getMatrices().scale(BUTTON_TEXT_SCALE, BUTTON_TEXT_SCALE, 1.0f);
            for (int i = 0; i < linesToDraw; i++) {
                int lineX = tx + (maxRenderedWidth - textRenderer.getWidth(lines[i])) / 2;
                context.drawText(textRenderer, lines[i], lineX, ty + i * lineHeight, this.active ? TEXT : 0xFF8A93A5, false);
            }
            context.getMatrices().pop();
        }
    }
}
