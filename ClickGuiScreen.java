package com.apoya.client.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.*;

/**
 * Apoya Basan Client - modern ClickGUI (Fabric 1.21.11, Yarn mappings)
 *
 * Kontroller:
 *  - Başlığa sol tık + sürükle : paneli taşı
 *  - Başlığa sağ tık           : paneli aç/kapat
 *  - Modüle sol tık            : aç/kapat
 *  - Modüle sağ tık            : ayarları aç/kapat
 *  - Slider'ı sürükle          : değeri değiştir
 *  - Modülün üstünde 0.5 sn bekle: açıklama tooltip'i
 */
public final class ClickGuiScreen extends Screen {

    // ------------------------------------------------------------------
    //  Model: kendi modül sınıfını GuiModule'e küçük bir adapter ile bağla
    // ------------------------------------------------------------------
    public interface GuiModule {
        String name();
        String description();
        boolean enabled();
        void toggle();
        default List<GuiSetting> settings() { return List.of(); }
    }

    public sealed interface GuiSetting permits BoolSetting, SliderSetting {
        String name();
    }

    public record BoolSetting(String name, BooleanSupplier get, Consumer<Boolean> set) implements GuiSetting {}

    public record SliderSetting(String name, double min, double max,
                                DoubleSupplier get, DoubleConsumer set) implements GuiSetting {}

    public record Category(String name, List<GuiModule> modules) {}

    // ------------------------------------------------------------------
    //  Tema (logodaki yeşil + mor)
    // ------------------------------------------------------------------
    private static final int PANEL_W = 128, HEADER_H = 22, ROW_H = 20, SET_H = 18;

    private static final int C_HEADER = 0xFF1B1530;
    private static final int C_ROW    = 0xFF15111F;
    private static final int C_HOVER  = 0xFF1F1833;
    private static final int C_SET    = 0xFF110E1A;
    private static final int C_ACCENT = 0xFF4ADE50;
    private static final int C_ACC2   = 0xFFA855F7;
    private static final int C_TEXT   = 0xFFEDE9FE;
    private static final int C_MUTED  = 0xFF8B84A6;
    private static final int C_OFF    = 0xFF3A3354;

    // ------------------------------------------------------------------
    //  Durum
    // ------------------------------------------------------------------
    @FunctionalInterface
    private interface ClickAction { void run(int button, double mx, double my); }

    private record Hit(int x1, int y1, int x2, int y2, ClickAction action) {
        boolean contains(double x, double y) { return x >= x1 && x < x2 && y >= y1 && y < y2; }
    }

    private record SliderRef(SliderSetting setting, int x1, int x2) {}

    private static final class ModState {
        boolean expanded;
        float expand, toggle, hover;
    }

    private final List<Panel> panels = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    private long openTime, lastFrame;
    private Panel draggingPanel;
    private int dragDX, dragDY;
    private SliderRef activeSlider;

    private GuiModule hoveredNow, tipModule;
    private long tipSince;

    public ClickGuiScreen(List<Category> categories) {
        super(Text.literal("Apoya Basan"));
        int x = 20;
        for (Category c : categories) {
            panels.add(new Panel(c, x, 24));
            x += PANEL_W + 10;
        }
    }

    @Override
    protected void init() {
        openTime = System.currentTimeMillis();
        lastFrame = System.nanoTime();
    }

    @Override
    public boolean shouldPause() { return false; }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------
    @Override
    public void render(DrawContext ctx, int mx, int my, float tickDelta) {
        long nowNs = System.nanoTime();
        float dt = Math.min(0.05f, (nowNs - lastFrame) / 1_000_000_000f);
        lastFrame = nowNs;

        float t = Math.min(1f, (System.currentTimeMillis() - openTime) / 220f);
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);

        // Arka planı karart (vanilla blur'u bilerek çağırmıyoruz)
        ctx.fill(0, 0, width, height, ((int) (150 * ease) << 24) | 0x0A0814);

        hits.clear();
        hoveredNow = null;
        for (Panel p : panels) p.render(ctx, mx, my, dt);

        // Tooltip
        if (hoveredNow == null) {
            tipModule = null;
        } else if (hoveredNow != tipModule) {
            tipModule = hoveredNow;
            tipSince = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - tipSince > 500) {
            String d = tipModule.description();
            int tw = textRenderer.getWidth(d);
            int tx = Math.min(mx + 10, width - tw - 10), ty = my + 12;
            ctx.fill(tx - 4, ty - 3, tx + tw + 4, ty + 11, 0xF0120F1C);
            ctx.fill(tx - 4, ty - 3, tx - 2, ty + 11, C_ACC2);
            ctx.drawText(textRenderer, d, tx, ty, C_TEXT, true);
        }

