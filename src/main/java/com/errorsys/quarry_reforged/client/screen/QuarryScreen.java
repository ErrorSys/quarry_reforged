package com.errorsys.quarry_reforged.client.screen;

import com.errorsys.quarry_reforged.content.blockentity.QuarryBlockEntity;
import com.errorsys.quarry_reforged.net.ModNetworking;
import com.errorsys.quarry_reforged.screen.QuarryScreenHandler;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

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

    private static final int MACHINE_X = 5;
    private static final int MACHINE_Y = 0;
    private static final int MACHINE_W = 220;
    private static final int MACHINE_H = 161;
    private static final int PLAYER_PANEL_X = 25;
    private static final int PLAYER_PANEL_Y = 166;
    private static final int PLAYER_PANEL_W = 180;
    private static final int PLAYER_PANEL_H = 76;
    private static final int HOTBAR_PANEL_X = 25;
    private static final int HOTBAR_PANEL_Y = 245;
    private static final int HOTBAR_PANEL_W = 180;
    private static final int HOTBAR_PANEL_H = 28;
    private static final int CONTROLS_X = 12;
    private static final int CONTROLS_Y = 15;
    private static final int CONTROLS_W = 81;
    private static final int CONTROLS_H = 31;
    private static final int STATUS_X = 110;
    private static final int STATUS_Y = 15;
    private static final int STATUS_W = 84;
    private static final int STATUS_H = 15;
    private static final int MESSAGE_X = 110;
    private static final int MESSAGE_Y = 50;
    private static final int MESSAGE_W = 84;
    private static final int MESSAGE_H = 16;
    private static final int ENERGY_X = 198;
    private static final int ENERGY_Y = 15;
    private static final int ENERGY_W = 22;
    private static final int ENERGY_H = 138;
    private static final int UPGRADE_PANEL_X = 12;
    private static final int UPGRADE_PANEL_Y = 72;
    private static final int UPGRADE_PANEL_W = 148;
    private static final int UPGRADE_PANEL_H = 22;
    private static final int PROGRESS_PANEL_X = 12;
    private static final int PROGRESS_PANEL_Y = 98;
    private static final int PROGRESS_PANEL_W = 166;
    private static final int PROGRESS_PANEL_H = 10;
    private static final int OUTPUT_PANEL_X = 12;
    private static final int OUTPUT_PANEL_Y = 113;
    private static final int OUTPUT_PANEL_W = 166;
    private static final int OUTPUT_PANEL_H = 40;
    // Preview-only slot outlines for fast layout iteration. Copy final values into QuarryScreenHandler.
    private static final int PREVIEW_UPGRADE_X = 15;
    private static final int PREVIEW_UPGRADE_Y = 75;
    private static final int PREVIEW_OUTPUT_X = 15;
    private static final int PREVIEW_OUTPUT_Y = 116;
    private static final int PREVIEW_PLAYER_INV_X = 35;
    private static final int PREVIEW_PLAYER_INV_Y = 181;
    private static final int PREVIEW_HOTBAR_X = 35;
    private static final int PREVIEW_HOTBAR_Y = 251;

    private static final int TITLE_X = MACHINE_X + (MACHINE_W / 2) - 18;
    private static final int TITLE_Y = 4;
    private static final int INVENTORY_TITLE_X = PLAYER_PANEL_X + 4;
    private static final int INVENTORY_TITLE_Y = PLAYER_PANEL_Y + 4;

    private static final int START_BTN_X = 15;
    private static final int START_BTN_Y = 18;
    private static final int START_BTN_W = 35;
    private static final int START_BTN_H = 12;
    private static final int REMOVE_BTN_X = 15;
    private static final int REMOVE_BTN_Y = 32;
    private static final int REMOVE_BTN_W = 66;
    private static final int REMOVE_BTN_H = 12;

    private QuarryButton startStopBtn;
    private QuarryButton removeFrameBtn;

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

        int x = this.x;
        int y = this.y;

        startStopBtn = new QuarryButton(x + START_BTN_X, y + START_BTN_Y, START_BTN_W, START_BTN_H, Text.translatable("gui.quarry_reforged.button.start"), () -> send(ModNetworking.TOGGLE_ACTIVE));
        removeFrameBtn = new QuarryButton(x + REMOVE_BTN_X, y + REMOVE_BTN_Y, REMOVE_BTN_W, REMOVE_BTN_H, Text.translatable("gui.quarry_reforged.button.remove_frame"), () -> send(ModNetworking.REMOVE_FRAME));

        addDrawableChild(startStopBtn);
        addDrawableChild(removeFrameBtn);
    }

    private void send(Identifier channel) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        BlockPos pos = handler.pos;
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(channel, buf);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        drawWindow(context, this.x + MACHINE_X, this.y + MACHINE_Y, MACHINE_W, MACHINE_H);
        drawWindow(context, this.x + PLAYER_PANEL_X, this.y + PLAYER_PANEL_Y, PLAYER_PANEL_W, PLAYER_PANEL_H);
        drawWindow(context, this.x + HOTBAR_PANEL_X, this.y + HOTBAR_PANEL_Y, HOTBAR_PANEL_W, HOTBAR_PANEL_H);

        drawPanel(context, this.x + CONTROLS_X, this.y + CONTROLS_Y, CONTROLS_W, CONTROLS_H);
        drawPanel(context, this.x + STATUS_X, this.y + STATUS_Y, STATUS_W, STATUS_H);
        drawPanel(context, this.x + MESSAGE_X, this.y + MESSAGE_Y, MESSAGE_W, MESSAGE_H);
        drawPanel(context, this.x + ENERGY_X, this.y + ENERGY_Y, ENERGY_W, ENERGY_H);
        drawPanel(context, this.x + UPGRADE_PANEL_X, this.y + UPGRADE_PANEL_Y, UPGRADE_PANEL_W, UPGRADE_PANEL_H);
        drawPanel(context, this.x + PROGRESS_PANEL_X, this.y + PROGRESS_PANEL_Y, PROGRESS_PANEL_W, PROGRESS_PANEL_H);
        drawPanel(context, this.x + OUTPUT_PANEL_X, this.y + OUTPUT_PANEL_Y, OUTPUT_PANEL_W, OUTPUT_PANEL_H);

        drawSlotFrames(context, this.x + PREVIEW_UPGRADE_X, this.y + PREVIEW_UPGRADE_Y, 8, 1);
        drawSlotFrames(context, this.x + PREVIEW_OUTPUT_X, this.y + PREVIEW_OUTPUT_Y, 9, 2);
        drawSlotFrames(context, this.x + PREVIEW_PLAYER_INV_X, this.y + PREVIEW_PLAYER_INV_Y, 9, 3);
        drawSlotFrames(context, this.x + PREVIEW_HOTBAR_X, this.y + PREVIEW_HOTBAR_Y, 9, 1);

        long energy = ((long) handler.props.get(0) << 32) | (handler.props.get(1) & 0xffffffffL);
        long cap = ((long) handler.props.get(2) << 32) | (handler.props.get(3) & 0xffffffffL);
        int fillHeight = cap <= 0 ? 0 : (int) MathHelper.clamp((float) (energy * (ENERGY_H - 6)) / cap, 0, ENERGY_H - 6);
        int barLeft = this.x + ENERGY_X + 3;
        int barBottom = this.y + ENERGY_Y + ENERGY_H - 3;
        context.fill(barLeft, barBottom - fillHeight, barLeft + 16, barBottom, 0xFF6DBB78);

        int progress = handler.props.get(6);
        int progressWidth = MathHelper.clamp((progress * (PROGRESS_PANEL_W - 4)) / 1000, 0, PROGRESS_PANEL_W - 4);
        context.fill(this.x + PROGRESS_PANEL_X + 2, this.y + PROGRESS_PANEL_Y + 2, this.x + PROGRESS_PANEL_X + 2 + progressWidth, this.y + PROGRESS_PANEL_Y + PROGRESS_PANEL_H - 2, 0xFF7A9C54);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        QuarryBlockEntity quarry = handler.quarry;
        boolean running = quarry.isActiveClient() || quarry.isFrameRemovalActiveClient();
        startStopBtn.setMessage(Text.translatable(running ? "gui.quarry_reforged.button.stop" : "gui.quarry_reforged.button.start"));
        startStopBtn.active = quarry.canToggleActiveClient();
        removeFrameBtn.active = quarry.canRemoveFrameClient();

        drawCenteredString(context, quarry.getDisplayStatus(), this.x + STATUS_X + STATUS_W / 2, this.y + STATUS_Y + 4, TEXT);

        drawCenteredScaledString(context, trimMultiline(quarry.getDisplayStatusMessage(), (MESSAGE_W - 8) * 2), this.x + MESSAGE_X + MESSAGE_W / 2, this.y + MESSAGE_Y + 6, quarry.getDisplayStatusColor(), 0.5f);

        if (mouseX >= this.x + ENERGY_X && mouseX <= this.x + ENERGY_X + ENERGY_W
            && mouseY >= this.y + ENERGY_Y && mouseY <= this.y + ENERGY_Y + ENERGY_H) {
            long displayEnergy = ((long) handler.props.get(0) << 32) | (handler.props.get(1) & 0xffffffffL);
            long displayCap = ((long) handler.props.get(2) << 32) | (handler.props.get(3) & 0xffffffffL);
            context.drawTooltip(textRenderer, Text.literal(displayEnergy + " / " + displayCap + " E"), mouseX, mouseY);
        }

        if (mouseX >= this.x + PROGRESS_PANEL_X && mouseX <= this.x + PROGRESS_PANEL_X + PROGRESS_PANEL_W
            && mouseY >= this.y + PROGRESS_PANEL_Y && mouseY <= this.y + PROGRESS_PANEL_Y + PROGRESS_PANEL_H) {
            int current = quarry.getProgressLayerCurrent();
            int total = quarry.getProgressLayerTotal();
            int percent = quarry.getProgressPercentDisplay();
            context.drawTooltip(textRenderer, Text.literal(current + "/" + total + " - " + percent + "%"), mouseX, mouseY);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, this.title, TITLE_X, TITLE_Y, TEXT, false);
        context.drawText(textRenderer, this.playerInventoryTitle, INVENTORY_TITLE_X, INVENTORY_TITLE_Y, TEXT, false);
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
        context.drawText(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, color, false);
    }

    private void drawCenteredScaledString(DrawContext context, String text, int centerX, int y, int color, float scale) {
        context.getMatrices().push();
        context.getMatrices().scale(scale, scale, 1.0f);
        float inv = 1.0f / scale;
        String[] lines = text.split("\\R", -1);
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
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = trim(lines[i], width);
        }
        return String.join("\n", lines);
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
            String label = textRenderer.trimToWidth(getMessage().getString(), maxLabelWidth);
            float scaledLabelWidth = textRenderer.getWidth(label) * BUTTON_TEXT_SCALE;
            float scaledLabelHeight = 8.0f * BUTTON_TEXT_SCALE;
            int tx = Math.round((getX() + (width - scaledLabelWidth) / 2.0f) / BUTTON_TEXT_SCALE);
            int ty = Math.round((getY() + (height - scaledLabelHeight) / 2.0f) / BUTTON_TEXT_SCALE);

            context.getMatrices().push();
            context.getMatrices().scale(BUTTON_TEXT_SCALE, BUTTON_TEXT_SCALE, 1.0f);
            context.drawText(textRenderer, label, tx, ty, this.active ? TEXT : 0xFF8A93A5, false);
            context.getMatrices().pop();
        }
    }
}
