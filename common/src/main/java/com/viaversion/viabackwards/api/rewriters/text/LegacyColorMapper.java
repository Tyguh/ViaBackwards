/*
 * This file is part of ViaBackwards - https://github.com/ViaVersion/ViaBackwards
 * Copyright (C) 2016-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viaversion.viabackwards.api.rewriters.text;

import com.viaversion.viabackwards.ViaBackwards;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.libs.gson.JsonPrimitive;
import java.util.Map;

public final class LegacyColorMapper {

    private static final LegacyColor[] COLORS = {
        new LegacyColor("black", 0x000000),
        new LegacyColor("dark_blue", 0x0000AA),
        new LegacyColor("dark_green", 0x00AA00),
        new LegacyColor("dark_aqua", 0x00AAAA),
        new LegacyColor("dark_red", 0xAA0000),
        new LegacyColor("dark_purple", 0xAA00AA),
        new LegacyColor("gold", 0xFFAA00),
        new LegacyColor("gray", 0xAAAAAA),
        new LegacyColor("dark_gray", 0x555555),
        new LegacyColor("blue", 0x5555FF),
        new LegacyColor("green", 0x55FF55),
        new LegacyColor("aqua", 0x55FFFF),
        new LegacyColor("red", 0xFF5555),
        new LegacyColor("light_purple", 0xFF55FF),
        new LegacyColor("yellow", 0xFFFF55),
        new LegacyColor("white", 0xFFFFFF)
    };

    private LegacyColorMapper() {
    }

    public static void map(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                map(child);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        JsonPrimitive color = object.getAsJsonPrimitive("color");
        if (color != null) {
            String mapped = mapColor(color.getAsString());
            if (mapped != null) {
                object.addProperty("color", mapped);
            }
        }

        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (!entry.getKey().equals("color")) {
                map(entry.getValue());
            }
        }
    }

    private static String mapColor(String colorName) {
        if (!colorName.matches("(?i)#[0-9a-f]{6}")) {
            return null;
        }

        int sourceRgb = Integer.parseInt(colorName.substring(1), 16);
        Map<Integer, Integer> mappings = ViaBackwards.getConfig().customHexColorMappings();
        Integer targetRgb = mappings.get(sourceRgb);
        if (targetRgb == null && !mappings.isEmpty()) {
            long smallestDiff = Long.MAX_VALUE;
            for (Map.Entry<Integer, Integer> mapping : mappings.entrySet()) {
                long diff = colorDifference(sourceRgb, mapping.getKey());
                if (diff < smallestDiff) {
                    targetRgb = mapping.getValue();
                    smallestDiff = diff;
                }
            }
        }
        return closestLegacyColor(targetRgb != null ? targetRgb : sourceRgb);
    }

    private static String closestLegacyColor(int rgb) {
        LegacyColor closest = null;
        long smallestDiff = Long.MAX_VALUE;
        for (LegacyColor color : COLORS) {
            if (color.rgb == rgb) {
                return color.name;
            }
            long diff = colorDifference(rgb, color.rgb);
            if (diff < smallestDiff) {
                closest = color;
                smallestDiff = diff;
            }
        }
        return closest.name;
    }

    private static long colorDifference(int firstRgb, int secondRgb) {
        int firstR = (firstRgb >> 16) & 0xFF;
        int firstG = (firstRgb >> 8) & 0xFF;
        int firstB = firstRgb & 0xFF;
        int secondR = (secondRgb >> 16) & 0xFF;
        int secondG = (secondRgb >> 8) & 0xFF;
        int secondB = secondRgb & 0xFF;

        int rAverage = (firstR + secondR) / 2;
        int rDiff = secondR - firstR;
        int gDiff = secondG - firstG;
        int bDiff = secondB - firstB;
        return ((long) (2 + (rAverage >> 8)) * rDiff * rDiff)
            + (4L * gDiff * gDiff)
            + ((long) (2 + ((255 - rAverage) >> 8)) * bDiff * bDiff);
    }

    private record LegacyColor(String name, int rgb) {
    }
}