        // Alt köşe imzası
        ctx.drawText(textRenderer, "APOYA", 8, height - 14, C_ACCENT, true);
        ctx.drawText(textRenderer, "BASAN CLIENT", 8 + textRenderer.getWidth("APOYA "), height - 14, C_ACC2, true);
    }

    // ------------------------------------------------------------------
    //  Girdi (1.21.9+ Click tabanlı API)
    // ------------------------------------------------------------------
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(click.x(), click.y())) {
                h.action().run(click.button(), click.x(), click.y());
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingPanel != null) {
            draggingPanel.x = clamp((int) click.x() - dragDX, 0, width - PANEL_W);
            draggingPanel.y = clamp((int) click.y() - dragDY, 0, height - HEADER_H);
            return true;
        }
        if (activeSlider != null) {
            applySlider(activeSlider, click.x());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        draggingPanel = null;
        activeSlider = null;
        return super.mouseReleased(click);
    }

    private void applySlider(SliderRef r, double mx) {
        double p = (mx - r.x1()) / (double) (r.x2() - r.x1());
        p = Math.max(0, Math.min(1, p));
        double v = r.setting().min() + p * (r.setting().max() - r.setting().min());
        r.setting().set().accept(Math.round(v * 100.0) / 100.0);
    }

    // ------------------------------------------------------------------
    //  Panel
    // ------------------------------------------------------------------
    private final class Panel {
        final Category cat;
        int x, y;
        boolean open = true;
        float openAnim = 1f;
        final Map<GuiModule, ModState> states = new HashMap<>();

        Panel(Category cat, int x, int y) {
            this.cat = cat;
            this.x = x;
            this.y = y;
            for (GuiModule m : cat.modules()) states.put(m, new ModState());
        }

        int contentHeight() {
            int h = 0;
            for (GuiModule m : cat.modules()) {
                h += ROW_H + Math.round(m.settings().size() * SET_H * states.get(m).expand);
            }
            return h;
        }

        void render(DrawContext ctx, int mx, int my, float dt) {
            openAnim = approach(openAnim, open ? 1f : 0f, dt * 14f);
            int vis = Math.round(contentHeight() * openAnim);
            int bottom = y + HEADER_H + vis;
            boolean settled = openAnim > 0.95f;

            // Gölge + panel genelini tıklamaya kapat (arkadaki panele geçmesin)
            ctx.fill(x - 1, y - 1, x + PANEL_W + 1, bottom + 1, 0x50000000);
            hits.add(new Hit(x, y, x + PANEL_W, bottom, (b, cx, cy) -> {}));

            // Başlık
            ctx.fill(x, y, x + PANEL_W, y + HEADER_H, C_HEADER);
            hGradient(ctx, x, y + HEADER_H - 2, x + PANEL_W, y + HEADER_H, C_ACCENT, C_ACC2);
            ctx.drawText(textRenderer, cat.name().toUpperCase(Locale.ROOT), x + 8, y + 7, C_TEXT, true);
            ctx.drawText(textRenderer, open ? "-" : "+", x + PANEL_W - 12, y + 7, C_MUTED, true);
            hits.add(new Hit(x, y, x + PANEL_W, y + HEADER_H, (b, cx, cy) -> {
                if (b == 0) {
                    draggingPanel = this;
                    dragDX = (int) cx - x;
                    dragDY = (int) cy - y;
                    panels.remove(this);   // öne getir
                    panels.add(this);
                } else if (b == 1) {
                    open = !open;
                }
            }));

            if (vis <= 0) return;

            ctx.enableScissor(x, y + HEADER_H, x + PANEL_W, y + HEADER_H + vis);
            int ry = y + HEADER_H;

            for (GuiModule m : cat.modules()) {
                ModState s = states.get(m);
                int n = m.settings().size();
                int setH = Math.round(n * SET_H * s.expand);

                boolean over = mx >= x && mx < x + PANEL_W && my >= ry && my < ry + ROW_H
                        && my < y + HEADER_H + vis;
                if (over) hoveredNow = m;

                s.hover  = approach(s.hover,  over ? 1f : 0f, dt * 16f);
                s.toggle = approach(s.toggle, m.enabled() ? 1f : 0f, dt * 14f);
                s.expand = approach(s.expand, s.expanded ? 1f : 0f, dt * 12f);

                // Satır
                ctx.fill(x, ry, x + PANEL_W, ry + ROW_H, lerp(C_ROW, C_HOVER, s.hover));
                ctx.fill(x, ry, x + 2, ry + ROW_H, lerp(C_ROW, C_ACCENT, s.toggle));
                ctx.drawText(textRenderer, m.name(), x + 9, ry + 6,
                        lerp(C_MUTED, C_TEXT, Math.max(s.toggle, s.hover * 0.6f)), true);

                // Anahtar (toggle switch)
                int sx = x + PANEL_W - 26, sy = ry + 5;
                ctx.fill(sx, sy, sx + 18, sy + 10, lerp(C_OFF, C_ACCENT, s.toggle));
                int kx = sx + 1 + Math.round(8 * s.toggle);
                ctx.fill(kx, sy + 1, kx + 8, sy + 9, 0xFFFFFFFF);

                // Ayarı olan modüllerde küçük işaret
                if (n > 0) ctx.fill(x + PANEL_W - 33, ry + 9, x + PANEL_W - 30, ry + 12, C_ACC2);

                if (settled) {
                    hits.add(new Hit(x, ry, x + PANEL_W, ry + ROW_H, (b, cx, cy) -> {
                        if (b == 0) m.toggle();
                        else if (b == 1 && n > 0) s.expanded = !s.expanded;
                    }));
                }

                // Ayarlar
                if (setH > 0) {
                    int top = ry + ROW_H;
                    ctx.enableScissor(x, top, x + PANEL_W, top + setH);
                    ctx.fill(x, top, x + PANEL_W, top + setH, C_SET);
                    int yy = top;
                    for (GuiSetting gs : m.settings()) {
                        renderSetting(ctx, gs, yy, settled && s.expand > 0.95f);
                        yy += SET_H;
                    }
                    ctx.disableScissor();
                }
                ry += ROW_H + setH;
            }
            ctx.disableScissor();
        }

        private void renderSetting(DrawContext ctx, GuiSetting gs, int yy, boolean clickable) {
            if (gs instanceof BoolSetting b) {
                boolean v = b.get().getAsBoolean();
                ctx.drawText(textRenderer, b.name(), x + 9, yy + 5, C_MUTED, true);
                int bx = x + PANEL_W - 18;
                ctx.fill(bx, yy + 4, bx + 10, yy + 14, C_OFF);
                if (v) ctx.fill(bx + 2, yy + 6, bx + 8, yy + 12, C_ACCENT);
                if (clickable) {
                    hits.add(new Hit(x, yy, x + PANEL_W, yy + SET_H, (btn, cx, cy) -> {
                        if (btn == 0) b.set().accept(!v);
                    }));
                }
            } else if (gs instanceof SliderSetting sl) {
                double v = sl.get().getAsDouble();
                float p = (float) Math.max(0, Math.min(1, (v - sl.min()) / (sl.max() - sl.min())));
                String vt = String.format(Locale.ROOT, "%.2f", v);
                ctx.drawText(textRenderer, sl.name(), x + 9, yy + 2, C_MUTED, true);
                ctx.drawText(textRenderer, vt, x + PANEL_W - 9 - textRenderer.getWidth(vt), yy + 2, C_TEXT, true);

                int bx1 = x + 9, bx2 = x + PANEL_W - 9;
                ctx.fill(bx1, yy + 13, bx2, yy + 16, C_OFF);
                int fw = Math.round((bx2 - bx1) * p);
                if (fw > 0) hGradient(ctx, bx1, yy + 13, bx1 + fw, yy + 16, C_ACCENT, C_ACC2);
                ctx.fill(bx1 + fw - 1, yy + 11, bx1 + fw + 2, yy + 18, 0xFFFFFFFF);

                if (clickable) {
                    SliderRef ref = new SliderRef(sl, bx1, bx2);
                    hits.add(new Hit(x, yy, x + PANEL_W, yy + SET_H, (btn, cx, cy) -> {
                        if (btn == 0) {
                            activeSlider = ref;
                            applySlider(ref, cx);
                        }
                    }));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    //  Yardımcılar
    // ------------------------------------------------------------------
    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static float approach(float cur, float target, float speed) {
        return cur + (target - cur) * Math.min(1f, speed);
    }

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void hGradient(DrawContext ctx, int x1, int y1, int x2, int y2, int a, int b) {
        int w = x2 - x1;
        if (w <= 0) return;
        for (int i = 0; i < w; i += 2) {
            ctx.fill(x1 + i, y1, Math.min(x1 + i + 2, x2), y2, lerp(a, b, (float) i / w));
        }
    }
}
