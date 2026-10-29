package com.witpath.support;

import com.witpath.vo.AvatarPresetVO;

import java.util.List;

/**
 * 内置「在线头像」预设
 * <p>
 * 设计取舍：不引用任何外网 CDN / 头像服务，全部由后端本地生成 SVG 再上传 OSS，
 * 既保证离线可用，也保证预览（前端 data URI）与最终落库头像完全一致。
 * 图形采用抽象几何标记（非具象图标），与项目「暖色 + 简约」的视觉语言统一。
 * </p>
 */
public final class AvatarPresets {

    /** 预设定义：标识 / 名称 / 渐变起止色 / 48x48 坐标系内的图形标记 */
    public record Preset(String id, String name, String from, String to, String mark) {
    }

    private static final int SIZE = 128;

    /** 图形标记绘制在 48x48 画布内，整体平移到 128x128 画布中心 */
    private static final int MARK_OFFSET = (SIZE - 48) / 2;

    private static final List<Preset> PRESETS = List.of(
            new Preset("amber-orbit", "暖阳·轨道", "#F8D9A6", "#E2943F",
                    "<circle cx=\"24\" cy=\"24\" r=\"9\"/>"
                            + "<ellipse cx=\"24\" cy=\"24\" rx=\"19\" ry=\"7.5\" transform=\"rotate(-28 24 24)\"/>"
                            + "<circle cx=\"41\" cy=\"14\" r=\"3.2\" fill=\"#FFF8EF\" stroke=\"none\"/>"),
            new Preset("peach-wave", "蜜桃·涟漪", "#FFD9C2", "#EF8B62",
                    "<path d=\"M4 16c5.5-7 11 7 16.5 0S31.5 9 44 16\"/>"
                            + "<path d=\"M4 26c5.5-7 11 7 16.5 0S31.5 19 44 26\"/>"
                            + "<path d=\"M4 36c5.5-7 11 7 16.5 0S31.5 29 44 36\"/>"),
            new Preset("sun-spark", "晨曦·星芒", "#FFE3A8", "#F5A524",
                    "<path d=\"M24 5l4.5 13.5L42 23l-13.5 4.5L24 41l-4.5-13.5L6 23l13.5-4.5z\" fill=\"#FFF8EF\" stroke=\"none\"/>"
                            + "<circle cx=\"38\" cy=\"11\" r=\"2.6\" fill=\"#FFF8EF\" stroke=\"none\"/>"
                            + "<circle cx=\"11\" cy=\"37\" r=\"2\" fill=\"#FFF8EF\" stroke=\"none\"/>"),
            new Preset("clay-bar", "陶土·成长", "#E8CDB6", "#A9705A",
                    "<g stroke-width=\"4\">"
                            + "<path d=\"M9 38V28\"/><path d=\"M19 38V20\"/><path d=\"M29 38V25\"/><path d=\"M39 38V12\"/>"
                            + "</g>"),
            new Preset("sky-arch", "晴空·门", "#C3DFF4", "#4A86B8",
                    "<path d=\"M5 38q9.5-26 19-26t19 26\"/>"
                            + "<path d=\"M13 38q7-16 11-16t11 16\"/>"),
            new Preset("leaf-dot", "青苔·点阵", "#CFE9C4", "#5EA678",
                    "<g fill=\"#FFF8EF\" stroke=\"none\">"
                            + "<circle cx=\"12\" cy=\"12\" r=\"2.6\"/><circle cx=\"24\" cy=\"12\" r=\"2.6\"/><circle cx=\"36\" cy=\"12\" r=\"2.6\"/>"
                            + "<circle cx=\"12\" cy=\"24\" r=\"2.6\"/><circle cx=\"24\" cy=\"24\" r=\"2.6\"/><circle cx=\"36\" cy=\"24\" r=\"2.6\"/>"
                            + "<circle cx=\"12\" cy=\"36\" r=\"2.6\"/><circle cx=\"24\" cy=\"36\" r=\"2.6\"/><circle cx=\"36\" cy=\"36\" r=\"2.6\"/>"
                            + "</g>"),
            new Preset("dusk-cube", "暮色·方体", "#CDC9F2", "#6660B4",
                    "<path d=\"M24 6l16 9v18l-16 9-16-9V15z\"/>"
                            + "<path d=\"M24 24l16-9\"/><path d=\"M24 24v18\"/><path d=\"M24 24L8 15\"/>"),
            new Preset("sage-chevron", "松烟·进阶", "#DDE8D6", "#5C7C58",
                    "<path d=\"M8 32l12-12 12 12\"/>"
                            + "<path d=\"M16 40l12-12 12 12\"/>"),
            new Preset("sand-rings", "素笺·同心", "#F1E5D0", "#B0873F",
                    "<circle cx=\"24\" cy=\"24\" r=\"18\"/>"
                            + "<circle cx=\"24\" cy=\"24\" r=\"11\"/>"
                            + "<circle cx=\"24\" cy=\"24\" r=\"4\" fill=\"#FFF8EF\" stroke=\"none\"/>"),
            new Preset("blush-hex", "微醺·棱面", "#FBD3C7", "#D9694F",
                    "<path d=\"M24 6l16 9v18l-16 9-16-9V15z\"/>"
                            + "<path d=\"M24 15v18\"/><path d=\"M8 15l16 9 16-9\"/>"),
            new Preset("night-slash", "静夜·斜纹", "#C9CEDF", "#45507E",
                    "<g stroke-width=\"4\">"
                            + "<path d=\"M8 34l10-22\"/><path d=\"M20 34l10-22\"/><path d=\"M32 34l10-22\"/>"
                            + "</g>"),
            new Preset("mint-leaf", "清润·新叶", "#C7EDE4", "#3E9E8C",
                    "<path d=\"M40 8C22 12 10 24 10 40c14-2 26-12 30-32z\" fill=\"#FFF8EF\" stroke=\"none\"/>"
                            + "<path d=\"M10 40c8-6 14-14 18-24\"/>"));

    private AvatarPresets() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    /** 返回可直接下发的 VO 列表（含 SVG 源码，供前端 data URI 预览） */
    public static List<AvatarPresetVO> toVOList() {
        return PRESETS.stream().map(p -> {
            AvatarPresetVO vo = new AvatarPresetVO();
            vo.setId(p.id());
            vo.setName(p.name());
            vo.setSvg(toSvg(p));
            return vo;
        }).toList();
    }

    public static Preset find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return PRESETS.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
    }

    /** 生成 128x128 的头像 SVG */
    public static String toSvg(Preset preset) {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + SIZE + "\" height=\"" + SIZE
                + "\" viewBox=\"0 0 " + SIZE + " " + SIZE + "\" role=\"img\" aria-label=\"" + preset.name() + "\">"
                + "<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">"
                + "<stop offset=\"0\" stop-color=\"" + preset.from() + "\"/>"
                + "<stop offset=\"1\" stop-color=\"" + preset.to() + "\"/>"
                + "</linearGradient></defs>"
                + "<rect width=\"" + SIZE + "\" height=\"" + SIZE + "\" rx=\"30\" fill=\"url(#g)\"/>"
                + "<g transform=\"translate(" + MARK_OFFSET + " " + MARK_OFFSET + ")\" fill=\"none\""
                + " stroke=\"#FFF8EF\" stroke-width=\"2.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\""
                + " opacity=\"0.95\">" + preset.mark() + "</g>"
                + "</svg>";
    }
}
